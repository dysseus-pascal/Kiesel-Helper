package ch.dysseus.kieselhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Zweite Quelle: was die Uhr im Hintergrund gesammelt hat (Data Logging).
 *
 * WARUM ES DAS BRAUCHT: der Hintergrund-Worker von Kieselsport kann keine
 * AppMessage schicken. Er schreibt die Pulskurve jede Sekunde in ein Log der
 * Uhr, und die Pebble-App holt es ab, sobald sie die Uhr erreicht - auch
 * Stunden spaeter, und ohne dass die Uhr-App offen ist. Dann kommt je Satz
 * ein Broadcast hier an.
 *
 * DAS KLASSISCHE PEBBLEKIT-FORMAT, wie beim Nachrichtenempfaenger: keine
 * Bibliothek, nur die Intent-Namen. Ein Satz ist ein Byte-Feld (Base64 im
 * Intent), acht Byte little endian: Beginn (4), Sekunde (2), Puls (2). Jeder
 * Satz wird bestaetigt (ACK_DATA), sonst schickt die Pebble-App ihn wieder.
 *
 * GEPRUEFT, UND ES KAM NICHTS AN: die App von Core Devices reicht Data
 * Logging nicht an klassische Companion-Apps weiter (Stand 0.38.1). Die Kurve
 * kommt deshalb seit Kieselsport 0.10.0 ueber AppMessage in Stuecken (siehe
 * Aufgaben.training). Dieser Empfaenger bleibt: sollte die Pebble-App es
 * einmal koennen, kommt die Kurve auf diesem Weg frueher.
 */
class DatenlogEmpfaenger : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val uuid = leseUuid(intent) ?: return
        if (uuid != Aufgaben.KIESELSPORT_UUID) return

        when (intent.action) {
            ACTION_RECEIVE_DATA -> empfange(context, intent)
            ACTION_FINISH_SESSION -> {
                Log.i(PebbleEmpfaenger.TAG, "Datenlog abgeschlossen")
                val ergebnis = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        Pulskurve.alleEintragen(context)
                    } finally {
                        ergebnis.finish()
                    }
                }
            }
        }
    }

    private fun empfange(context: Context, intent: Intent) {
        val logUuid = intent.getStringExtra(EXTRA_DATA_LOG_UUID)
        val id = intent.getIntExtra(EXTRA_DATA_ID, -1)
        // ZUERST BESTAETIGEN, wie bei den Nachrichten: die Pebble-App wartet
        // darauf und wiederholt sonst denselben Satz.
        if (logUuid != null && id >= 0) {
            context.sendBroadcast(
                Intent(ACTION_ACK_DATA).apply {
                    putExtra(EXTRA_UUID, Aufgaben.KIESELSPORT_UUID)
                    putExtra(EXTRA_DATA_LOG_UUID, logUuid)
                    putExtra(EXTRA_DATA_ID, id)
                }
            )
        }
        if (intent.getIntExtra(EXTRA_DATA_LOG_TAG, -1) != TAG_PULS) return
        if (intent.getIntExtra(EXTRA_DATA_TYPE, -1) != TYP_BYTES) return
        val roh = intent.getStringExtra(EXTRA_DATA_OBJECT) ?: return
        val bytes = try {
            Base64.decode(roh, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return
        }
        // Mehrere Saetze koennen in einem Feld liegen: acht Byte je Satz.
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        while (b.remaining() >= 8) {
            val beginn = b.int.toLong() and 0xFFFFFFFFL
            val sekunde = b.short.toInt() and 0xFFFF
            val puls = b.short.toInt() and 0xFFFF
            if (beginn > 0) Pulskurve.anhaengen(context, beginn, sekunde, puls)
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

    companion object {
        const val ACTION_RECEIVE_DATA = "com.getpebble.action.dl.RECEIVE_DATA"
        const val ACTION_ACK_DATA = "com.getpebble.action.dl.ACK_DATA"
        const val ACTION_FINISH_SESSION = "com.getpebble.action.dl.FINISH_SESSION"
        const val EXTRA_UUID = "uuid"
        const val EXTRA_DATA_LOG_UUID = "data_log_uuid"
        const val EXTRA_DATA_LOG_TAG = "data_log_tag"
        const val EXTRA_DATA_ID = "pbl_data_id"
        const val EXTRA_DATA_TYPE = "pbl_data_type"
        const val EXTRA_DATA_OBJECT = "pbl_data_object"
        /** Das Log mit der Pulskurve - dieselbe Nummer wie im Worker. */
        const val TAG_PULS = 1
        /** PebbleDataType.BYTES */
        const val TYP_BYTES = 0
    }
}
