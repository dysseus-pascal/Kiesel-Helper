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
 * DIE LEISTE BLEIBT TROTZDEM. Wer einmal hier ist, vergleicht weiter; der
 * Weg von Schlaf zu Herz soll nicht ueber den Zurueck-Knopf fuehren. Sie
 * beginnt nur nicht mehr immer links, sondern bei dem, was man angetippt hat.
 */
class TrendActivity : ComponentActivity() {

    private lateinit var wurzel: LinearLayout
    private lateinit var leiste: TrendTab.Leiste
    private lateinit var inhalt: LinearLayout
    private var wahl = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wahl = intent.getIntExtra(EXTRA_GRUPPE, 0).coerceIn(0, TrendTab.GRUPPEN.size - 1)
        setContentView(baueAnsicht())
        lifecycleScope.launch { lade() }
    }

    /**
     * Kopf fest, Inhalt beweglich - wie auf dem Hauptschirm.
     *
     * Die Auswahlleiste steht MIT im festen Teil. Sie ist die Antwort auf
     * "und was war beim Schlaf?", und die stellt sich mitten im Lesen, nicht
     * oben am Anfang.
     */
    private fun baueAnsicht(): View {
        val aussen = spalte()
        aussen.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )

        val oben = spalte().apply { setPadding(dp(16f), dp(16f), dp(16f), 0) }
        oben.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        oben.luft(10f)
        oben.addView(kopf("Trend"))
        oben.luft(6f)

        leiste = TrendTab.leiste(this) { gewaehlt ->
            wahl = gewaehlt
            leiste.male(gewaehlt)
            lifecycleScope.launch { lade() }
        }
        leiste.male(wahl)
        oben.addView(leiste.sicht)
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
            gruppe.spalten.associateWith { speicher.reihe(it) } to speicher.umfang()
        }

        // Die Wolke NUR fuer Herz. Sie liest vierzehn Tage Einzelmessungen aus
        // der Akte - das ist die teuerste Abfrage der App, und fuer die
        // Schritte-Gruppe braucht sie niemand.
        val wolke = if (gruppe.name == "Herz") {
            Gesundheit(this@TrendActivity).pulswolke()
        } else {
            emptyList()
        }

        inhalt.removeAllViews()
        inhalt.addView(TrendTab.inhalt(this@TrendActivity, wahl, daten, umfang, wolke))
    }

    companion object {
        const val EXTRA_GRUPPE = "gruppe"

        /** Die Nummern der Gruppen in [TrendTab.GRUPPEN] - hier, damit sie einmal stehen. */
        const val SCHRITTE = 0
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
