package ch.dysseus.kieselhelper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Vier Zahlen auf dem Startbildschirm.
 *
 * DAS WIDGET IST DER EIGENTLICHE PUNKT der ganzen Uebung. Eine App oeffnen,
 * um zu sehen, wie weit man heute gelaufen ist, kostet mehr Aufmerksamkeit,
 * als die Antwort wert ist. Hier steht sie, wenn man das Telefon ohnehin
 * anschaut.
 *
 * NUR WERTE MIT ZIEL. Schritte, Bewegung, Schlaf, Wasser - die vier, bei denen
 * ein Balken etwas bedeutet. Ruhepuls und HRV stehen ohne Balken in der
 * Fusszeile; sie sind Zustand, nicht Fortschritt.
 *
 * Kein Glance und kein Compose: ein Widget lebt in einem fremden Prozess und
 * kennt nur RemoteViews. Was hier steht, sind Anweisungen an den
 * Startbildschirm, keine Ansichten.
 */
class GesundheitWidget : AppWidgetProvider() {

    /**
     * Neu zeichnen - und zwar mit frisch gelesenen Zahlen.
     *
     * DIE AKTE ANTWORTET LANGSAM, ein Broadcast hat aber nur wenige Sekunden.
     * [goAsync] haelt den Empfang so lange offen; ohne ihn beendet Android den
     * Prozess mitten im Lesen, und das Widget bliebe auf dem alten Stand,
     * ohne dass irgendwo etwas schiefginge.
     */
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val fertig = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sicht = zeichne(context, Gesundheit(context).lies())
                ids.forEach { manager.updateAppWidget(it, sicht) }
            } catch (e: Exception) {
                Log.w(PebbleEmpfaenger.TAG, "Widget: " + e.message)
            } finally {
                fertig.finish()
            }
        }
    }

    companion object {

        /**
         * Das Widget auffordern, neu zu lesen.
         *
         * Gerufen, nachdem diese App selbst etwas eingetragen hat, und wenn
         * der Gesundheits-Schirm frische Zahlen geholt hat. Ein Widget, das
         * aelter ist als die App, die daneben offen war, sieht nach Fehler
         * aus, obwohl nur die halbe Stunde noch nicht um war.
         */
        fun stosseAn(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GesundheitWidget::class.java)
            )
            if (ids == null || ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, GesundheitWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }

        private fun zeichne(context: Context, stand: Gesundheit.Stand?): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_gesundheit)

            v.setOnClickPendingIntent(
                R.id.w_wurzel,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, HauptActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )

            // DER KNOPF IST DIE ANTWORT AUF DIE HALBE STUNDE. Android laesst
            // ein Widget nicht oefter von selbst nachsehen; wer es jetzt
            // wissen will, tippt hier und wartet zwei Sekunden.
            v.setOnClickPendingIntent(
                R.id.w_auffrischen,
                PendingIntent.getBroadcast(
                    context, 1,
                    Intent(context, GesundheitWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_IDS,
                            AppWidgetManager.getInstance(context).getAppWidgetIds(
                                ComponentName(context, GesundheitWidget::class.java)
                            )
                        )
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )

            // WIE ALT, NICHT WANN. "21:12" beantwortet die Frage nicht, die
            // man am Widget hat; "vor 04:30" beantwortet sie. Der Chronometer
            // zaehlt selbst weiter und braucht dafuer keinen Wecker - ein
            // Dreiminutentakt aus einem Alarm waere vierhundertachtzig
            // Weckrufe am Tag fuer eine Textzeile.
            v.setChronometer(
                R.id.w_stand, SystemClock.elapsedRealtime(), "vor %s", true
            )

            if (stand == null) {
                v.setTextViewText(R.id.w_fuss, "Keine Gesundheitsakte")
                return v
            }

            kachel(v, R.id.w_schritte_wert, R.id.w_schritte_balken,
                   Zahlen.ganz(stand.schritte.zahl), stand.schritte.anteil)
            kachel(v, R.id.w_aktiv_wert, R.id.w_aktiv_balken,
                   Zahlen.ganz(stand.aktiv.zahl)?.plus(" min"), stand.aktiv.anteil)
            kachel(v, R.id.w_schlaf_wert, R.id.w_schlaf_balken,
                   Zahlen.dauer(stand.schlaf.zahl), stand.schlaf.anteil)
            kachel(v, R.id.w_wasser_wert, R.id.w_wasser_balken,
                   Zahlen.ganz(stand.wasser.zahl)?.plus(" ml"), stand.wasser.anteil)

            v.setTextViewText(R.id.w_fuss, fusszeile(stand))
            return v
        }

        private fun kachel(
            v: RemoteViews,
            wertId: Int,
            balkenId: Int,
            text: String?,
            anteil: Float,
        ) {
            v.setTextViewText(wertId, text ?: "—")
            v.setProgressBar(balkenId, 100, (anteil * 100).toInt(), false)
        }

        /**
         * Die leise Zeile unten: was keinen Balken hat.
         *
         * Fehlt ein Wert, faellt er samt Trenner weg. Ein "Ruhepuls —" waere
         * eine Zeile Platz fuer die Auskunft, dass es keine Auskunft gibt.
         */
        private fun fusszeile(stand: Gesundheit.Stand): String {
            val teile = mutableListOf<String>()
            Zahlen.ganz(stand.ruhepuls.zahl)?.let { teile += "Ruhepuls $it" }
            Zahlen.ganz(stand.hrv.zahl)?.let { teile += "HRV $it ms" }
            Zahlen.eine(stand.distanz.zahl)?.let { teile += "$it km" }
            return if (teile.isEmpty()) "Noch nichts eingetragen"
                   else teile.joinToString(" · ")
        }
    }
}
