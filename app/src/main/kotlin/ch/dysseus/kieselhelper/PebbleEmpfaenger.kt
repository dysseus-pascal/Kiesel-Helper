package ch.dysseus.kieselhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

/**
 * Erste Quelle: was die Uhren schicken.
 *
 * WIE DIE NACHRICHT HIERHER KOMMT: die Pebble-App sendet eingehende
 * AppMessages als gewoehnlichen Broadcast weiter. Das ist die klassische
 * PebbleKit-Schnittstelle, und sie braucht keine einzige Bibliothek - nur die
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
 *     Manifest angemeldeten Empfaenger - siehe EmpfangsDienst.
 *
 * Dass eine Watchapp mit eigener pkjs-Telefonseite hier trotzdem ankommt, liegt
 * an appMessageToMultipleCompanions in LibPebbleConfig.kt: der Schalter steht
 * standardmaessig auf true, AppMessages gehen dann an PKJS UND an die
 * klassische Companion-App.
 *
 * WAS HIER NICHT STEHT: was mit den Werten geschieht. Das steht in [Aufgaben].
 * Diese Klasse tut zwei Dinge - bestaetigen und weiterreichen.
 */
class PebbleEmpfaenger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RECEIVE) return

        val uuid = leseUuid(intent) ?: return

        // Fuer eine unbekannte UUID NICHT bestaetigen: die Nachricht gilt
        // einer fremden Watchapp, und ein ACK von uns behauptete, wir haetten
        // sie verarbeitet.
        if (uuid !in Aufgaben.BEKANNTE_UHREN) return

        val daten = intent.getStringExtra(EXTRA_MSG_DATA)
        if (daten == null) {
            Log.w(TAG, "Nachricht ohne Daten")
            return
        }
        val nachNummer = leseWoerterbuch(daten)

        // Zuerst bestaetigen, dann arbeiten. Die Uhr wartet auf das ACK; kaeme
        // es erst nach dem Schreiben, liefe sie in ihren Zeitablauf, sobald die
        // Gesundheitsakte einmal langsam ist. Bestaetigt wird JEDE Nachricht
        // einer bekannten App, auch eine ohne verwertbaren Inhalt - sonst
        // meldete die Uhr einen Fehler, wo keiner ist.
        bestaetige(context, intent.getIntExtra(EXTRA_TRANSACTION_ID, -1))

        val ergebnis = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Zahlen UND Texte. Lange kamen nur Zahlen - ein Messwert ist
                // eine Zahl. Seit SupCycle die Namen seiner Praeparate
                // mitschickt, ist das nicht mehr wahr.
                val zahlen = mutableMapOf<Int, Long>()
                val texte = mutableMapOf<Int, String>()
                for ((nummer, wert) in nachNummer) {
                    when (wert) {
                        is Wert.Zahl -> zahlen[nummer] = wert.zahl
                        is Wert.Text -> texte[nummer] = wert.text
                    }
                }
                Aufgaben.verarbeite(context, uuid, zahlen, texte)?.let {
                    Verlauf(context).merkeMeldung(it)
                    Log.i(TAG, it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Verarbeitung fehlgeschlagen", e)
                Verlauf(context).merkeMeldung("Fehler: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                // Ein Empfaenger darf nicht warten - nach onReceive ist der
                // Prozess frei. goAsync haelt ihn so lange am Leben.
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
        // Nachricht auf INTENT_APP_ACK. RECEIVE_ACK ist die Gegenrichtung -
        // das schickt die Pebble-App hinaus, wenn die UHR eine Nachricht von
        // UNS bestaetigt hat (siehe UhrSender). Ein ACK dorthin hoert niemand,
        // und die Uhr lief in SEND_TIMEOUT.
        //
        // Kein setPackage: der Empfaenger der Pebble-App ist zur Laufzeit
        // angemeldet und bekommt implizite Broadcasts.
        context.sendBroadcast(
            Intent(ACTION_ACK).apply { putExtra(EXTRA_TRANSACTION_ID, transaktion) }
        )
    }

    /**
     * Das klassische PebbleKit-Format: ein JSON-Feld je Eintrag mit key, type,
     * length und value.
     *
     * Zeichenketten werden inzwischen mitgelesen - die Uhr-Apps schicken zwar
     * nur Zahlen, aber seit es die Gegenrichtung gibt, ist das Format an beiden
     * Enden dasselbe, und ein Wert, den wir schicken koennen, sollten wir auch
     * lesen koennen.
     */
    private fun leseWoerterbuch(json: String): Map<Int, Wert> {
        val aus = HashMap<Int, Wert>()
        try {
            val a = JSONArray(json)
            for (i in 0 until a.length()) {
                val eintrag = a.optJSONObject(i) ?: continue
                val schluessel = eintrag.optInt("key", -1)
                if (schluessel < 0) continue
                when (eintrag.optString("type", "int")) {
                    "int", "uint" -> aus[schluessel] = Wert.Zahl(eintrag.optLong("value"))
                    "string" -> eintrag.optString("value", "").takeIf { it.isNotEmpty() }
                        ?.let { aus[schluessel] = Wert.Text(it) }
                    else -> Unit   // Rohdaten kommen nicht vor
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
