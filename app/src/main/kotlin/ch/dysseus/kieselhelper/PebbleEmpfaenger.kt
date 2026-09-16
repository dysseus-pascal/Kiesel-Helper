package ch.dysseus.kieselhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant
import java.util.UUID

/**
 * Nimmt entgegen, was die Uhren schicken, und wendet die geladenen
 * Beschreibungen darauf an.
 *
 * WIE DIE NACHRICHT HIERHER KOMMT: die Pebble-App sendet eingehende
 * AppMessages als gewoehnlichen Broadcast weiter. Das ist die klassische
 * PebbleKit-Schnittstelle, und sie braucht keine einzige Bibliothek — nur die
 * Intent-Namen unten. Nachzulesen in coredevices/mobileapp unter
 * libpebble3/.../pebblekit/classic/PebbleKitClassic.kt.
 *
 * DREI DINGE, DIE DABEI LEICHT SCHIEFGEHEN, alle in der Vorgaenger-App einmal
 * falsch gemacht:
 *
 *  1. Die Watchapp darf KEINEN companionApp-Eintrag in ihrer package.json
 *     haben. Ein Paketname dort waehlt PebbleKit2, und das bindet sich an einen
 *     Dienst, statt zu senden (CompanionAppLifecycleManager.android.kt).
 *  2. Die UUID im Intent ist ein java.util.UUID, kein String.
 *  3. Der Broadcast ist implizit, erreicht also ab Android 8 keinen im
 *     Manifest angemeldeten Empfaenger — siehe EmpfangsDienst.
 *
 * Dass eine Watchapp mit eigener pkjs-Telefonseite hier trotzdem ankommt, liegt
 * an appMessageToMultipleCompanions in LibPebbleConfig.kt: der Schalter steht
 * standardmaessig auf true, AppMessages gehen dann an PKJS UND an die
 * klassische Companion-App.
 *
 * WAS SICH GEGENUEBER DER VORGAENGER-APP GEAENDERT HAT: hier steht kein
 * `when (app)` mehr. Welche Uhr-App gemeint ist und was mit ihren Werten
 * geschehen soll, sagt die geladene Beschreibung.
 */
class PebbleEmpfaenger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECEIVE) return

        val uuid = leseUuid(intent) ?: return
        val modul = ModulSpeicher(context).fuer(uuid)

        val transaktion = intent.getIntExtra(EXTRA_TRANSACTION_ID, -1)

        // Fuer eine UUID ohne Beschreibung NICHT bestaetigen: die Nachricht
        // gilt einer fremden Watchapp, und ein ACK von uns behauptete, wir
        // haetten sie verarbeitet.
        if (modul == null) return

        val daten = intent.getStringExtra(EXTRA_MSG_DATA)
        if (daten == null) {
            Log.w(TAG, "Nachricht ohne Daten")
            return
        }
        val werte = leseWoerterbuch(daten)

        // Zuerst bestaetigen, dann arbeiten. Die Uhr wartet auf das ACK; kaeme
        // es erst nach dem Schreiben, liefe sie in ihren Zeitablauf, sobald die
        // Gesundheitsakte einmal langsam ist. Bestaetigt wird JEDE Nachricht
        // einer bekannten App, auch eine ohne verwertbaren Inhalt — sonst
        // meldete die Uhr einen Fehler, wo keiner ist.
        bestaetige(context, transaktion)

        imHintergrund(context) { wendeAn(context, modul, werte) }
    }

    /**
     * Die Regeln der Beschreibung der Reihe nach pruefen.
     *
     * Rueckgabe ist die Meldung fuer die Statusanzeige.
     */
    private suspend fun wendeAn(context: Context, modul: Modul, werte: Map<Int, Long>): String {
        val verlauf = Verlauf(context)
        val akte = Akte(context)
        val meldungen = mutableListOf<String>()

        modul.regeln.forEachIndexed { index, regel ->
            // Greift die Regel? Alle unter `wenn` genannten Felder muessen da
            // sein. Ohne diese Bedingung truege eine blosse Standmeldung der
            // Uhr — die kommt bei jedem Start und jedem Wecker — jedes Mal
            // einen weiteren Eintrag in die Akte.
            val fehlt = regel.wenn.any { name -> wert(modul, werte, name) == null }
            if (fehlt) return@forEachIndexed

            // Schon eingetragen? Eine erneut zugestellte Nachricht darf nicht
            // ein zweites Mal schreiben.
            val merkmal = regel.nichtZweimalFuer?.let { wert(modul, werte, it) }
            if (merkmal != null && verlauf.schonGetan(modul.uuid.toString(), index, merkmal)) {
                return@forEachIndexed
            }

            val zeitRoh = wert(modul, werte, regel.zeitAus)
            if (zeitRoh == null) {
                meldungen.add("${modul.name}: Zeitfeld ${regel.zeitAus} fehlt")
                return@forEachIndexed
            }
            val beginn =
                if (regel.zeitInMillisekunden) Instant.ofEpochMilli(zeitRoh)
                else Instant.ofEpochSecond(zeitRoh)

            val zahl = regel.wertAus?.let { wert(modul, werte, it) }
            if (regel.wertAus != null && zahl == null) {
                meldungen.add("${modul.name}: Feld ${regel.wertAus} fehlt")
                return@forEachIndexed
            }

            val ergebnis = try {
                akte.schreibe(
                    regel = regel,
                    wert = (zahl ?: 0L).toDouble(),
                    beginn = beginn,
                    meldung = fuelleMeldung(regel.meldung, modul, werte, regel),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Eintragen fehlgeschlagen", e)
                "Eintragen fehlgeschlagen: " + (e.message ?: e.javaClass.simpleName)
            }
            meldungen.add(ergebnis)

            // Erst merken, wenn es geklappt hat. Wer den Riegel vorher setzte,
            // verloere die Messung endgueltig, sobald ein Schreibversuch einmal
            // scheitert.
            if (merkmal != null && !ergebnis.contains("fehl") && !ergebnis.contains("Erlaubnis")) {
                verlauf.merkeGetan(modul.uuid.toString(), index, merkmal)
            }
        }

        val text = if (meldungen.isEmpty()) "${modul.name}: nichts einzutragen"
        else meldungen.joinToString("; ")
        verlauf.merkeMeldung(text)
        Log.i(TAG, text)
        return text
    }

    /** Den Wert eines Feldes, ueber Name -> Nummer -> Nachricht. */
    private fun wert(modul: Modul, werte: Map<Int, Long>, name: String): Long? {
        val nummer = modul.schluessel[name] ?: return null
        return werte[nummer]
    }

    /** {FELD} in der Meldung durch den Wert ersetzen. */
    private fun fuelleMeldung(
        vorlage: String,
        modul: Modul,
        werte: Map<Int, Long>,
        regel: Regel,
    ): String {
        if (vorlage.isEmpty()) return "${modul.name}: ${regel.art.klartext} eingetragen"
        var text = vorlage
        for (name in modul.schluessel.keys) {
            val v = wert(modul, werte, name) ?: continue
            text = text.replace("{$name}", v.toString())
        }
        return text
    }

    /**
     * Ein Empfaenger darf nicht warten — nach onReceive ist der Prozess frei.
     * goAsync() haelt ihn so lange am Leben, wie das Eintragen dauert.
     */
    private fun imHintergrund(context: Context, block: suspend () -> String) {
        val ergebnis = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Verarbeitung fehlgeschlagen", e)
                Verlauf(context).merkeMeldung("Fehler: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                ergebnis.finish()
            }
        }
    }

    private fun leseUuid(intent: Intent): UUID? {
        val roh = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getSerializableExtra(EXTRA_UUID, UUID::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_UUID)
        }
        return when {
            roh is UUID -> roh
            else -> try {
                UUID.fromString(roh as? String ?: intent.getStringExtra(EXTRA_UUID))
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun bestaetige(context: Context, transaktion: Int) {
        if (transaktion < 0) return
        // DIE RICHTIGE LEITUNG IST ...app.ACK, NICHT ...app.RECEIVE_ACK.
        // PebbleKitClassic.kt lauscht fuer die Bestaetigung einer eingehenden
        // Nachricht auf INTENT_APP_ACK. RECEIVE_ACK ist die Gegenrichtung —
        // das schickt die Pebble-App hinaus, wenn die UHR etwas bestaetigt hat.
        // Ein ACK dorthin hoert niemand, und die Uhr lief in SEND_TIMEOUT.
        //
        // Kein setPackage: der Empfaenger der Pebble-App ist zur Laufzeit
        // angemeldet und bekommt implizite Broadcasts.
        val ack = Intent(ACTION_ACK).apply {
            putExtra(EXTRA_TRANSACTION_ID, transaktion)
        }
        context.sendBroadcast(ack)
    }

    /**
     * Das klassische PebbleKit-Format: ein JSON-Feld je Eintrag mit key, type,
     * length und value. Hier interessieren nur ganze Zahlen — mehr schickt
     * keine der Uhr-Apps.
     */
    private fun leseWoerterbuch(json: String): Map<Int, Long> {
        val aus = HashMap<Int, Long>()
        try {
            val a = JSONArray(json)
            for (i in 0 until a.length()) {
                val eintrag = a.optJSONObject(i) ?: continue
                val schluessel = eintrag.optInt("key", -1)
                if (schluessel < 0) continue
                when (eintrag.optString("type", "int")) {
                    "int", "uint" -> aus[schluessel] = eintrag.optLong("value")
                    else -> Unit   // Zeichenketten und Rohdaten kommen nicht vor
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nachricht nicht lesbar: $json", e)
        }
        return aus
    }

    companion object {
        const val TAG = "KieselHelper"

        const val ACTION_RECEIVE = "com.getpebble.action.app.RECEIVE"
        // Bestaetigung einer Nachricht, die von der UHR kam.
        const val ACTION_ACK = "com.getpebble.action.app.ACK"
        const val EXTRA_UUID = "uuid"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_MSG_DATA = "msg_data"
    }
}
