package ch.dysseus.kieselhelper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Eine Buehne und drei Zahlen auf dem Startbildschirm.
 *
 * DAS WIDGET IST DER EIGENTLICHE PUNKT der ganzen Uebung. Eine App oeffnen,
 * um zu sehen, wie weit man heute gelaufen ist, kostet mehr Aufmerksamkeit,
 * als die Antwort wert ist. Hier steht sie, wenn man das Telefon ohnehin
 * anschaut.
 *
 * UND ES WEISS, WAS GERADE ZAEHLT (siehe Widgetlage): morgens die Nacht, nach
 * dem Training das Training, sonst die Schritte, abends die Bilanz. Die drei
 * kleinen Kacheln zeigen den Rest; ein Tipp auf eine macht sie fuer eine
 * Stunde zur Buehne - der Ausweg, wenn die Automatik daneben liegt.
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
    override fun onReceive(context: Context, intent: Intent) {
        // Ein Tipp auf eine kleine Kachel: diese Lage fuer eine Stunde, dann
        // wieder die Automatik. Danach ganz normal neu zeichnen.
        if (intent.action == ACTION_LAGE) {
            val wunsch = intent.getStringExtra(EXTRA_LAGE)
            context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).edit()
                .putString(WUNSCH, wunsch)
                .putLong(WUNSCH_BIS, System.currentTimeMillis() + 60 * 60 * 1000)
                .apply()
            stosseAn(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val fertig = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val stand = Gesundheit(context).lies()
                val sicht = zeichne(context, stand, stand?.let { blick(context, it) })
                ids.forEach { manager.updateAppWidget(it, sicht) }
            } catch (e: Exception) {
                Log.w(PebbleEmpfaenger.TAG, "Widget: " + e.message)
            } finally {
                fertig.finish()
            }
        }
    }

    companion object {

        private const val DATEI = "kiesel-widget"
        private const val SCHLUESSEL = "gelesen"
        private const val WUNSCH = "wunsch"
        private const val WUNSCH_BIS = "wunsch_bis"
        const val ACTION_LAGE = "ch.dysseus.kieselhelper.WIDGET_LAGE"
        const val EXTRA_LAGE = "lage"

        /**
         * Spaetestens so alt darf der Stand werden.
         *
         * Androids eigener Takt kann nicht unter eine halbe Stunde. Diese
         * Grenze zieht deshalb der Minutentakt des laufenden Dienstes: bei
         * jedem Tick wird geschaut, ob zehn Minuten um sind.
         */
        private const val HOECHSTALTER_MIN = 10L

        private fun merkeZeitpunkt(context: Context) {
            context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
                .edit().putLong(SCHLUESSEL, System.currentTimeMillis()).apply()
        }

        private fun alterMinuten(context: Context): Long {
            val dann = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
                .getLong(SCHLUESSEL, 0L)
            if (dann <= 0L) return -1L
            return (System.currentTimeMillis() - dann) / 60000
        }

        /**
         * Das Alter in Worten - in ganzen Minuten.
         *
         * "vor 0 min" waere richtig und liest sich falsch; in der ersten
         * Minute heisst es deshalb "gerade eben".
         */
        private fun alter(context: Context): String {
            val min = alterMinuten(context)
            return when {
                min < 0 -> ""
                min < 1 -> "gerade eben"
                min < 60 -> "vor $min min"
                else -> "vor " + (min / 60) + " h"
            }
        }

        /**
         * Der Minutentakt: Text nachziehen, und alle zehn Minuten neu lesen.
         *
         * GERUFEN AUS DEM LAUFENDEN DIENST, nicht aus einem Wecker. Android
         * schickt ACTION_TIME_TICK jede Minute an angemeldete Empfaenger -
         * aber nur bei eingeschaltetem Bildschirm, also genau dann, wenn
         * jemand hinschauen koennte. Ein eigener Wecker im Minutentakt waere
         * 1440 Weckrufe am Tag fuer eine Textzeile.
         *
         * Nur der Text wird nachgezogen (partiallyUpdateAppWidget), nicht das
         * ganze Widget: die Zahlen dafuer neu aus der Akte zu holen, waere
         * sechzig Abfragen in der Stunde fuer eine Zeile, die sich um eine
         * Minute geaendert hat.
         */
        fun taktet(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, GesundheitWidget::class.java)
            )
            if (ids == null || ids.isEmpty()) return

            if (alterMinuten(context) >= HOECHSTALTER_MIN) {
                stosseAn(context)
                return
            }
            val nur = RemoteViews(context.packageName, R.layout.widget_gesundheit)
            nur.setTextViewText(R.id.w_stand, alter(context))
            manager.partiallyUpdateAppWidget(ids, nur)
        }

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

        /**
         * Der Blick auf den Stand, wie die Lagenlogik ihn braucht.
         *
         * Das letzte Training kommt direkt aus der Akte: der Stand fuehrt es
         * nicht, und der Trainings-Reiter holt zu viel (Spur, Pulskurve)
         * fuer ein Widget.
         */
        private suspend fun blick(context: Context, stand: Gesundheit.Stand): Widgetlage.Blick {
            val zone = java.time.ZoneId.systemDefault()
            val jetzt = java.time.LocalDateTime.now(zone)
            val heute = Einstellungen.heute(context)

            // Die Nacht endete: Minuten seit 18 Uhr des Vortags.
            val schlafEnde = stand.nachtzeiten?.let {
                heute.minusDays(1).atTime(Gesundheit.NACHT_AB).plusMinutes(it.bis.toLong())
            }
            val woche = stand.wocheSchlaf.filter { it.tag != heute }.mapNotNull { it.zahl }
            val erholsam = stand.phasen?.takeIf { it.da }?.let { (it.tief + it.rem) / it.summe }

            // Das letzte Training der letzten drei Stunden
            var tEnde: java.time.LocalDateTime? = null
            var tName: String? = null
            var tMin: Long? = null
            var tPuls: Double? = null
            var tKm: Double? = null
            var tBeginn: Long? = null
            try {
                val klient = Akte(context).bereit()
                val jetztI = java.time.Instant.now()
                val s = klient?.readRecords(
                    androidx.health.connect.client.request.ReadRecordsRequest(
                        androidx.health.connect.client.records.ExerciseSessionRecord::class,
                        androidx.health.connect.client.time.TimeRangeFilter.between(jetztI.minusSeconds(3 * 3600), jetztI),
                    )
                )?.records?.maxByOrNull { it.endTime }
                if (s != null && klient != null) {
                    tEnde = java.time.LocalDateTime.ofInstant(s.endTime, zone)
                    tName = s.title ?: Sportart.von(s).name
                    tMin = java.time.Duration.between(s.startTime, s.endTime).toMinutes()
                    tBeginn = s.startTime.epochSecond
                    val puls = klient.readRecords(
                        androidx.health.connect.client.request.ReadRecordsRequest(
                            androidx.health.connect.client.records.HeartRateRecord::class,
                            androidx.health.connect.client.time.TimeRangeFilter.between(s.startTime, s.endTime),
                        )
                    ).records.flatMap { it.samples }.map { it.beatsPerMinute }
                    if (puls.isNotEmpty()) tPuls = puls.average()
                    val punkte = Spur.lies(context, s.startTime.epochSecond)
                    if (punkte.size >= 2) tKm = Spur.laenge(punkte) / 1000
                }
            } catch (e: Exception) {
                Log.w(PebbleEmpfaenger.TAG, "Widget, Training: " + e.message)
            }

            val prefs = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            val wunsch = if (prefs.getLong(WUNSCH_BIS, 0L) > System.currentTimeMillis()) {
                prefs.getString(WUNSCH, null)?.let { runCatching { Widgetlage.Art.valueOf(it) }.getOrNull() }
            } else null

            return Widgetlage.Blick(
                jetzt = jetzt,
                schlafEnde = schlafEnde,
                schlafMin = stand.schlaf.zahl,
                schlafZielMin = stand.schlaf.ziel,
                schlafWocheMin = woche.takeIf { it.isNotEmpty() }?.average(),
                erholsamAnteil = erholsam,
                ruhepuls = stand.ruhepuls.zahl,
                hrv = stand.hrv.zahl,
                trainingEnde = tEnde, trainingName = tName, trainingMin = tMin,
                trainingPuls = tPuls, trainingKm = tKm, trainingBeginn = tBeginn,
                schritte = stand.schritte.zahl, schritteZiel = stand.schritte.ziel,
                aktivMin = stand.aktiv.zahl, aktivZiel = stand.aktiv.ziel,
                wasserMl = stand.wasser.zahl, wasserZiel = stand.wasser.ziel,
                glasMl = Einstellungen.glasMl(context).toDouble(),
                offenePraeparate = stand.suppListe.filter { !it.genommen }.map { it.name },
                koffeinMg = stand.koffeinMg,
                wunsch = wunsch,
            )
        }

        private fun zeichne(context: Context, stand: Gesundheit.Stand?, blick: Widgetlage.Blick?): RemoteViews {
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

            merkeZeitpunkt(context)
            v.setTextViewText(R.id.w_stand, alter(context))
            faerbe(context, v)

            if (stand == null || blick == null) {
                v.setTextViewText(R.id.w_lage, "Heute")
                v.setTextViewText(R.id.w_gross, "—")
                v.setTextViewText(R.id.w_satz, "")
                v.setTextViewText(R.id.w_fuss, "Keine Gesundheitsakte")
                return v
            }

            val lage = Widgetlage.ermittle(blick)
            v.setTextViewText(R.id.w_lage, lage.wort)
            v.setTextViewText(R.id.w_name, lage.name)
            v.setTextViewText(R.id.w_gross, lage.gross)
            v.setTextViewText(R.id.w_einheit, lage.einheit)
            v.setTextViewText(R.id.w_satz, lage.satz)
            if (lage.anteil != null) {
                v.setViewVisibility(R.id.w_balken, android.view.View.VISIBLE)
                v.setProgressBar(R.id.w_balken, 100, (lage.anteil * 100).toInt(), false)
            } else {
                v.setViewVisibility(R.id.w_balken, android.view.View.GONE)
            }
            // Ein Training auf der Buehne fuehrt zu seiner Seite.
            lage.trainingBeginn?.let { beginn ->
                v.setOnClickPendingIntent(
                    R.id.w_buehne,
                    PendingIntent.getActivity(
                        context, 2,
                        Intent(context, VergangeneActivity::class.java)
                            .putExtra("beginn", beginn)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
            }

            val ids = listOf(
                Triple(R.id.w_k1, R.id.w_k1_name, Pair(R.id.w_k1_wert, R.id.w_k1_balken)),
                Triple(R.id.w_k2, R.id.w_k2_name, Pair(R.id.w_k2_wert, R.id.w_k2_balken)),
                Triple(R.id.w_k3, R.id.w_k3_name, Pair(R.id.w_k3_wert, R.id.w_k3_balken)),
            )
            lage.kacheln.take(3).forEachIndexed { i, k ->
                val (wurzel, nameId, rest) = ids[i]
                v.setTextViewText(nameId, k.name)
                kachel(v, rest.first, rest.second, k.text, k.anteil)
                // Ein Tipp macht die Kachel fuer eine Stunde zur Buehne.
                val wunsch = when (k.schluessel) {
                    "schlaf" -> Widgetlage.Art.MORGEN
                    "wasser" -> Widgetlage.Art.ERINNERUNG
                    else -> Widgetlage.Art.TAG
                }
                v.setOnClickPendingIntent(
                    wurzel,
                    PendingIntent.getBroadcast(
                        context, 10 + i,
                        Intent(context, GesundheitWidget::class.java).apply {
                            action = ACTION_LAGE
                            putExtra(EXTRA_LAGE, wunsch.name)
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
            }

            v.setTextViewText(R.id.w_fuss, fusszeile(stand))
            return v
        }

        /**
         * Die Farben: hell/dunkel nach den Einstellungen der App, und Material
         * You, wenn es dort an ist.
         *
         * ERST AB ANDROID 12. Vorher kann ein Widget eine Farbe nicht fuer
         * Tag und Nacht getrennt bekommen; dort bleiben die Farben aus dem
         * Layout, und die folgen dem System.
         */
        private fun faerbe(context: Context, v: RemoteViews) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
            fun text(id: Int, farbe: Int) {
                val (h, d) = Thema.widgetPaar(context, farbe)
                v.setColorInt(id, "setTextColor", h, d)
            }
            fun liste(id: Int, methode: String, farbe: Int) {
                val (h, d) = Thema.widgetPaar(context, farbe)
                v.setColorStateList(id, methode,
                    android.content.res.ColorStateList.valueOf(h),
                    android.content.res.ColorStateList.valueOf(d))
            }
            liste(R.id.w_wurzel, "setBackgroundTintList", R.color.karte)
            text(R.id.w_lage, R.color.akzent)
            listOf(R.id.w_titel, R.id.w_stand, R.id.w_name, R.id.w_einheit, R.id.w_fuss,
                   R.id.w_k1_name, R.id.w_k2_name, R.id.w_k3_name).forEach { text(it, R.color.schrift_zart) }
            listOf(R.id.w_gross, R.id.w_satz, R.id.w_k1_wert, R.id.w_k2_wert, R.id.w_k3_wert)
                .forEach { text(it, R.color.schrift) }
            listOf(R.id.w_balken, R.id.w_k1_balken, R.id.w_k2_balken, R.id.w_k3_balken).forEach {
                liste(it, "setProgressTintList", R.color.akzent)
                liste(it, "setProgressBackgroundTintList", R.color.linie)
            }
            liste(R.id.w_auffrischen, "setImageTintList", R.color.schrift_zart)
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
