package ch.dysseus.kieselhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Faehrt den Empfangsdienst nach einem Neustart wieder hoch.
 *
 * BOOT_COMPLETED ist einer der wenigen impliziten Broadcasts, die ein im
 * Manifest angemeldeter Empfaenger auch ab Android 8 noch bekommt - er steht
 * auf der Ausnahmeliste. Fuer com.getpebble.action.app.RECEIVE gilt das nicht,
 * genau deshalb gibt es den Dienst ueberhaupt.
 *
 * WAS HIER NICHT STEHEN DARF: ein Dienst vom Typ dataSync. Seit Android 15
 * lehnt das System genau das ab - aus einem BOOT_COMPLETED-Empfaenger duerfen
 * die Arten dataSync, camera, mediaPlayback, phoneCall, mediaProjection und
 * microphone nicht gestartet werden. Deshalb ist EmpfangsDienst specialUse.
 *
 * Der Fang drumherum bleibt trotzdem: schlaegt der Start doch einmal fehl,
 * soll das Telefon nicht mit einem Absturz hochfahren. Der naechste Start der
 * App holt den Dienst dann nach.
 */
class StartEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            EmpfangsDienst.starte(context)
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Start nach Neustart abgelehnt: " + e.message)
        }
    }
}
