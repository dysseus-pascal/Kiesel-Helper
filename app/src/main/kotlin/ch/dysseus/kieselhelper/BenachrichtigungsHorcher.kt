package ch.dysseus.kieselhelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Zweite Quelle: was das Telefon selbst meldet.
 *
 * WARUM DAS DIE INTERESSANTERE HAELFTE IST: die Uhr schickt, was eine Uhr-App
 * ausdruecklich schickt. Eine Benachrichtigung schickt jede App auf dem
 * Telefon, ohne davon zu wissen - Navigation, Nachrichten, Kalender,
 * Paketverfolgung. Damit steht dem Zettel alles offen, was auf dem Telefon
 * ohnehin erscheint.
 *
 * WARUM DIE KOMPONENTE SCHON DA IST, BEVOR EIN ZETTEL SIE BRAUCHT: sie liesse
 * sich nicht nachreichen. Ein Dienst muss im Manifest stehen, und das Manifest
 * wird beim Installieren festgeschrieben. Genau das ist die Mauer, um die es
 * hier die ganze Zeit geht.
 *
 * ALS HINTERGRUNDLAEUFER IST ER DAS BESTE, WAS ANDROID HERGIBT: das System
 * bindet ihn selbst, bindet nach einem Neustart und nach einem Absturz wieder,
 * ohne Vordergrunddienst und ohne die Sechs-Stunden-Grenze, die Dienste vom
 * Typ dataSync trifft. Die Freigabe erteilt der Benutzer einmal in den
 * Systemeinstellungen; danach laeuft es.
 *
 * GELESEN WIRD NUR, WAS EIN ZETTEL VERLANGT. Ohne passenden Zettel sieht diese
 * Klasse jede Benachrichtigung und wirft sie sofort weg - siehe die erste
 * Abfrage in [onNotificationPosted].
 */
class BenachrichtigungsHorcher : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(PebbleEmpfaenger.TAG, "Benachrichtigungen: verbunden")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return

        // Zuerst und ohne irgendetwas zu lesen: gibt es ueberhaupt einen
        // Zettel fuer dieses Paket? Ohne diesen Riegel liefe der ganze Inhalt
        // jeder Benachrichtigung des Telefons durch diesen Code.
        val module = ModulSpeicher(this).alle().filter {
            (it.quelle as? Quelle.Benachrichtigung)?.paket == n.packageName
        }
        if (module.isEmpty()) return

        val felder = lies(n)
        CoroutineScope(Dispatchers.IO).launch {
            for (m in module) {
                try {
                    Regelwerk.wendeAn(applicationContext, m, felder)
                } catch (e: Exception) {
                    Log.e(PebbleEmpfaenger.TAG, "Zettel " + m.name + " fehlgeschlagen", e)
                }
            }
        }
    }

    /**
     * Die Benachrichtigung in benannte Felder zerlegen.
     *
     * Die Namen stehen in [Benachrichtigungsfeld] - dort und nur dort, damit
     * das Einlesen eines Zettels und das Fuellen hier nicht auseinanderlaufen.
     */
    private fun lies(n: StatusBarNotification): Map<String, Wert> {
        val aus = mutableMapOf<String, Wert>()
        aus[Benachrichtigungsfeld.PAKET] = Wert.Text(n.packageName)
        aus[Benachrichtigungsfeld.WANN] = Wert.Zahl(n.postTime)
        aus[Benachrichtigungsfeld.DAUERHAFT] = Wert.Zahl(if (n.isOngoing) 1L else 0L)

        val extras = n.notification?.extras ?: return aus
        fun nimm(name: String, schluessel: String) {
            @Suppress("DEPRECATION")
            Wert.aus(extras.get(schluessel))?.let { aus[name] = it }
        }
        nimm(Benachrichtigungsfeld.TITEL, android.app.Notification.EXTRA_TITLE)
        nimm(Benachrichtigungsfeld.TEXT, android.app.Notification.EXTRA_TEXT)
        nimm(Benachrichtigungsfeld.UNTERTEXT, android.app.Notification.EXTRA_SUB_TEXT)
        nimm(Benachrichtigungsfeld.GROSSTEXT, android.app.Notification.EXTRA_BIG_TEXT)
        nimm(Benachrichtigungsfeld.ZUSATZ, android.app.Notification.EXTRA_INFO_TEXT)
        Wert.aus(n.notification?.tickerText)?.let { aus[Benachrichtigungsfeld.TICKER] = it }

        // Jedes Extra zusaetzlich unter seinem eigenen Namen. Das ist die
        // Hintertuer fuer Apps, die ihre Angabe nicht in Titel oder Text legen
        // - und die Stelle, an der ein Zettel auf eine App zugeschnitten wird,
        // ohne dass die App davon etwas wissen muss.
        for (schluessel in extras.keySet()) {
            @Suppress("DEPRECATION")
            Wert.aus(extras.get(schluessel))?.let {
                aus[Benachrichtigungsfeld.EXTRA_VORSATZ + schluessel] = it
            }
        }
        return aus
    }

    companion object {

        /** Hat der Benutzer den Zugriff auf Benachrichtigungen freigegeben? */
        fun freigegeben(context: Context): Boolean {
            val erlaubt = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            val eigen = ComponentName(context, BenachrichtigungsHorcher::class.java)
            return erlaubt.split(":").any {
                val c = ComponentName.unflattenFromString(it)
                c != null && c.packageName == eigen.packageName &&
                    c.className == eigen.className
            }
        }

        /**
         * Die Systemeinstellung oeffnen, in der der Benutzer freigibt.
         *
         * Es gibt keinen Dialog dafuer und keine Abfrage zur Laufzeit - diese
         * Freigabe wird ausschliesslich in den Einstellungen erteilt. Mehr als
         * hinfuehren kann eine App nicht.
         */
        fun einstellungen(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
