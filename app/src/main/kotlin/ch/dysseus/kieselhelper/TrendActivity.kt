package ch.dysseus.kieselhelper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Der Trend - und zwar zu der Zahl, auf die man eben getippt hat.
 *
 * ER WAR EIN REITER UND IST JETZT EIN SCHIRM DAHINTER. Als Reiter stand er
 * gleichberechtigt neben den Tageswerten, obwohl er eine ANTWORT ist und
 * keine eigene Frage: man schaut auf 7985 Schritte und will wissen, ob das
 * viel ist. Wer das als Reiter baut, verlangt zwei Bewegungen - unten
 * umschalten, oben die Kategorie suchen - und zwischen ihnen vergisst man,
 * was man wissen wollte.
 *
 * EINE SEITE JE KARTE. Eine Leiste mit allen Gruppen oben machte aus der
 * Antwort wieder einen Katalog: wer auf den Schlaf tippt, will den Schlaf
 * sehen und was mit ihm zusammenhaengt, nicht einen Weg zum Herz. Wer das
 * Herz will, geht zurueck und tippt auf das Herz.
 */
class TrendActivity : KieselActivity() {

    private lateinit var wurzel: LinearLayout
    private lateinit var inhalt: LinearLayout
    private var wahl = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wahl = intent.getIntExtra(EXTRA_GRUPPE, 0).coerceIn(0, TrendTab.GRUPPEN.size - 1)
        toenen()
        setContentView(baueAnsicht())
        lifecycleScope.launch { lade() }
    }

    /**
     * Den Ton der Karte behalten, aus der man kam.
     *
     * WER AUF EINE GRUENE KARTE TIPPT, soll nicht auf einem blauen Schirm
     * landen - der Weg dorthin waere sonst nicht mehr zu sehen.
     */
    private fun toenen() {
        Ton.setze(if (wahl == ERNAEHRUNG) Ton.ERNAEHRUNG else Ton.GESUNDHEIT)
    }

    /** Kopf fest, Inhalt beweglich - wie auf dem Hauptschirm. */
    private fun baueAnsicht(): View {
        val aussen = spalte()
        aussen.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )

        val oben = spalte().apply { setPadding(dp(16f), dp(16f), dp(16f), 0) }
        oben.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        oben.luft(10f)
        oben.addView(kopf(getString(R.string.trend_titel, TrendTab.GRUPPEN[wahl].name(this))))
        oben.luft(6f)
        aussen.addView(oben)

        wurzel = spalte().apply { setPadding(dp(16f), 0, dp(16f), dp(24f)) }
        inhalt = spalte()
        wurzel.addView(inhalt)

        val roller = ScrollView(this).apply {
            addView(wurzel)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        aussen.addView(roller)
        aussen.randUmSystemleisten()
        return aussen
    }

    /**
     * Die Tabelle wird im Hintergrund gelesen.
     *
     * Ein Jahr sind dreihundert Zeilen - das ist schnell, aber SQLite auf dem
     * Hauptfaden ist es nie, und der Fehler faellt erst auf, wenn die Tabelle
     * gross genug ist.
     */
    private suspend fun lade() {
        val gruppe = TrendTab.GRUPPEN[wahl]
        val (daten, umfang) = withContext(Dispatchers.IO) {
            val speicher = Speicher(this@TrendActivity)
            TrendTab.spaltenFuer(gruppe).associateWith { speicher.reihe(it) } to
                speicher.umfang()
        }

        // Die Wolke NUR fuer Herz. Sie liest vierzehn Tage Einzelmessungen aus
        // der Akte - das ist die teuerste Abfrage der App, und fuer die
        // Schritte-Gruppe braucht sie niemand.
        val wolke = if (gruppe.schluessel == "herz") {
            Gesundheit(this@TrendActivity).pulswolke()
        } else {
            emptyList()
        }

        // DER TON NOCH EINMAL, UNMITTELBAR VOR DEM BAUEN. Waehrend hier
        // gelesen wurde, kann der Hauptschirm dahinter fertig geladen und den
        // Ton auf seinen Reiter zurueckgestellt haben - er ist eine Stelle
        // fuer die ganze App.
        toenen()
        inhalt.removeAllViews()
        inhalt.addView(TrendTab.inhalt(this@TrendActivity, wahl, daten, umfang, wolke))
    }

    companion object {
        const val EXTRA_GRUPPE = "gruppe"

        /** Die Nummern der Gruppen in [TrendTab.GRUPPEN] - hier, damit sie einmal stehen. */
        const val BEWEGUNG = 0
        const val SCHLAF = 1
        const val HERZ = 2
        const val ERNAEHRUNG = 3

        fun zeige(ctx: Context, gruppe: Int) {
            ctx.startActivity(
                Intent(ctx, TrendActivity::class.java).putExtra(EXTRA_GRUPPE, gruppe)
            )
        }
    }
}
