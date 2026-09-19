package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Was der Trend-Schirm braucht: je Spalte die Tage aus dem Speicher. */
typealias Trenddaten = Map<String, List<Pair<LocalDate, Double>>>

/**
 * Der Trend-Schirm: der typische Mittwoch, und wohin es geht.
 *
 * DIE FRAGE, DIE EIN TAGESWERT NICHT BEANTWORTET. 7985 Schritte sind viel oder
 * wenig - das haengt davon ab, was ein Mittwoch bei einem sonst ist. Diese
 * Seite rechnet das aus den eigenen Aufzeichnungen aus, nicht aus der Akte:
 * Health Connect vergisst, [Speicher] nicht.
 *
 * GRUPPEN STATT EINZELWERTE. Schlaf ohne Tiefschlaf daneben sagt wenig, und
 * ein Ruhepuls ohne die Spanne des Tages noch weniger. Was zusammen gelesen
 * wird, steht zusammen in einem Bild - nicht hintereinander auf zwei.
 *
 * Die gestrichelte Linie ist ueberall DASSELBE: der Mittelwert ueber alle
 * Tage. So heisst "ueber der Linie" auf jedem Bild dasselbe.
 */
object TrendTab {

    /** Eine Gruppe zusammengehoerender Groessen, mit ihren Spalten im Speicher. */
    data class Gruppe(val name: String, val spalten: List<String>)

    val GRUPPEN = listOf(
        Gruppe("Schritte", listOf("schritte")),
        Gruppe("Schlaf", listOf("schlaf", "tief")),
        Gruppe("Herz", listOf("ruhepuls", "puls_min", "puls_hoch", "puls_tief", "hrv")),
        Gruppe("Ernährung", listOf("wasser", "supp_faellig", "supp_genommen")),
        Gruppe("Aktiv", listOf("aktiv")),
        Gruppe("Zusammenhänge", listOf(
            "schlaf", "tief", "ruhepuls", "puls_min", "hrv", "schritte", "aktiv"
        )),
    )

    // --- Die Auswahlleiste ---------------------------------------------------

    /**
     * Die Leiste ueberlebt den Wechsel.
     *
     * SIE WIRD NICHT NEU GEBAUT, nur neu eingefaerbt. Vorher entstand bei jedem
     * Umschalten eine neue Leiste - und die stand wieder ganz links, waehrend
     * man gerade rechts aussen auf "Aktiv" getippt hatte. Wer schiebt, will
     * dort bleiben, wo er geschoben hat.
     */
    class Leiste(val sicht: HorizontalScrollView, private val felder: List<TextView>) {
        fun male(gewaehlt: Int) {
            val ctx = sicht.context
            felder.forEachIndexed { i, feld ->
                feld.setTextColor(ctx.farbe(
                    if (i == gewaehlt) R.color.akzent_schrift else R.color.schrift_zart
                ))
                feld.background = GradientDrawable().apply {
                    setColor(ctx.farbe(if (i == gewaehlt) R.color.akzent else R.color.karte))
                    cornerRadius = ctx.dp(16f).toFloat()
                    if (i != gewaehlt) setStroke(ctx.dp(1f), ctx.farbe(R.color.linie))
                }
            }
        }
    }

    fun leiste(ctx: Context, waehle: (Int) -> Unit): Leiste {
        val reihe = ctx.reihe()
        // WRAP_CONTENT, nicht MATCH_PARENT: in einem Schieber hiesse "so breit
        // wie der Platz", dass nichts hinausragt - und damit schoebe sich auch
        // nichts.
        reihe.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val felder = GRUPPEN.mapIndexed { i, gruppe ->
            TextView(ctx).apply {
                text = gruppe.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(ctx.dp(14f), ctx.dp(8f), ctx.dp(14f), ctx.dp(8f))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = ctx.dp(8f) }
                setOnClickListener { waehle(i) }
                reihe.addView(this)
            }
        }
        val schieber = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(reihe)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ctx.dp(4f) }
        }
        return Leiste(schieber, felder).also { it.male(0) }
    }

    // --- Der Inhalt ----------------------------------------------------------

    fun inhalt(
        ctx: Context,
        gewaehlt: Int,
        daten: Trenddaten,
        umfang: Pair<Int, LocalDate?>,
        wolke: List<Gesundheit.Punkt> = emptyList(),
    ): LinearLayout {
        val s = ctx.spalte()
        val gruppe = GRUPPEN[gewaehlt]
        val heute = Einstellungen.heute(ctx)
        val (tage, seit) = umfang

        val genug = gruppe.spalten.any { (daten[it]?.size ?: 0) >= 2 }
        if (!genug) {
            val k = ctx.karte()
            k.addView(ctx.kartentitel("Noch zu wenig ${gruppe.name}"))
            k.addView(ctx.zart(
                "Die App schreibt jeden gelesenen Tag in ihre eigene Tabelle und " +
                    "hat beim ersten Start geholt, was Health Connect noch hatte. " +
                    "Findet sich dort nichts dazu, füllt sie sich ab jetzt — ein " +
                    "Tag je Tag."
            ))
            if (tage > 0) k.addView(ctx.zart("Gespeichert: $tage Tage, seit $seit."))
            s.addView(k)
            return s
        }

        when (gruppe.name) {
            "Schlaf" -> schlaf(ctx, s, daten, heute)
            "Herz" -> herz(ctx, s, daten, heute, wolke)
            "Ernährung" -> ernaehrung(ctx, s, daten, heute)
            "Zusammenhänge" -> zusammenhaenge(ctx, s, daten, heute)
            else -> einfach(ctx, s, gruppe, daten, heute)
        }

        s.addView(ctx.zart(
            "$tage Tage im Speicher, seit $seit. Heute zählt nicht mit — ein " +
                "angefangener Tag hat immer zu wenig, und der heutige Wochentag " +
                "wäre sonst für immer der schwächste. Ein Wochentag bleibt leer, " +
                "bis er ${Auswertung.MINDESTENS} Mal aufgezeichnet ist."
        ))
        return s
    }

    // --- Die drei Formen -----------------------------------------------------

    /** Eine einzelne Groesse: Wochenprofil und Verlauf. */
    private fun einfach(
        ctx: Context,
        s: LinearLayout,
        gruppe: Gruppe,
        daten: Trenddaten,
        heute: LocalDate,
    ) {
        val bild = Auswertung.bild(daten[gruppe.spalten.first()].orEmpty(), heute)
        val form: (Double) -> String = { Zahlen.ganz(it) ?: "" }
        val einheit = if (gruppe.name == "Aktiv") " min" else ""

        s.addView(ctx.abschnitt("TYPISCHE WOCHE"))
        val woche = ctx.karte()
        woche.addView(ctx.saeulenbild(
            bild.profil.map { p ->
                Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek,
                       kleinster = p.kleinster, groesster = p.groesster)
            },
            ziel = bild.gesamt,
        ))
        woche.addView(ctx.zart(
            schnittzeile(bild, form, einheit) +
                " Der Fühler zeigt die Spanne, aus der gemittelt wurde."
        ))
        extreme(ctx, woche, bild, form, einheit)
        wochenendzeile(ctx, woche, daten, gruppe.spalten.first(), gruppe.name, form, heute)
        s.addView(woche)

        s.addView(ctx.abschnitt("VERLAUF"))
        val verlauf = ctx.karte()
        verlauf.addView(ctx.saeulenbild(wochensaeulen(ctx, bild), ziel = bild.gesamt))
        verlauf.addView(ctx.zart("Kalenderwochen, je der Schnitt eines Tages"))
        verlauf.addView(ctx.fliesstext(richtung(bild)))
        s.addView(verlauf)
    }

    /**
     * Schlaf und Tiefschlaf in EINEM Balken.
     *
     * Der Tiefschlaf steckt im Schlaf; zwei Balken nebeneinander behaupteten
     * zwei Dinge. Dunkel im Hellen ist die Form, die das Verhaeltnis zeigt.
     */
    private fun schlaf(ctx: Context, s: LinearLayout, daten: Trenddaten, heute: LocalDate) {
        val gesamt = Auswertung.bild(daten["schlaf"].orEmpty(), heute)
        val tief = Auswertung.bild(daten["tief"].orEmpty(), heute)
        val tiefNachTag = tief.profil.associate { it.tag to it.mittel }
        val form: (Double) -> String = { Zahlen.dauer(it) ?: "" }
        val ideal = Einstellungen.schlafziel(ctx).toDouble()

        s.addView(ctx.abschnitt("TYPISCHE WOCHE"))
        val woche = ctx.karte()
        woche.addView(ctx.saeulenbild(
            gesamt.profil.map { p ->
                Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek,
                       innen = tiefNachTag[p.tag])
            },
            ziel = gesamt.gesamt,
            marke = ideal,
        ))
        woche.addView(ctx.zart(
            "Heller Balken: Schlaf gesamt, dunkel der Tiefschlaf. Gestrichelt " +
                "dein Schnitt, farbig dein Ideal."
        ))
        erreicht(ctx, woche, daten["schlaf"].orEmpty(), ideal, heute)
        woche.addView(ctx.fliesstext(
            buildString {
                gesamt.gesamt?.let { append("Im Schnitt " + form(it)) }
                tief.gesamt?.let { append(", davon " + form(it) + " tief") }
                if (isNotEmpty()) append(".")
            }
        ))
        extreme(ctx, woche, gesamt, form, "")
        wochenendzeile(ctx, woche, daten, "schlaf", "Schlaf", form, heute)
        wochenendzeile(ctx, woche, daten, "tief", "Tiefschlaf", form, heute)
        s.addView(woche)

        s.addView(ctx.abschnitt("VERLAUF"))
        val verlauf = ctx.karte()
        val tiefWochen = tief.wochen.associate { it.montag to it.mittel }
        verlauf.addView(ctx.saeulenbild(
            gesamt.wochen.map { w ->
                Saeule(kw(w.montag), w.mittel,
                       hervor = w.montag == heute.with(DayOfWeek.MONDAY),
                       innen = tiefWochen[w.montag])
            },
            ziel = gesamt.gesamt,
            marke = ideal,
        ))
        verlauf.addView(ctx.zart("Kalenderwochen, je der Schnitt einer Nacht"))
        verlauf.addView(ctx.fliesstext(richtung(gesamt)))
        s.addView(verlauf)
    }

    /**
     * Herz: die Spanne des Tages, der Ruhepuls darin, die HRV daneben.
     *
     * DREI DINGE, ZWEI BILDER. Puls tief, hoch und Ruhepuls teilen sich eine
     * Achse in Schlaegen je Minute und gehoeren in ein Bild. Die HRV wird in
     * Millisekunden gemessen; sie in dieselbe Achse zu zwingen hiesse, zwei
     * Einheiten uebereinanderzulegen und zu hoffen, dass es niemand liest.
     */
    private fun herz(
        ctx: Context,
        s: LinearLayout,
        daten: Trenddaten,
        heute: LocalDate,
        wolke: List<Gesundheit.Punkt>,
    ) {
        val ruhe = Auswertung.bild(
            verschmelze(daten["ruhepuls"].orEmpty(), daten["puls_min"].orEmpty()), heute
        )
        val hoch = Auswertung.bild(daten["puls_hoch"].orEmpty(), heute)
        val tief = Auswertung.bild(daten["puls_tief"].orEmpty(), heute)
        val hrv = Auswertung.bild(daten["hrv"].orEmpty(), heute)

        val hochTag = hoch.profil.associate { it.tag to it.mittel }
        val tiefTag = tief.profil.associate { it.tag to it.mittel }
        val ruheTag = ruhe.profil.associate { it.tag to it.mittel }

        s.addView(ctx.abschnitt("TYPISCHE WOCHE"))
        val woche = ctx.karte()
        woche.addView(ctx.spannenbild(
            Auswertung.WOCHENTAGE.map { tag ->
                Spanne(kurz(tag), tiefTag[tag], hochTag[tag], ruheTag[tag],
                       hervor = tag == heute.dayOfWeek)
            }
        ))
        woche.addView(ctx.zart("Vom Tagestief zum Tageshoch; der helle Strich ist der Ruhepuls."))
        woche.addView(ctx.fliesstext(
            buildString {
                ruhe.gesamt?.let { append("Ruhepuls im Schnitt " + (Zahlen.ganz(it) ?: "") + " bpm") }
                val t = tief.gesamt; val h = hoch.gesamt
                if (t != null && h != null) {
                    if (isNotEmpty()) append(", ")
                    append("der Tag typischerweise zwischen " + (Zahlen.ganz(t) ?: "") +
                           " und " + (Zahlen.ganz(h) ?: ""))
                }
                if (isNotEmpty()) append(".")
            }
        ))
        s.addView(woche)

        if (wolke.isNotEmpty()) {
            s.addView(ctx.abschnitt("DER TYPISCHE TAG"))
            val wolkenkarte = ctx.karte()
            wolkenkarte.addView(ctx.pulsbild(emptyList(), ruhe.gesamt, wolke))
            wolkenkarte.addView(ctx.zart(
                "Alle Pulsmessungen der letzten 14 Tage, nach Tageszeit " +
                    "übereinandergelegt. Die Linie ist der gleitende Median — " +
                    "so verläuft ein Tag bei dir normalerweise."
            ))
            s.addView(wolkenkarte)
        }

        s.addView(ctx.abschnitt("HRV"))
        val hrvKarte = ctx.karte()
        hrvKarte.addView(ctx.saeulenbild(
            hrv.profil.map { p ->
                Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek)
            },
            ziel = hrv.gesamt,
        ))
        hrvKarte.addView(ctx.zart(schnittzeile(hrv, { Zahlen.ganz(it) ?: "" }, " ms")))
        s.addView(hrvKarte)

        s.addView(ctx.abschnitt("VERLAUF"))
        val verlauf = ctx.karte()
        val hochW = hoch.wochen.associate { it.montag to it.mittel }
        val tiefW = tief.wochen.associate { it.montag to it.mittel }
        val ruheW = ruhe.wochen.associate { it.montag to it.mittel }
        val montage = (hochW.keys + tiefW.keys + ruheW.keys).sorted().takeLast(Auswertung.WOCHEN)
        verlauf.addView(ctx.spannenbild(
            montage.map { m ->
                Spanne(kw(m), tiefW[m], hochW[m], ruheW[m],
                       hervor = m == heute.with(DayOfWeek.MONDAY))
            }
        ))
        verlauf.addView(ctx.zart("Kalenderwochen"))
        verlauf.addView(ctx.fliesstext(richtung(ruhe, "Ruhepuls, letzte vier Wochen")))
        s.addView(verlauf)
    }

    /**
     * Ernaehrung: Wasser und Supplemente auf einem Schirm.
     *
     * Sie haben nichts miteinander zu tun und gehoeren trotzdem zusammen -
     * beides ist, was man dem Koerper ZUFUEHRT, und beides haengt an derselben
     * Frage: hat man heute daran gedacht.
     */
    private fun ernaehrung(
        ctx: Context,
        s: LinearLayout,
        daten: Trenddaten,
        heute: LocalDate,
    ) {
        val wasser = Auswertung.bild(daten["wasser"].orEmpty(), heute)
        val form: (Double) -> String = { Zahlen.ganz(it) ?: "" }

        s.addView(ctx.abschnitt("WASSER, TYPISCHE WOCHE"))
        val karte = ctx.karte()
        karte.addView(ctx.saeulenbild(
            wasser.profil.map { p ->
                Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek)
            },
            ziel = wasser.gesamt,
        ))
        karte.addView(ctx.zart(schnittzeile(wasser, form, " ml")))
        extreme(ctx, karte, wasser, form, " ml")
        wochenendzeile(ctx, karte, daten, "wasser", "Wasser", form, heute)
        s.addView(karte)

        s.addView(ctx.abschnitt("WASSER, VERLAUF"))
        val verlauf = ctx.karte()
        verlauf.addView(ctx.saeulenbild(wochensaeulen(ctx, wasser), ziel = wasser.gesamt))
        verlauf.addView(ctx.zart("Kalenderwochen, je der Schnitt eines Tages"))
        verlauf.addView(ctx.fliesstext(richtung(wasser)))
        s.addView(verlauf)

        val faellig = Auswertung.bild(daten["supp_faellig"].orEmpty(), heute)
        val genommen = Auswertung.bild(daten["supp_genommen"].orEmpty(), heute)
        s.addView(ctx.abschnitt("SUPPLEMENTE"))
        val supp = ctx.karte()
        if (faellig.anzahl == 0) {
            supp.addView(ctx.zart(
                "Von SupCycle kam noch nichts. Die Uhr meldet ihren Stand, " +
                    "sobald dort etwas abgehakt wird — rückwirkend gibt es " +
                    "nichts zu holen."
            ))
        } else {
            val genommenTag = genommen.profil.associate { it.tag to it.mittel }
            supp.addView(ctx.saeulenbild(
                faellig.profil.map { p ->
                    Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek,
                           innen = genommenTag[p.tag])
                }
            ))
            supp.addView(ctx.zart("Hell geplant, dunkel genommen"))
            val g = genommen.gesamt
            val f = faellig.gesamt
            supp.addView(ctx.fliesstext(
                if (g != null && f != null && f > 0)
                    "Von " + zehntel(f) + " geplanten Einnahmen am Tag kommen im " +
                        "Schnitt " + zehntel(g) + " an — " +
                        (Zahlen.ganz(g / f * 100) ?: "") + " %."
                else "Noch kein Schnitt."
            ))
        }
        s.addView(supp)
    }

    /** Eine Nachkommastelle, aber ohne die ",0" bei glatten Zahlen. */
    private fun zehntel(d: Double): String =
        if (d == Math.floor(d)) (Zahlen.ganz(d) ?: "") else (Zahlen.eine(d) ?: "")


    /**
     * Zusammenhaenge: zwei Groessen gegeneinander.
     *
     * FUENF PAARE, NICHT ALLE. Aus sieben Spalten liessen sich einundzwanzig
     * Paare bilden, und mindestens die Haelfte davon ist Unsinn - wer lange
     * genug sucht, findet in jedem Datensatz eine Korrelation. Diese fuenf
     * sind die, nach denen man wirklich fragt.
     */
    private val PAARE = listOf(
        listOf("Schlaf", "schlaf", "Ruhepuls", "ruhepuls"),
        listOf("Schlaf", "schlaf", "HRV", "hrv"),
        listOf("Tiefschlaf", "tief", "HRV", "hrv"),
        listOf("Schritte", "schritte", "Schlaf", "schlaf"),
        listOf("Aktiv", "aktiv", "Ruhepuls", "ruhepuls"),
    )

    private fun zusammenhaenge(
        ctx: Context,
        s: LinearLayout,
        daten: Trenddaten,
        heute: LocalDate,
    ) {
        s.addView(ctx.abschnitt("ZUSAMMENHÄNGE"))

        PAARE.forEach { (nameX, spalteX, nameY, spalteY) ->
            val bild = Auswertung.zusammenhang(
                reihe(daten, spalteX), reihe(daten, spalteY), heute
            )
            val karte = ctx.karte()
            karte.addView(ctx.kartentitel("$nameX und $nameY"))

            if (!bild.belastbar) {
                karte.addView(ctx.zart(
                    "Gemeinsame Tage: ${bild.n}. Gezeigt wird erst ab " +
                        "${Auswertung.PAARE_MINDESTENS} — aus einer Handvoll Punkte " +
                        "lässt sich jede Gerade legen, und sie sähe überzeugend aus."
                ))
                s.addView(karte)
                return@forEach
            }

            karte.addView(ctx.streubild(bild, formel(spalteX), formel(spalteY)))
            karte.addView(ctx.zart("waagerecht $nameX, senkrecht $nameY"))
            val r = bild.r ?: 0.0
            karte.addView(ctx.fliesstext(
                "r = " + Zahlen.zwei(r) + " über ${bild.n} Tage — ${bild.staerke} " +
                    "Zusammenhang." +
                    if (kotlin.math.abs(r) >= 0.2)
                        " Mehr $nameX ging mit " +
                            (if (r > 0) "mehr" else "weniger") + " $nameY einher."
                    else ""
            ))
            s.addView(karte)
        }

        // EINMAL AM ENDE, nicht fuenfmal daneben: der Satz gilt fuer alle
        // Bilder hier, und wiederholt liest ihn niemand mehr.
        s.addView(ctx.zart(
            "Zusammenhang ist keine Ursache. Wer lange schläft, hat vielleicht " +
                "einen tieferen Ruhepuls — oder wer einen tieferen Ruhepuls hat, " +
                "schläft besser, oder beides hängt an einem dritten, das hier gar " +
                "nicht steht. Und: fünf Paare sind fünf Versuche; bei genug " +
                "Versuchen findet sich irgendwo immer eine Linie."
        ))
    }

    /**
     * Die Reihe zu einer Spalte - mit dem Ruhepuls als Sonderfall.
     *
     * Gemessener schlaegt geschaetzten, aber nie beide fuer denselben Tag.
     */
    private fun reihe(daten: Trenddaten, spalte: String): List<Pair<LocalDate, Double>> =
        if (spalte == "ruhepuls")
            verschmelze(daten["ruhepuls"].orEmpty(), daten["puls_min"].orEmpty())
        else daten[spalte].orEmpty()

    private fun formel(spalte: String): (Double) -> String =
        if (spalte == "schlaf" || spalte == "tief") { d -> Zahlen.dauer(d) ?: "" }
        else { d -> Zahlen.ganz(d) ?: "" }

    /**
     * Wochenende gegen Werktag, in einem Satz.
     *
     * Kein eigenes Bild: der Unterschied ist EINE Zahl, und ein Balkenpaar
     * dafuer waere Verpackung. Steht hier nichts, fehlen auf einer Seite die
     * Tage.
     */
    private fun wochenendzeile(
        ctx: Context,
        karte: LinearLayout,
        daten: Trenddaten,
        spalte: String,
        name: String,
        form: (Double) -> String,
        heute: LocalDate,
    ) {
        val w = Auswertung.wochenende(reihe(daten, spalte), heute) ?: return
        val mehr = w.unterschied >= 0
        karte.addView(ctx.fliesstext(
            "$name: " + form(w.wochenende) + " am Wochenende gegen " +
                form(w.werktag) + " unter der Woche — " +
                form(kotlin.math.abs(w.unterschied)) + (if (mehr) " mehr" else " weniger") + "."
        ))
    }

    /**
     * Die Bilanz gegen das eigene Ideal.
     *
     * DAS IST DER ZWECK DES IDEALWERTS: nicht ein Strich im Bild, sondern die
     * Frage "wie oft habe ich ihn erreicht". Gezaehlt werden ganze Naechte,
     * nicht Anteile - halb erreicht gibt es beim Schlafen nicht.
     */
    private fun erreicht(
        ctx: Context,
        karte: LinearLayout,
        reihe: List<Pair<LocalDate, Double>>,
        ideal: Double,
        heute: LocalDate,
    ) {
        val naechte = reihe.filter { it.first < heute }
        if (naechte.isEmpty()) return
        val gut = naechte.count { it.second >= ideal }
        val schnitt = naechte.map { it.second }.average()
        val unterschied = schnitt - ideal
        karte.addView(ctx.fliesstext(
            "Dein Ideal " + (Zahlen.dauer(ideal) ?: "") + " — erreicht in " +
                gut + " von " + naechte.size + " Nächten. Im Schnitt " +
                (Zahlen.dauer(kotlin.math.abs(unterschied)) ?: "") +
                (if (unterschied >= 0) " darüber." else " darunter.")
        ))
    }

    // --- Kleinkram -----------------------------------------------------------

    /**
     * Gemessener Ruhepuls schlaegt geschaetzten.
     *
     * Beide in einer Reihe, aber nie beide fuer denselben Tag: wo ein
     * eingetragener Wert steht, hat das Nachttief nichts zu suchen.
     */
    private fun verschmelze(
        echt: List<Pair<LocalDate, Double>>,
        ersatz: List<Pair<LocalDate, Double>>,
    ): List<Pair<LocalDate, Double>> {
        val karte = ersatz.toMap().toMutableMap()
        echt.forEach { karte[it.first] = it.second }
        return karte.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    private fun wochensaeulen(ctx: Context, bild: Auswertung.Bild): List<Saeule> {
        val diese = Einstellungen.heute(ctx).with(DayOfWeek.MONDAY)
        return bild.wochen.map { w -> Saeule(kw(w.montag), w.mittel, hervor = w.montag == diese) }
    }

    private fun schnittzeile(
        bild: Auswertung.Bild,
        form: (Double) -> String,
        einheit: String,
    ) = bild.gesamt?.let { "Gestrichelt: der Schnitt über alle Tage, " + form(it) + einheit + "." }
        ?: "Noch kein Schnitt."

    private fun extreme(
        ctx: Context,
        karte: LinearLayout,
        bild: Auswertung.Bild,
        form: (Double) -> String,
        einheit: String,
    ) {
        val stark = bild.staerkster ?: return
        val schwach = bild.schwaechster ?: return
        if (stark.tag == schwach.tag) return
        karte.addView(ctx.fliesstext(
            "Am meisten am " + lang(stark.tag) + " (" + form(stark.mittel!!) + einheit +
                "), am wenigsten am " + lang(schwach.tag) + " (" +
                form(schwach.mittel!!) + einheit + ")."
        ))
    }

    /**
     * Die Richtung als Doppelpunktsatz.
     *
     * Kein Verb: "der Ruhepuls liegen" und "die vier Wochen liegt" sind beide
     * falsch, und die Zahl kann in beide Richtungen zeigen. Ein Doppelpunkt
     * stimmt immer.
     */
    private fun richtung(bild: Auswertung.Bild, was: String = "Letzte vier Wochen") =
        bild.veraenderung?.let { v ->
            val vorzeichen = if (v >= 0) "+" else ""
            "$was: $vorzeichen${Zahlen.ganz(v)} % gegenüber den vier davor."
        } ?: "Für einen Vergleich über acht Wochen fehlen noch Tage."

    private fun kurz(tag: DayOfWeek) =
        tag.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun lang(tag: DayOfWeek) =
        tag.getDisplayName(TextStyle.FULL, Locale.getDefault())

    private fun kw(montag: LocalDate) =
        montag.get(WeekFields.ISO.weekOfWeekBasedYear()).toString()
}
