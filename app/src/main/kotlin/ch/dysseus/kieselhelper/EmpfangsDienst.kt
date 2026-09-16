package ch.dysseus.kieselhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Haelt den Empfaenger am Leben.
 *
 * WARUM DAS SEIN MUSS - und warum ein Eintrag im Manifest nicht reicht:
 * die Pebble-App verschickt eingehende AppMessages so:
 *
 *     val intent = Intent("com.getpebble.action.app.RECEIVE").apply { ... }
 *     context.sendOrderedBroadcast(intent, null)
 *
 * Ohne Paket und ohne Komponente - das ist ein IMPLIZITER Broadcast. Und seit
 * Android 8 bekommen im Manifest angemeldete Empfaenger solche Broadcasts nicht
 * mehr; nur zur LAUFZEIT angemeldete bekommen sie noch. In der Vorgaenger-App
 * stand der Empfaenger zuerst allein im Manifest und wurde deshalb nie
 * aufgerufen, ganz gleich was die Uhr schickte.
 *
 * Ein zur Laufzeit angemeldeter Empfaenger braucht aber einen laufenden Prozess.
 * Deshalb dieser Vordergrunddienst mit seiner stillen Meldung: er ist der Preis
 * dafuer, dass Messungen auch dann ankommen, wenn die App nicht offen ist.
 */
class EmpfangsDienst : Service() {

    private var empfaenger: PebbleEmpfaenger? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(MELDUNG_ID, baueMeldung())

        val e = PebbleEmpfaenger()
        val filter = IntentFilter(PebbleEmpfaenger.ACTION_RECEIVE)
        if (Build.VERSION.SDK_INT >= 33) {
            // Ab Android 13 muss ausdruecklich stehen, ob fremde Apps senden
            // duerfen. Sie duerfen - genau darum geht es hier.
            registerReceiver(e, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(e, filter)
        }
        empfaenger = e
        Log.i(PebbleEmpfaenger.TAG, "Empfaenger zur Laufzeit angemeldet")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: wird der Dienst wegen Speichermangels beendet, startet
        // ihn das System wieder. Ohne das verpasst man die naechste Messung,
        // ohne es zu merken.
        return START_STICKY
    }

    /**
     * Die Zeitgrenze fuer Vordergrunddienste.
     *
     * Seit Android 15 bekommen manche Dienstarten eine Uhr gestellt - dataSync
     * etwa nur sechs Stunden je vierundzwanzig. Genau deshalb ist dieser Dienst
     * inzwischen specialUse und NICHT mehr dataSync; fuer specialUse laeuft
     * keine solche Uhr.
     *
     * Die Behandlung bleibt trotzdem stehen. Sie kostet nichts, und wer darauf
     * nicht reagiert, dessen Dienst wird hart beendet - mit einer ANR-Meldung
     * obendrein. Der Fehler faellt erst nach einem halben Tag Laufzeit auf, also
     * genau dann, wenn die naechtliche Messung ankommen soll.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.i(PebbleEmpfaenger.TAG, "Zeitgrenze erreicht - Dienst startet neu")
        neustartPlanen()
        stopSelf(startId)
    }

    private fun neustartPlanen() {
        try {
            starte(applicationContext)
        } catch (e: Exception) {
            // Darf im Hintergrund abgelehnt werden. Dann holt es der
            // naechste App-Start oder der naechste Neustart des Telefons nach.
            Log.w(PebbleEmpfaenger.TAG, "Neustart abgelehnt: " + e.message)
        }
    }

    override fun onDestroy() {
        empfaenger?.let {
            try {
                unregisterReceiver(it)
            } catch (e: IllegalArgumentException) {
                // war nicht angemeldet - nichts zu tun
            }
        }
        empfaenger = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun baueMeldung(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val kanal = NotificationChannel(
                KANAL_ID,
                getString(R.string.kanal_name),
                // Niedrigste Stufe: die Meldung muss da sein, damit der Dienst
                // laufen darf, soll aber nicht stoeren.
                NotificationManager.IMPORTANCE_MIN,
            )
            kanal.setShowBadge(false)
            mgr.createNotificationChannel(kanal)
        }
        val oeffnen = PendingIntent.getActivity(
            this, 0, Intent(this, HauptActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, KANAL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.dienst_laeuft))
            .setContentIntent(oeffnen)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val KANAL_ID = "empfang"
        private const val MELDUNG_ID = 1

        fun starte(context: Context) {
            val intent = Intent(context, EmpfangsDienst::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
