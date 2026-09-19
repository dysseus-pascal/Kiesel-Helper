package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Der Trend-Schirm: der typische Mittwoch, und wohin es geht.
 *
 * DIE FRAGE, DIE EIN TAGESWERT NICHT BEANTWORTET. 7985 Schritte sind viel oder
 * wenig - das haengt davon ab, was ein Mittwoch bei einem sonst ist. Diese
 * Seite rechnet das aus den eigenen Aufzeichnungen aus, nicht aus der Akte:
 * Health Connect vergisst, [Speicher] nicht.
 *
 * ZWEI BILDER, MEHR NICHT. Das Wochenprofil beantwortet "welcher Tag ist mein
 * schwacher", der Verlauf "wird es besser". Alles Weitere waere Statistik um
 * ihrer selbst willen.
 *
 * Die gestrichelte Linie ist in beiden Bildern DASSELBE: der Mittelwert ueber
 * alle Tage. So heisst "ueber der Linie" ueberall dasselbe.
 */
object TrendTab {

    /** Eine auswertbare Groesse: Spalte im Speicher, Ziel, Darstellung. */
    data class Groesse(
        val name: String,
        val spalte: String,
        val einheit: String,
        val form: (Double) -> String,
    )

    val GROESSEN = listOf(
        Groesse("Schritte", "schritte", "") { Zahlen.ganz(it) ?: "" },
        Groesse("Schlaf", "schlaf", "") { Zahlen.dauer(it) ?: "" },
        Groesse("Tiefschlaf", "tief", "") { Zahlen.dauer(it) ?: "" },
        Groesse("Wasser", "wasser", "ml") { Zahlen.ganz(it) ?: "" },
        Groesse("Aktiv", "aktiv", "min") { Zahlen.ganz(it) ?: "" },
        Groesse("Ruhepuls", "ruhepuls", "bpm") { Zahlen.ganz(it) ?: "" },
        Groesse("HRV", "hrv", "ms") { Zahlen.ganz(it) ?: "" },
    )

    fun baue(
        ctx: Context,
        gewaehlt: Int,
        reihe: List<Pair<LocalDate, Double>>,
        umfang: Pair<Int, LocalDate?>,
        waehle: (Int) -> Unit,
    ): LinearLayout {
        val s = ctx.spalte()
        val groesse = GROESSEN[gewaehlt]
        s.addView(auswahl(ctx, gewaehlt, waehle))

        val (tage, seit) = umfang
        if (reihe.size < 2) {
            val k = ctx.karte()
            k.addView(ctx.kartentitel("Noch zu wenig ${groesse.name}"))
            k.addView(ctx.zart(
                "Die App schreibt jeden gelesenen Tag in ihre eigene Tabelle und " +
                    "hat beim ersten Start geholt, was Health Connect noch hatte. " +
                    "Findet sich dort nichts für diese Grösse, füllt sie sich ab " +
                    "jetzt — ein Tag je Tag."
            ))
            if (tage > 0) k.addView(ctx.zart("Gespeichert: $tage Tage, seit $seit."))
            s.addView(k)
            return s
        }

        val bild = Auswertung.bild(reihe, LocalDate.now())
        val mittel = bild.gesamt
        val heute = LocalDate.now().dayOfWeek

        // --- Die typische Woche ---
        s.addView(ctx.abschnitt("TYPISCHE WOCHE"))
        val woche = ctx.karte()
        woche.addView(ctx.saeulenbild(
            bild.profil.map { p ->
                Saeule(
                    p.tag.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    p.mittel,
                    hervor = p.tag == heute,
                    oben = p.mittel?.let { if (p.tag == heute) groesse.form(it) else null },
                )
            },
            ziel = mittel,
        ))
        woche.addView(ctx.zart(
            if (mittel != null)
                "Gestrichelt: der Schnitt über alle Tage, " +
                    groesse.form(mittel) + einheit(groesse) + "."
            else "Noch kein Schnitt."
        ))
        val stark = bild.staerkster
        val schwach = bild.schwaechster
        if (stark != null && schwach != null && stark.tag != schwach.tag) {
            woche.addView(ctx.fliesstext(
                "Am meisten am " + lang(stark.tag) + " (" + groesse.form(stark.mittel!!) +
                    einheit(groesse) + "), am wenigsten am " + lang(schwach.tag) +
                    " (" + groesse.form(schwach.mittel!!) + einheit(groesse) + ")."
            ))
        }
        val duenn = bild.profil.filter { it.mittel == null && it.anzahl > 0 }
        if (duenn.isNotEmpty() || bild.profil.any { it.anzahl < Auswertung.MINDESTENS }) {
            woche.addView(ctx.zart(
                "Ein Wochentag bleibt leer, solange er weniger als " +
                    "${Auswertung.MINDESTENS} Mal aufgezeichnet ist. Aus einem " +
                    "einzigen Mittwoch ein Muster zu lesen wäre keine Auswertung, " +
                    "sondern eine Erinnerung."
            ))
        }
        s.addView(woche)

        // --- Der Verlauf ---
        s.addView(ctx.abschnitt("VERLAUF"))
        val verlauf = ctx.karte()
        val kw = WeekFields.ISO.weekOfWeekBasedYear()
        verlauf.addView(ctx.saeulenbild(
            bild.wochen.map { w ->
                Saeule(w.montag.get(kw).toString(), w.mittel,
                       hervor = w.montag == LocalDate.now().with(java.time.DayOfWeek.MONDAY))
            },
            ziel = mittel,
        ))
        verlauf.addView(ctx.zart("Kalenderwochen, je der Schnitt eines Tages"))
        verlauf.addView(ctx.fliesstext(
            bild.veraenderung?.let { v ->
                val richtung = if (v >= 0) "+" else ""
                "Die letzten vier Wochen liegen $richtung${Zahlen.ganz(v)} % über " +
                    "den vier davor."
            } ?: "Für einen Vergleich über acht Wochen fehlen noch Tage."
        ))
        s.addView(verlauf)

        // --- Was dahintersteht ---
        s.addView(ctx.zart(
            "$tage Tage im Speicher, seit $seit. Gerechnet wird über " +
                "${bild.anzahl} Tage mit ${groesse.name}. Heute zählt nicht mit " +
                "— ein angefangener Tag hat immer zu wenig, und der heutige " +
                "Wochentag wäre sonst für immer der schwächste."
        ))
        return s
    }

    private fun einheit(g: Groesse) = if (g.einheit.isEmpty()) "" else " " + g.einheit

    private fun lang(tag: java.time.DayOfWeek) =
        tag.getDisplayName(TextStyle.FULL, Locale.getDefault())

    /**
     * Die Auswahl der Groesse - eine Reihe zum Schieben.
     *
     * Keine gleich breiten Reiter wie oben: sieben Namen nebeneinander waeren
     * je vierzig Punkte breit. Was nicht hinpasst, schiebt man heran.
     */
    private fun auswahl(ctx: Context, gewaehlt: Int, waehle: (Int) -> Unit): HorizontalScrollView {
        val reihe = ctx.reihe()
        // WRAP_CONTENT, nicht MATCH_PARENT: in einem Schieber bedeutet
        // "so breit wie der Platz", dass nichts hinausragt - und damit
        // schiebt sich auch nichts.
        reihe.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        GROESSEN.forEachIndexed { i, g ->
            reihe.addView(TextView(ctx).apply {
                text = g.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(ctx.dp(14f), ctx.dp(8f), ctx.dp(14f), ctx.dp(8f))
                setTextColor(ctx.farbe(
                    if (i == gewaehlt) R.color.akzent_schrift else R.color.schrift_zart
                ))
                background = GradientDrawable().apply {
                    setColor(ctx.farbe(if (i == gewaehlt) R.color.akzent else R.color.karte))
                    cornerRadius = ctx.dp(16f).toFloat()
                    if (i != gewaehlt) setStroke(ctx.dp(1f), ctx.farbe(R.color.linie))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = ctx.dp(8f) }
                setOnClickListener { waehle(i) }
            })
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(reihe)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ctx.dp(4f) }
        }
    }
}
