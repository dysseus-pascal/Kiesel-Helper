package ch.dysseus.kieselhelper

import android.content.Context
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Was diese App tut - fest eingebaut, nicht nachgeladen.
 *
 * VORHER STAND HIER EIN APPARAT. Zettel aus GitHub, ein Leser dafuer, ein
 * Regelwerk, ein Katalog von 22 Satzarten, ein Bildschirm zum Einbinden: rund
 * 1660 Zeilen, damit die App Dinge tun konnte, die ihr niemand beigebracht
 * hatte. Das war richtig gedacht fuer eine App, die andere benutzen. Diese
 * benutzt nur einer, und fuer ihn sind es vier Aufgaben - die stehen jetzt hier
 * und sind in einer Minute zu lesen.
 *
 * Drei kommen VON der Uhr: zwei davon gehen in die Gesundheitsakte, die dritte
 * (SupCycle) in den eigenen Speicher, weil die Akte fuer "genommen" keine
 * Satzart hat. Die vierte geht in die andere Richtung und steht in
 * [OsmandNavigation]: sie hat keine AppMessage als Anlass, sondern OsmAnds
 * Schnittstelle.
 */
object Aufgaben {

    private const val TAG = PebbleEmpfaenger.TAG

    // --- Drinktervall: getrunkenes Wasser ---

    private val DRINKTERVALL: UUID =
        UUID.fromString("5b0f7a3e-2c8d-4b61-9e4f-7d2a1c9b8e50")

    /**
     * Die Feldnummern ergeben sich aus der Reihenfolge der `messageKeys` in der
     * package.json der Uhr-App, beginnend bei 10000. Wer dort eine Zeile
     * dazwischenschiebt, verschiebt alle folgenden - und diese App traegt
     * danach still den falschen Wert ein.
     */
    private const val DT_GLASS_ML = 10008
    private const val DT_DRANK_AT = 10009

    // --- Herzintervall: naechtliche RMSSD-Messung ---

    private val HERZINTERVALL: UUID =
        UUID.fromString("7e1b28b2-cd13-4b50-ac4e-187b92707707")

    private const val HZ_RMSSD = 10000
    private const val HZ_WHEN = 10005

    // --- SupCycle: was heute ansteht und was davon genommen ist ---

    private val SUPCYCLE: UUID =
        UUID.fromString("33ce868a-2beb-4335-9421-4d741ed16eb3")

    /**
     * SupCycle schickt das ohnehin - fuer seine eigenen Timeline-Pins.
     *
     * Die Uhr meldet nach jeder Einnahme den Kalendertag und zwei Bitmasken:
     * was heute faellig ist und was davon abgehakt wurde. Namen kommen nicht
     * mit; die kennt nur die Konfigseite von SupCycle. Gezaehlt wird also,
     * nicht aufgelistet - und das ist genau die Zahl, die man taeglich wissen
     * will.
     */
    private const val SC_TODAY = 10039
    private const val SC_DUE = 10040
    private const val SC_TAKEN = 10041

    /** Alle Uhr-Apps, von denen diese App ueberhaupt etwas annimmt. */
    val BEKANNTE_UHREN = setOf(DRINKTERVALL, HERZINTERVALL, SUPCYCLE)

    /** Die Berechtigungen, die dafuer noetig sind. */
    val BERECHTIGUNGEN: Set<String> = setOf(
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
    )

    /**
     * Eine Nachricht von der Uhr verarbeiten.
     *
     * Rueckgabe ist ein Satz fuer den Verlauf, oder null wenn nichts geschah.
     * Kein Fehlercode: die App besteht im Kern aus einem Empfaenger, und ohne
     * diese Zeile saehe man ihr von aussen nie an, ob sie etwas tut.
     */
    suspend fun verarbeite(context: Context, von: UUID, felder: Map<Int, Long>): String? =
        when (von) {
            DRINKTERVALL -> wasser(context, felder)
            HERZINTERVALL -> herz(context, felder)
            SUPCYCLE -> supplemente(context, felder)
            else -> null
        }

    /**
     * Supplemente festhalten - im EIGENEN Speicher, nicht in der Akte.
     *
     * DIE AKTE KENNT KEIN "GENOMMEN". Was ihr am naechsten kommt, ist ein
     * Ernaehrungssatz mit Naehrstoffmassen - und die weiss SupCycle nicht: ein
     * Plan dort besteht aus Namen und Zyklen, nicht aus Milligramm. Eine Zahl
     * zu erfinden, damit sie in eine fremde Tabelle passt, waere der
     * schlechteste aller Wege.
     *
     * Gezaehlt wird, was FAELLIG war und davon genommen wurde. Ein Praeparat,
     * das heute pausiert, gehoert in keine Quote.
     */
    private suspend fun supplemente(context: Context, felder: Map<Int, Long>): String? {
        val ymd = felder[SC_TODAY] ?: return null
        val faellig = felder[SC_DUE] ?: return null
        val genommen = felder[SC_TAKEN] ?: 0L

        val tag = try {
            LocalDate.of(
                (ymd / 10000).toInt(), ((ymd / 100) % 100).toInt(), (ymd % 100).toInt()
            )
        } catch (e: Exception) {
            Log.w(TAG, "SupCycle: unbrauchbares Datum " + ymd); return null
        }

        val wieViele = java.lang.Long.bitCount(faellig)
        // UND, nicht blosses Zaehlen: abgehakt bleibt abgehakt, auch wenn ein
        // Praeparat heute gar nicht dran waere. In der Quote hat es dann
        // nichts verloren.
        val davon = java.lang.Long.bitCount(genommen and faellig)

        if (!Riegel.neu(context, "supcycle", "$ymd:$faellig:$genommen")) return null

        withContext(Dispatchers.IO) {
            Speicher(context).merke(tag, mapOf(
                "supp_faellig" to wieViele.toDouble(),
                "supp_genommen" to davon.toDouble(),
            ))
        }
        GesundheitWidget.stosseAn(context)
        return "$davon von $wieViele Präparaten"
    }

    /**
     * Getrunkenes Wasser eintragen.
     *
     * NICHT ZWEIMAL FUER DENSELBEN AUGENBLICK. Die Uhr schickt ihren Stand bei
     * jeder Gelegenheit mit, nicht nur beim Trinken; ohne diesen Riegel stuende
     * ein Glas mehrfach in der Akte. Der Zeitpunkt des Trinkens ist der
     * natuerliche Schluessel dafuer.
     */
    private suspend fun wasser(context: Context, felder: Map<Int, Long>): String? {
        val ml = felder[DT_GLASS_ML] ?: return null
        val wann = felder[DT_DRANK_AT] ?: return null
        if (ml <= 0 || wann <= 0) return null
        if (!Riegel.neu(context, "drinktervall", wann.toString())) return null

        val beginn = Instant.ofEpochSecond(wann)
        val satz = HydrationRecord(
            startTime = beginn,
            startZoneOffset = null,
            // Eine Minute Dauer: die Akte will eine Spanne, und ein Glas
            // trinkt sich nicht in einem Augenblick.
            endTime = beginn.plusSeconds(60),
            endZoneOffset = null,
            volume = Volume.milliliters(ml.toDouble()),
            metadata = herkunft(),
        )
        return schreibe(context, satz, "$ml ml eingetragen")
    }

    /**
     * Die Herzratenvariabilitaet eintragen.
     *
     * Auch hier ein Riegel auf dem Zeitpunkt: die Uhr schickt die Messung der
     * letzten Nacht, bis sie bestaetigt ist, und das kann mehrfach geschehen.
     */
    private suspend fun herz(context: Context, felder: Map<Int, Long>): String? {
        val ms = felder[HZ_RMSSD] ?: return null
        val wann = felder[HZ_WHEN] ?: return null
        if (ms <= 0 || wann <= 0) return null
        if (!Riegel.neu(context, "herzintervall", wann.toString())) return null

        val satz = HeartRateVariabilityRmssdRecord(
            time = Instant.ofEpochSecond(wann),
            zoneOffset = null,
            heartRateVariabilityMillis = ms.toDouble(),
            metadata = herkunft(),
        )
        return schreibe(context, satz, "$ms ms eingetragen")
    }

    /**
     * Die Herkunft des Satzes: automatisch erfasst, von einer Uhr.
     *
     * Ohne das steht die Messung in der Akte da, als haette man sie von Hand
     * eingetippt - und man kann sie spaeter nicht mehr von einer echten
     * Eingabe unterscheiden.
     */
    private fun herkunft(): Metadata =
        Metadata.autoRecorded(device = Device(type = Device.TYPE_WATCH))

    private suspend fun schreibe(context: Context, satz: Record, meldung: String): String {
        val klient = Akte(context).bereit()
            ?: return "Gesundheitsakte nicht verfügbar"
        return try {
            klient.insertRecords(listOf(satz))
            // Das Widget zeigt Wasser; ein Glas, das erst in einer halben
            // Stunde dort ankommt, sieht aus wie ein verschlucktes.
            GesundheitWidget.stosseAn(context)
            meldung
        } catch (e: Exception) {
            // Die haeufigste Ursache ist eine fehlende Erlaubnis. Sie zu
            // nennen ist nuetzlicher als "Fehler".
            Log.w(TAG, "Eintragen fehlgeschlagen: " + e.message)
            "Nicht eingetragen — Erlaubnis in der App prüfen"
        }
    }
}

/**
 * Der Riegel gegen Doppeleintraege.
 *
 * Die Uhr schickt ihren Stand bei jeder Gelegenheit mit, nicht nur beim
 * Ereignis. Ohne diesen Riegel stuende dasselbe Glas mehrfach in der
 * Gesundheitsakte - und ein Eintrag, den niemand gemacht hat, ist schlimmer
 * als ein fehlender: er sieht aus wie eine Messung.
 *
 * Gemerkt wird je Aufgabe EIN Merkmal, naemlich das zuletzt verarbeitete.
 * Mehr braucht es nicht: die Werte kommen in der Zeit vorwaerts, und ein
 * Nachzuegler von vorgestern waere ohnehin keiner.
 */
object Riegel {
    private const val DATEI = "kiesel-riegel"

    /** true heisst: noch nicht dagewesen, jetzt vormerken. */
    fun neu(context: Context, aufgabe: String, merkmal: String): Boolean {
        val p = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
        if (p.getString(aufgabe, null) == merkmal) return false
        p.edit().putString(aufgabe, merkmal).apply()
        return true
    }
}
