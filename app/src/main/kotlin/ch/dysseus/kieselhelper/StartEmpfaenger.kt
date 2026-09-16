package ch.dysseus.kieselhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Faehrt den Empfangsdienst nach einem Neustart wieder hoch.
 *
 * BOOT_COMPLETED ist einer der wenigen impliziten Broadcasts, die ein im
 * Manifest angemeldeter Empfaenger auch ab Android 8 noch bekommt - er steht
 * auf der Ausnahmeliste. Fuer com.getpebble.action.app.RECEIVE gilt das nicht,
 * genau deshalb gibt es den Dienst ueberhaupt.
 */
class StartEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        EmpfangsDienst.starte(context)
    }
}
