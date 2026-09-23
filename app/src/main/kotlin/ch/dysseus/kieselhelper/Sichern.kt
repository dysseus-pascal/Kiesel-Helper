package ch.dysseus.kieselhelper

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Sichern und Zurückholen - in einen Ordner auf dem Telefon.
 *
 * WAS HIER WIRKLICH AUF DEM SPIEL STEHT: die Gesundheitsakte hält rund
 * dreissig Tage. Alles, was diese App an Wochenprofilen, typischen Tagen und
 * Zusammenhängen rechnet, steht danach nur noch in ihrer eigenen Tabelle — und
 * die liegt in den App-Daten eines einzigen Telefons.
 *
 * ZURÜCKHOLEN ERGÄNZT, ES ÜBERSCHREIBT NICHT. Eine Sicherung ist älter als
 * das, was gerade auf dem Telefon steht; sie darüberzulegen hiesse, die
 * letzten Tage gegen alte Zahlen zu tauschen. Geschrieben wird nur, wo lokal
 * nichts steht — das ist zugleich die Antwort auf zwei Telefone: beide
 * ergänzen einander, keines löscht das andere.
 *
 * TÄGLICH IM HINTERGRUND, über WorkManager. Ein Wecker, den Android im
 * Stromsparen verschluckt, wäre eine Sicherung, die es nur gibt, wenn man
 * daran denkt — und dann hat man sie auch von Hand angestossen.
 */
object Sichern {

    private const val ARBEIT = "kiesel-sicherung"

    fun bereit(context: Context): Boolean = Einstellungen.sicherungOrdner(context).isNotBlank()

    private fun ziel(context: Context): OrdnerZiel? {
        if (!bereit(context)) return null
        return OrdnerZiel(context, android.net.Uri.parse(Einstellungen.sicherungOrdner(context)))
    }

    /** Ist der Ordner noch da und beschreibbar? */
    suspend fun pruefe(context: Context): String = withContext(Dispatchers.IO) {
        val ziel = ziel(context) ?: return@withContext "Kein Ordner gewählt"
        when (val e = ziel.pruefe()) {
            is OrdnerZiel.Ergebnis.Gut -> "Ordner »" + ziel.name + "« bereit"
            is OrdnerZiel.Ergebnis.Schlecht -> e.grund
        }
    }

    /**
     * Alles hochladen: die Tabelle als eine Datei, die Spuren daneben.
     *
     * DIE SPUREN EINZELN UND NUR DIE NEUEN. Eine Stunde Laufen sind tausend
     * Punkte; sie bei jeder Sicherung erneut hochzuladen wäre jeden Tag
     * dasselbe Megabyte. Was schon oben liegt, steht in einer Liste daneben.
     */
    suspend fun jetzt(context: Context): String = withContext(Dispatchers.IO) {
        val ziel = ziel(context) ?: return@withContext "Nicht eingerichtet"

        val tage = Speicher(context).alleTage()
        val spuren = Spur.alle(context)
        val text = Sicherung.alsJson(
            tage = tage,
            einstellungen = Einstellungen.alsText(context),
            spuren = spuren,
            erzeugt = Instant.now().toString(),
        )

        when (val e = ziel.lege(Sicherung.DATEINAME, text.toByteArray(Charsets.UTF_8))) {
            is OrdnerZiel.Ergebnis.Schlecht -> {
                Verlauf(context).merkeMeldung("Sicherung: " + e.grund)
                return@withContext e.grund
            }
            else -> Unit
        }

        // Die Spuren in ihren Unterordner. Schlaegt eine fehl, geht die
        // Sicherung trotzdem durch: die Tabelle ist das Wertvolle.
        var neue = 0
        if (spuren.isNotEmpty()) {
            ziel.ordner(Sicherung.SPURORDNER)
            val schonOben = Einstellungen.gesicherteSpuren(context)
            spuren.filter { it.toString() !in schonOben }.forEach { beginn ->
                val inhalt = Spur.roh(context, beginn) ?: return@forEach
                val pfad = Sicherung.SPURORDNER + "/" + Sicherung.spurname(beginn)
                when (ziel.lege(pfad, inhalt.toByteArray(Charsets.UTF_8), "application/x-ndjson")) {
                    is OrdnerZiel.Ergebnis.Gut -> {
                        Einstellungen.merkeGesicherteSpur(context, beginn)
                        neue++
                    }
                    is OrdnerZiel.Ergebnis.Schlecht -> Unit
                }
            }
        }

        Einstellungen.setzeSicherungZuletzt(context, Instant.now().toEpochMilli())
        val meldung = tage.size.toString() + " Tage gesichert" +
            (if (neue > 0) ", $neue neue Strecken" else "")
        Verlauf(context).merkeMeldung(meldung)
        meldung
    }

    /**
     * Zurückholen - und nur in die Lücken schreiben.
     *
     * DIE SPUREN KOMMEN NUR, WENN SIE FEHLEN. Eine vorhandene Datei wird
     * nicht angefasst: sie ist die Aufzeichnung dieses Telefons und älter als
     * jede Kopie.
     */
    suspend fun zurueck(context: Context): String = withContext(Dispatchers.IO) {
        val dav = ziel(context) ?: return@withContext "Nicht eingerichtet"
        val text = dav.hole(Sicherung.DATEINAME)
            ?: return@withContext "Keine Sicherung gefunden"
        val stand = Sicherung.ausJson(text)
            ?: return@withContext "Die Datei dort ist keine Sicherung dieser App"

        val speicher = Speicher(context)
        val vorhanden: Map<LocalDate, Map<String, Double>> = speicher.alleTage().toMap()
        val luecken = Sicherung.nurLuecken(stand.tage, vorhanden)
        luecken.forEach { (tag, werte) ->
            speicher.merke(tag, werte.mapValues { it.value as Double? })
        }

        var spuren = 0
        stand.spuren.forEach { beginn ->
            if (Spur.vorhanden(context, beginn)) return@forEach
            val inhalt = dav.hole(Sicherung.SPURORDNER + "/" + Sicherung.spurname(beginn))
                ?: return@forEach
            if (Spur.schreibeRoh(context, beginn, inhalt)) spuren++
        }

        val meldung = "Zurückgeholt: " + luecken.size + " Tage ergänzt" +
            (if (spuren > 0) ", $spuren Strecken" else "") +
            (if (luecken.isEmpty() && spuren == 0) " — es fehlte nichts" else "")
        Verlauf(context).merkeMeldung(meldung)
        meldung
    }

    // --- Täglich von selbst ---

    fun planen(context: Context) {
        // KEIN NETZ NOETIG: der Ordner liegt auf dem Telefon, und die Sync-App
        // holt nach, wenn sie kann - auch aus dem Flugmodus heraus.
        val bedingung = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val arbeit = PeriodicWorkRequestBuilder<Arbeit>(Duration.ofDays(1))
            .setConstraints(bedingung)
            // Nicht sofort: die erste Sicherung macht man von Hand, und
            // gleich nach dem Einrichten laeuft ohnehin eine.
            .setInitialDelay(Duration.ofHours(12))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ARBEIT, ExistingPeriodicWorkPolicy.KEEP, arbeit
        )
    }

    fun abbestellen(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ARBEIT)
    }

    class Arbeit(context: Context, gaben: WorkerParameters) : CoroutineWorker(context, gaben) {
        override suspend fun doWork(): Result {
            if (!bereit(applicationContext)) return Result.success()
            return try {
                jetzt(applicationContext)
                Result.success()
            } catch (e: Exception) {
                // NOCHMAL VERSUCHEN, NICHT AUFGEBEN: ein Ordner, der gerade
                // nicht da ist, ist kein Grund, die Sicherung sein zu lassen.
                Log.w(PebbleEmpfaenger.TAG, "Sicherung: " + e.message)
                Result.retry()
            }
        }
    }
}
