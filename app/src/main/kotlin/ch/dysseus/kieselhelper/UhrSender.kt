package ch.dysseus.kieselhelper

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Der Weg zurueck: vom Telefon an eine Uhr-App.
 *
 * Die Gegenrichtung zu [PebbleEmpfaenger] und genauso schlicht - drei
 * Broadcasts, keine Bibliothek. Nachzulesen in coredevices/mobileapp unter
 * libpebble3/.../pebblekit/classic/, wo dieselben Namen auf der anderen Seite
 * stehen.
 *
 * DAS WOERTERBUCH IST NICHT FREI ERFUNDEN. Es ist das Format des alten
 * PebbleKit, und zwei Dinge daran sind leicht falsch zu machen:
 *
 *  1. Das Feld heisst `length`, traegt aber die BREITE der Zahl, nicht ihre
 *     Laenge: 0 fuer Text, 1, 2 oder 4 fuer ganze Zahlen. Die Gegenseite liest
 *     es mit WIDTH_MAP.get(o.getInt(LENGTH)) - ein falscher Wert dort und das
 *     Tupel faellt still unter den Tisch.
 *  2. `type` ist der Kleinbuchstabenname: "string", "int", "uint", "bytes".
 *
 * Und eine Eigenheit der Uhr: eine AppMessage erreicht nur die LAUFENDE App.
 * Deshalb kann ein Zettel `starten` setzen - dann geht ein START voraus.
 */
object UhrSender {

    private val naechsteNummer = AtomicInteger(1)

    /**
     * Eine Nachricht an eine Uhr-App schicken.
     *
     * Ohne auf die Bestaetigung zu warten. Die Uhr antwortet auf
     * RECEIVE_ACK beziehungsweise RECEIVE_NACK - fuer eine Bruecke, die
     * fortlaufend Navigationsanweisungen nachschiebt, ist die naechste
     * Nachricht aber ohnehin schneller da als eine Antwort auf die letzte.
     */
    fun sende(context: Context, an: UUID, starten: Boolean, felder: Map<Int, Wert>): String {
        if (felder.isEmpty()) return "nichts zu senden"

        if (starten) {
            // Idempotent laut Gegenseite: laeuft die App schon, passiert nichts.
            context.sendBroadcast(
                Intent(ACTION_START).apply { putExtra(EXTRA_UUID, an) }
            )
        }

        val nummer = naechsteNummer.getAndIncrement() and 0xFF
        val intent = Intent(ACTION_SEND).apply {
            putExtra(EXTRA_UUID, an)
            putExtra(EXTRA_TRANSACTION_ID, nummer)
            putExtra(EXTRA_MSG_DATA, woerterbuch(felder))
        }
        context.sendBroadcast(intent)
        Log.i(PebbleEmpfaenger.TAG, "An die Uhr: " + felder.size + " Felder an " + an)
        return felder.size.toString() + " Felder an die Uhr"
    }

    /** Die Uhr-App anhalten. Bisher von keinem Zettel benutzt, aber symmetrisch. */
    fun halteAn(context: Context, an: UUID) {
        context.sendBroadcast(Intent(ACTION_STOP).apply { putExtra(EXTRA_UUID, an) })
    }

    internal fun woerterbuch(felder: Map<Int, Wert>): String {
        val a = JSONArray()
        for ((nummer, wert) in felder) {
            val o = JSONObject()
            o.put(SCHLUESSEL, nummer)
            when (wert) {
                is Wert.Text -> {
                    o.put(TYP, "string")
                    o.put(BREITE, 0)          // Width.NONE
                    o.put(WERT, wert.text)
                }
                is Wert.Zahl -> {
                    o.put(TYP, "int")
                    o.put(BREITE, 4)          // Width.WORD
                    // Die Gegenseite liest mit getInt(); alles jenseits von
                    // 32 Bit passt ohnehin nicht in ein Tupel dieser Breite.
                    o.put(WERT, wert.zahl.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()))
                }
            }
            a.put(o)
        }
        return a.toString()
    }

    private const val ACTION_SEND = "com.getpebble.action.app.SEND"
    private const val ACTION_START = "com.getpebble.action.app.START"
    private const val ACTION_STOP = "com.getpebble.action.app.STOP"

    private const val EXTRA_UUID = "uuid"
    private const val EXTRA_TRANSACTION_ID = "transaction_id"
    private const val EXTRA_MSG_DATA = "msg_data"

    private const val SCHLUESSEL = "key"
    private const val TYP = "type"
    private const val BREITE = "length"
    private const val WERT = "value"
}
