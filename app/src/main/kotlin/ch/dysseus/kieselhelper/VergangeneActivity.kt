package ch.dysseus.kieselhelper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Die vergangenen Trainings - und eines davon im Einzelnen.
 *
 * EINE EIGENE SEITE STATT EINER LANGEN LISTE IM REITER. Zwanzig Karten unter
 * den Bildern nahmen dort mehr Platz als alles andere, und gesucht wird in
 * ihnen selten. Hier stehen sie als Zeilen, nach Monaten, und ein Tippen
 * zeigt das Training so gross wie das juengste im Reiter: mit Puls, Saetzen
 * oder Bahnen und der Karte.
 *
 * DERSELBE SCHIRM FUER BEIDES. Ohne [EXTRA_BEGINN] die Liste, mit ihm das
 * eine Training - so fuehrt der Zurueck-Knopf von der Einzelansicht wieder
 * in die Liste, wie man es erwartet.
 */
class VergangeneActivity : ComponentActivity() {

    private lateinit var inhalt: LinearLayout

    /** Die Kartenansichten dieses Schirms - sie wollen seinen Lebenslauf. */
    private val karten = mutableListOf<MapView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ton.setze(Ton.TRAINING)
        setContentView(baueAnsicht())
        lifecycleScope.launch { lade() }
    }

    override fun onResume() {
        super.onResume()
        karten.forEach { it.onResume() }
    }

    override fun onPause() {
        karten.forEach { it.onPause() }
        super.onPause()
    }

    override fun onDestroy() {
        karten.forEach { it.onDetach() }
        karten.clear()
        super.onDestroy()
    }

    private val beginn: Long get() = intent.getLongExtra(EXTRA_BEGINN, -1L)

    private fun baueAnsicht(): ScrollView {
        val wurzel = spalte().apply { setPadding(dp(16f), dp(16f), dp(16f), dp(28f)) }
        wurzel.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        wurzel.luft(10f)
        wurzel.addView(kopf(if (beginn < 0) "Vergangene Trainings" else "Training"))
        wurzel.luft(6f)
        inhalt = spalte()
        wurzel.addView(inhalt)
        return ScrollView(this).apply {
            isFillViewport = true
            addView(wurzel)
            randUmSystemleisten()
        }
    }

    private suspend fun lade() {
        // Was der Reiter eben gebaut hat. Leer nur, wenn der Prozess
        // dazwischen neu begann - dann selbst holen.
        val alle = TrainingTab.zuletzt.ifEmpty { TrainingTab.hole(this) }
        Ton.setze(Ton.TRAINING)
        inhalt.removeAllViews()

        if (beginn < 0) {
            zeigeListe(alle.drop(1))
            return
        }
        val eintrag = alle.firstOrNull { it.sitzung.startTime.epochSecond == beginn }
        if (eintrag == null) {
            inhalt.addView(karte().apply {
                addView(zart("Dieses Training steht nicht mehr in der Gesundheitsakte."))
            })
            return
        }
        val voll = TrainingTab.vervollstaendige(this, eintrag)
        Ton.setze(Ton.TRAINING)
        inhalt.addView(TrainingTab.sitzungskarte(this, voll, gross = true, karten = karten))
        karten.forEach { it.onResume() }
    }

    /**
     * Nach Monaten, eine Karte je Monat.
     *
     * Hundert einzelne Karten waeren hundert Rahmen; was man hier sucht, ist
     * eine Folge. Der Monat ist die Einheit, in der man sich erinnert -
     * "der lange Lauf im August".
     */
    private fun zeigeListe(aeltere: List<TrainingTab.Eintrag>) {
        if (aeltere.isEmpty()) {
            inhalt.addView(karte().apply {
                addView(zart("Noch keine älteren Trainings in den letzten drei Monaten."))
            })
            return
        }
        val zone = ZoneId.systemDefault()
        val monat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
        aeltere.groupBy { YearMonth.from(it.sitzung.startTime.atZone(zone)) }
            .forEach { (ym, liste) ->
                val minuten = liste.sumOf { Sportart.minuten(it.sitzung) }
                inhalt.addView(abschnitt(
                    monat.format(ym).uppercase(Locale.GERMAN) + "  ·  " + liste.size + "×  ·  " +
                        (Zahlen.dauer(minuten.toDouble()) ?: "")
                ))
                val k = karte().apply { setPadding(dp(14f), dp(6f), dp(14f), dp(6f)) }
                liste.forEachIndexed { i, e ->
                    if (i > 0) k.addView(strich())
                    k.addView(TrainingTab.zeile(this, e) {
                        zeige(this, e.sitzung.startTime.epochSecond)
                    })
                }
                inhalt.addView(k)
            }
        inhalt.addView(zart(
            "Die letzten drei Monate. Ältere Trainings stehen weiter in der " +
                "Gesundheitsakte, hier aber nicht."
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f) }
        })
    }

    companion object {
        private const val EXTRA_BEGINN = "beginn"

        /** Die Liste - oder, mit [beginn], das eine Training. */
        fun zeige(ctx: Context, beginn: Long = -1L) {
            ctx.startActivity(
                Intent(ctx, VergangeneActivity::class.java).putExtra(EXTRA_BEGINN, beginn)
            )
        }
    }
}
