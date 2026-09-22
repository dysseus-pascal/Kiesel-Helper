package ch.dysseus.kieselhelper

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat

/**
 * Schreibt mit, wo man während eines Trainings war.
 *
 * ER LÄUFT NUR, SOLANGE EIN TRAINING LÄUFT. Das ist der ganze Unterschied zu
 * einer App, die dauernd den Standort kennt: Kieselsport meldet den Start, der
 * Dienst geht an; es meldet das Ende, der Dienst geht aus. Dazwischen liegt
 * genau die Zeit, für die jemand eine Strecke sehen will.
 *
 * EIN VORDERGRUNDDIENST MIT SICHTBARER MELDUNG, und das ist keine Formalie:
 * Android verlangt sie für Ortung im Hintergrund, und wer den Standort eines
 * Menschen aufzeichnet, soll das nicht lautlos tun können.
 *
 * KEIN GOOGLE-STANDORTDIENST. Der LocationManager des Systems genügt, kostet
 * keine Abhängigkeit und läuft auch auf einem Telefon ohne Play-Dienste.
 */
class SpurDienst : android.app.Service(), LocationListener {

    private var beginn: Long = 0
    private var laeuft = false

    /**
     * Wie dicht gemessen wird.
     *
     * Drei Sekunden und fünf Meter: dichter macht die Linie nicht genauer,
     * nur die Datei grösser und den Akku leerer. Ein Läufer legt in drei
     * Sekunden gut zehn Meter zurück.
     */
    private val takt = 3000L
    private val abstand = 5f

    override fun onCreate() {
        super.onCreate()
        meldungskanal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AUS -> {
                halt()
                return START_NOT_STICKY
            }
            else -> {
                beginn = intent?.getLongExtra(EXTRA_BEGINN, 0L) ?: 0L
                if (beginn <= 0L) beginn = System.currentTimeMillis() / 1000
                starteVordergrund(intent?.getStringExtra(EXTRA_ART) ?: "Training")
                horche()
            }
        }
        // NICHT STICKY. Startet Android den Dienst nach einem Abschuss neu,
        // wuesste er weder, welches Training laeuft, noch ob ueberhaupt eines
        // laeuft - und zeichnete ins Leere.
        return START_NOT_STICKY
    }

    private fun starteVordergrund(art: String) {
        val tippen = PendingIntent.getActivity(
            this, 0, Intent(this, HauptActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val meldung: Notification = Notification.Builder(this, KANAL)
            .setContentTitle(art + " wird aufgezeichnet")
            .setContentText("Die Strecke kommt vom Telefon — die Uhr hat kein GPS.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(tippen)
            .setOngoing(true)
            .build()

        // Ab Android 14 muss die Art des Dienstes beim Start mitgegeben
        // werden; ohne sie wirft das System eine Ausnahme.
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, MELDUNG_ID, meldung,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(MELDUNG_ID, meldung)
        }
    }

    private fun horche() {
        if (laeuft) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // OHNE ERLAUBNIS KEINE SPUR, aber auch kein Absturz: das Training
            // wird trotzdem eingetragen, nur ohne Strecke.
            Log.w(PebbleEmpfaenger.TAG, "Keine Standortberechtigung - Spur entfällt")
            Verlauf(this).merkeMeldung(
                "Training ohne Strecke — Standortberechtigung fehlt"
            )
            halt()
            return
        }
        val verwalter = getSystemService(LocationManager::class.java) ?: return
        try {
            verwalter.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, takt, abstand, this
            )
            laeuft = true
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Ortung: " + e.message)
            halt()
        }
    }

    private fun halt() {
        if (laeuft) {
            try {
                getSystemService(LocationManager::class.java)?.removeUpdates(this)
            } catch (e: Exception) {
                // schon abgemeldet - nichts zu tun
            }
            laeuft = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(ort: Location) {
        Spur.haengeAn(this, beginn, ort)
    }

    override fun onDestroy() {
        halt()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun meldungskanal() {
        val kanal = NotificationChannel(
            KANAL, "Streckenaufzeichnung", NotificationManager.IMPORTANCE_LOW
        )
        kanal.description = "Läuft nur während eines Trainings."
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(kanal)
    }

    companion object {
        private const val KANAL = "kiesel-spur"
        private const val MELDUNG_ID = 4711
        private const val AUS = "ch.dysseus.kieselhelper.SPUR_AUS"
        private const val EXTRA_BEGINN = "beginn"
        private const val EXTRA_ART = "art"

        fun starte(context: Context, beginn: Long, art: String) {
            val i = Intent(context, SpurDienst::class.java)
                .putExtra(EXTRA_BEGINN, beginn)
                .putExtra(EXTRA_ART, art)
            context.startForegroundService(i)
        }

        fun stoppe(context: Context) {
            val i = Intent(context, SpurDienst::class.java).setAction(AUS)
            try {
                context.startService(i)
            } catch (e: Exception) {
                // Laeuft er nicht mehr, ist nichts zu stoppen.
                Log.i(PebbleEmpfaenger.TAG, "Spurdienst lief nicht: " + e.message)
            }
        }
    }
}
