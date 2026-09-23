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
 * Sichern und Zurückholen - in einen WebDAV-Ordner.
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

    fun webdavBereit(context: Context): Boolean =
        Einstellungen.sicherungUrl(context).isNotBlank() &&
            Einstellungen.sicherungNutzer(context).isNotBlank() &&
            Tresor.hatGeheimnis(context)

    fun ordnerBereit(context: Context): Boolean =
        Einstellungen.sicherungOrdner(context).isNotBlank()

    fun bereit(context: Context): Boolean = webdavBereit(context) || ordnerBereit(context)

    /**
     * Alle eingerichteten Ziele - der Ordner auf dem Telefon zuerst, weil er
     * immer geht; WebDAV dazu, wenn es eingerichtet ist. Beide bekommen
     * dieselbe Sicherung.
     */
    private fun ziele(context: Context): List<Ziel> {
        val aus = mutableListOf<Ziel>()
        if (ordnerBereit(context)) {
            aus += OrdnerZiel(context, android.net.Uri.parse(Einstellungen.sicherungOrdner(context)))
        }
        if (webdavBereit(context)) {
            aus += WebDav(
                Einstellungen.sicherungUrl(context),
                Einstellungen.sicherungNutzer(context),
                Tresor.lies(context),
            )
        }
        return aus
    }

    /** Zugang und Ordner prüfen; einen fehlenden Ordner anlegen. */
    suspend fun pruefe(context: Context): String = withContext(Dispatchers.IO) {
        val ziele = ziele(context)
        if (ziele.isEmpty()) return@withContext "Kein Ziel: Ordner wählen oder WebDAV eintragen"
        ziele.joinToString("\n") { ziel ->
            ziel.name + ": " + when (val e = ziel.pruefe()) {
                is WebDav.Ergebnis.Gut ->
                    if (e.text == "angelegt") "Ordner angelegt, Zugang stimmt"
                    else "Ordner erreichbar, Zugang stimmt"
                is WebDav.Ergebnis.Schlecht -> e.grund
            }
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
        val ziele = ziele(context)
        if (ziele.isEmpty()) return@withContext "Nicht eingerichtet"

        val tage = Speicher(context).alleTage()
        val spuren = Spur.alle(context)
        val text = Sicherung.alsJson(
            tage = tage,
            einstellungen = Einstellungen.alsText(context),
            spuren = spuren,
            erzeugt = Instant.now().toString(),
        )

        val meldungen = mutableListOf<String>()
        var eines = false
        for (ziel in ziele) {
            val fehler = sichereNach(context, ziel, text, spuren)
            if (fehler != null) {
                meldungen += ziel.name + ": " + fehler
            } else {
                eines = true
            }
        }
        if (eines) Einstellungen.setzeSicherungZuletzt(context, Instant.now().toEpochMilli())
        val meldung = if (eines) {
            tage.size.toString() + " Tage gesichert" +
                (if (ziele.size > 1) " (" + ziele.size + " Ziele)" else "") +
                (if (meldungen.isNotEmpty()) " — " + meldungen.joinToString("; ") else "")
        } else {
            meldungen.joinToString("; ")
        }
        Verlauf(context).merkeMeldung(meldung)
        meldung
    }

    /** Ein Ziel beschreiben; null heisst gut, sonst der Grund. */
    private fun sichereNach(context: Context, ziel: Ziel, text: String, spuren: List<Long>): String? {
        when (ziel.lege(Sicherung.DATEINAME, text.toByteArray(Charsets.UTF_8))) {
            is WebDav.Ergebnis.Schlecht -> {
                // 409 heisst meistens: der Ordner ist nicht da. Einmal
                // anlegen und noch einmal versuchen - das ist der haeufigste
                // Fall beim allerersten Mal.
                ziel.ordner()
                when (val zweit = ziel.lege(
                    Sicherung.DATEINAME, text.toByteArray(Charsets.UTF_8)
                )) {
                    is WebDav.Ergebnis.Schlecht -> return zweit.grund
                    else -> Unit
                }
            }
            else -> Unit
        }

        // Die Spuren in ihren Unterordner. Schlaegt eine fehl, geht die
        // Sicherung trotzdem durch: die Tabelle ist das Wertvolle.
        //
        // GEMERKT WIRD JE ZIEL, welche Spur schon dort liegt: der WebDAV-
        // Ordner und der Telefonordner haben nicht denselben Stand.
        if (spuren.isNotEmpty()) {
            ziel.ordner(Sicherung.SPURORDNER)
            val schonOben = Einstellungen.gesicherteSpuren(context, ziel.name)
            spuren.filter { it.toString() !in schonOben }.forEach { beginn ->
                val inhalt = Spur.roh(context, beginn) ?: return@forEach
                val pfad = Sicherung.SPURORDNER + "/" + Sicherung.spurname(beginn)
                when (ziel.lege(pfad, inhalt.toByteArray(Charsets.UTF_8), "application/x-ndjson")) {
                    is WebDav.Ergebnis.Gut -> Einstellungen.merkeGesicherteSpur(context, beginn, ziel.name)
                    is WebDav.Ergebnis.Schlecht -> Unit
                }
            }
        }
        return null
    }

    /**
     * Zurückholen - und nur in die Lücken schreiben.
     *
     * DIE SPUREN KOMMEN NUR, WENN SIE FEHLEN. Eine vorhandene Datei wird
     * nicht angefasst: sie ist die Aufzeichnung dieses Telefons und älter als
     * jede Kopie.
     */
    suspend fun zurueck(context: Context): String = withContext(Dispatchers.IO) {
        val ziele = ziele(context)
        if (ziele.isEmpty()) return@withContext "Nicht eingerichtet"
        // Das erste Ziel, das eine Sicherung hat - der Telefonordner vor dem
        // Server, weil er ohne Netz geht.
        var dav: Ziel = ziele.first()
        var text: String? = null
        for (z in ziele) {
            text = z.hole(Sicherung.DATEINAME)
            if (text != null) { dav = z; break }
        }
        if (text == null) return@withContext "Keine Sicherung gefunden"
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
        // Netz braucht nur der Server; der Ordner auf dem Telefon geht auch
        // im Flugmodus, und die Cloud-App holt nach, wenn sie kann.
        val bedingung = Constraints.Builder()
            .setRequiredNetworkType(
                if (webdavBereit(context)) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
            )
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
                // NOCHMAL VERSUCHEN, NICHT AUFGEBEN: ein Server, der gerade
                // nicht da ist, ist kein Grund, die Sicherung sein zu lassen.
                Log.w(PebbleEmpfaenger.TAG, "Sicherung: " + e.message)
                Result.retry()
            }
        }
    }
}
