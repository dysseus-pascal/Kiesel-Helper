package ch.dysseus.kieselhelper

import android.content.Context
import android.widget.LinearLayout
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
 * EINE SEITE JE KARTE. Wer auf den Schlaf tippt, sieht den Schlaf und das,
 * was mit ihm zusammenhaengt - nicht einen Schirm mit allem.
 *
 * GRUPPEN STATT EINZELWERTE. Schlaf ohne Tiefschlaf daneben sagt wenig, und
 * ein Ruhepuls ohne die Spanne des Tages noch weniger. Was zusammen gelesen
 * wird, steht zusammen in einem Bild - nicht hintereinander auf zwei.
 *
 * Die gestrichelte Linie ist ueberall DASSELBE: der Mittelwert ueber alle
 * Tage. So heisst "ueber der Linie" auf jedem Bild dasselbe.
 */
object TrendTab {

    /**
     * Eine Gruppe zusammengehoerender Groessen - eine je Karte.
     *
     * [spalten] traegt die Bilder dieser Seite. [bezug] sagt, welche
     * Zusammenhaenge hierher gehoeren: ein Paar erscheint auf jeder Seite,
     * deren Groesse es enthaelt, und sonst nirgends.
     *
     * [schluessel] waehlt die Form der Seite, [nameId] ist, was dasteht -
     * getrennt, weil ein Name in fuenf Sprachen kein Schluessel sein kann.
     */
    data class Gruppe(
        val schluessel: String,
        val nameId: Int,
        val spalten: List<String>,
        val bezug: Set<String>,
    ) {
        fun name(ctx: Context): String = ctx.getString(nameId)
    }

    /**
     * EINE SEITE JE KARTE, nicht ein Schirm mit allem und einer Leiste oben.
     * Wer auf den Schlaf tippt, will den Schlaf sehen; das Umschalten auf
     * das Herz war ein Weg, den niemand ging, und die Leiste stand dafuer
     * jedes Mal im Bild.
     */
    val GRUPPEN = listOf(
        Gruppe("bewegung", R.string.gruppe_bewegung, listOf("schritte", "aktiv"), setOf("schritte", "aktiv")),
        Gruppe("schlaf", R.string.gruppe_schlaf, listOf("schlaf", "tief", "schlaf_mitte"), setOf("schlaf", "tief")),
        Gruppe("herz", R.string.gruppe_herz, listOf("ruhepuls", "puls_min", "puls_hoch", "puls_tief", "hrv"),
               setOf("ruhepuls", "hrv")),
        Gruppe("ernaehrung", R.string.gruppe_ernaehrung, listOf("wasser", "supp_faellig", "supp_genommen"),
               setOf("koffein_mg")),
    )

    /** Die Zusammenhaenge, die auf die Seite dieser Gruppe gehoeren. */
    private fun paare(gruppe: Gruppe) =
        PAARE.filter { it.spalteX in gruppe.bezug || it.spalteY in gruppe.bezug }

    /**
     * Alles, was die Seite aus dem Speicher braucht: ihre eigenen Spalten und
     * die der Zusammenhaenge darunter. Der Ruhepuls zieht das Nachttief mit,
     * weil er aus beiden zusammengesetzt wird.
     */
    fun spaltenFuer(gruppe: Gruppe): Set<String> {
        val alle = (gruppe.spalten + paare(gruppe).flatMap { listOf(it.spalteX, it.spalteY) }).toMutableSet()
        if ("ruhepuls" in alle) alle += "puls_min"
        return alle
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
            k.addView(ctx.kartentitel(ctx.getString(R.string.tr_zu_wenig, gruppe.name(ctx))))
            k.addView(ctx.zart(ctx.getString(R.string.tr_zu_wenig_text)))
            if (tage > 0) k.addView(ctx.zart(ctx.getString(R.string.tr_gespeichert, tage, datum(seit))))
            s.addView(k)
            return s
        }

        when (gruppe.schluessel) {
            "bewegung" -> {
                val titel = { id: Int -> ctx.getString(id).uppercase(Locale.getDefault()) }
                einfach(ctx, s, titel(R.string.schritte), "schritte", ctx.getString(R.string.schritte), "", daten, heute)
                einfach(ctx, s, titel(R.string.aktiv), "aktiv", ctx.getString(R.string.aktiv), " min", daten, heute)
            }
            "schlaf" -> schlaf(ctx, s, daten, heute)
            "herz" -> herz(ctx, s, daten, heute, wolke)
            "ernaehrung" -> ernaehrung(ctx, s, daten, heute)
        }
        zusammenhaenge(ctx, s, paare(gruppe), daten, heute)

        s.addView(ctx.zart(ctx.getString(R.string.tr_fuss, tage, datum(seit), Auswertung.MINDESTENS)))
        return s
    }

    // --- Die drei Formen -----------------------------------------------------

    /**
     * Eine einzelne Groesse: Wochenprofil und Verlauf.
     *
     * Fehlen die Tage, faellt der Block weg, statt leere Bilder zu zeigen -
     * auf der Bewegungsseite stehen Schritte oft schon lange, bevor die erste
     * aktive Minute eingetragen ist.
     */
    private fun einfach(
        ctx: Context,
        s: LinearLayout,
        titel: String,
        spalte: String,
        name: String,
        einheit: String,
        daten: Trenddaten,
        heute: LocalDate,
    ) {
        val reihe = daten[spalte].orEmpty()
        if (reihe.size < 2) return
        val bild = Auswertung.bild(reihe, heute)
        val form: (Double) -> String = { Zahlen.ganz(it) ?: "" }

        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_typische_woche_x, titel)))
        val woche = ctx.karte()
        woche.addView(ctx.saeulenbild(
            bild.profil.map { p ->
                Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek,
                       kleinster = p.kleinster, groesster = p.groesster)
            },
            ziel = bild.gesamt,
        ))
        woche.addView(ctx.zart(
            schnittzeile(ctx, bild, form, einheit) + " " + ctx.getString(R.string.tr_fuehler)
        ))
        extreme(ctx, woche, bild, form, einheit)
        wochenendzeile(ctx, woche, daten, spalte, name, form, heute)
        s.addView(woche)

        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_verlauf_x, titel)))
        val verlauf = ctx.karte()
        verlauf.addView(ctx.saeulenbild(wochensaeulen(ctx, bild), ziel = bild.gesamt))
        verlauf.addView(ctx.zart(ctx.getString(R.string.tr_kw_schnitt_tag)))
        verlauf.addView(ctx.fliesstext(richtung(ctx, bild)))
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

        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_typische_woche)))
        val woche = ctx.karte()
        woche.addView(ctx.saeulenbild(
            gesamt.profil.map { p ->
                Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek,
                       innen = tiefNachTag[p.tag])
            },
            ziel = gesamt.gesamt,
            marke = ideal,
        ))
        woche.addView(ctx.zart(ctx.getString(R.string.tr_schlaf_legende)))
        erreicht(ctx, woche, daten["schlaf"].orEmpty(), ideal, heute)
        woche.addView(ctx.fliesstext(
            buildString {
                gesamt.gesamt?.let { append(ctx.getString(R.string.tr_im_schnitt, form(it))) }
                tief.gesamt?.let { append(ctx.getString(R.string.tr_davon_tief, form(it))) }
                if (isNotEmpty()) append(".")
            }
        ))
        extreme(ctx, woche, gesamt, form, "")
        wochenendzeile(ctx, woche, daten, "schlaf", ctx.getString(R.string.schlaf), form, heute)
        wochenendzeile(ctx, woche, daten, "tief", ctx.getString(R.string.tiefschlaf), form, heute)
        s.addView(woche)

        // DIE SCHLAFMITTE IST DIE ZWEITE HAELFTE DER GESCHICHTE. Wer jede
        // Nacht gleich lang, aber zu anderen Zeiten schlaeft, hat einen
        // unauffaelligen Mittelwert und trotzdem etwas zu sehen.
        val mitten = daten["schlaf_mitte"].orEmpty()
            .filter { it.first < heute }.map { it.second }
        val streuung = Auswertung.streuung(mitten)
        if (streuung != null) {
            s.addView(ctx.abschnitt(ctx.getString(R.string.tr_schlafmitte)))
            val k = ctx.karte()
            k.addView(ctx.kartentitel(
                (Zahlen.uhrzeitAb18(mitten.average()) ?: "") + " ± " +
                    (Zahlen.dauer(streuung) ?: "")
            ))
            k.addView(ctx.fliesstext(
                ctx.getString(R.string.tr_mitte_naechte, mitten.size) + " " +
                    ctx.getString(when {
                        streuung < 30 -> R.string.tr_sehr_regelmaessig
                        streuung < 60 -> R.string.tr_regelmaessig
                        streuung < 90 -> R.string.tr_schwankend
                        else -> R.string.tr_stark_schwankend
                    })
            ))
            k.addView(ctx.zart(ctx.getString(R.string.tr_ab_18)))
            s.addView(k)
        }

        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_verlauf)))
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
        verlauf.addView(ctx.zart(ctx.getString(R.string.tr_kw_schnitt_nacht)))
        verlauf.addView(ctx.fliesstext(richtung(ctx, gesamt)))
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

        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_typische_woche)))
        val woche = ctx.karte()
        woche.addView(ctx.spannenbild(
            Auswertung.WOCHENTAGE.map { tag ->
                Spanne(kurz(tag), tiefTag[tag], hochTag[tag], ruheTag[tag],
                       hervor = tag == heute.dayOfWeek)
            }
        ))
        woche.addView(ctx.zart(ctx.getString(R.string.tr_herz_legende)))
        woche.addView(ctx.fliesstext(
            buildString {
                ruhe.gesamt?.let { append(ctx.getString(R.string.tr_ruhepuls_schnitt, Zahlen.ganz(it) ?: "")) }
                val t = tief.gesamt; val h = hoch.gesamt
                if (t != null && h != null) {
                    if (isNotEmpty()) append(", ")
                    append(ctx.getString(R.string.tr_tag_zwischen, Zahlen.ganz(t) ?: "", Zahlen.ganz(h) ?: ""))
                }
                if (isNotEmpty()) append(".")
            }
        ))
        s.addView(woche)

        if (wolke.isNotEmpty()) {
            s.addView(ctx.abschnitt(ctx.getString(R.string.tr_typischer_tag)))
            val wolkenkarte = ctx.karte()
            wolkenkarte.addView(ctx.pulsbild(emptyList(), ruhe.gesamt, wolke))
            wolkenkarte.addView(ctx.zart(ctx.getString(R.string.tr_wolke)))
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
        hrvKarte.addView(ctx.zart(schnittzeile(ctx, hrv, { Zahlen.ganz(it) ?: "" }, " ms")))
        s.addView(hrvKarte)

        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_verlauf)))
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
        verlauf.addView(ctx.zart(ctx.getString(R.string.tr_kw)))
        verlauf.addView(ctx.fliesstext(richtung(ctx, ruhe, ctx.getString(R.string.tr_ruhepuls_4w))))
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

        val wasserTitel = ctx.getString(R.string.wasser).uppercase(Locale.getDefault())
        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_typische_woche_x, wasserTitel)))
        val karte = ctx.karte()
        karte.addView(ctx.saeulenbild(
            wasser.profil.map { p ->
                Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek)
            },
            ziel = wasser.gesamt,
        ))
        karte.addView(ctx.zart(schnittzeile(ctx, wasser, form, " ml")))
        extreme(ctx, karte, wasser, form, " ml")
        wochenendzeile(ctx, karte, daten, "wasser", ctx.getString(R.string.wasser), form, heute)
        s.addView(karte)

        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_verlauf_x, wasserTitel)))
        val verlauf = ctx.karte()
        verlauf.addView(ctx.saeulenbild(wochensaeulen(ctx, wasser), ziel = wasser.gesamt))
        verlauf.addView(ctx.zart(ctx.getString(R.string.tr_kw_schnitt_tag)))
        verlauf.addView(ctx.fliesstext(richtung(ctx, wasser)))
        s.addView(verlauf)

        val faellig = Auswertung.bild(daten["supp_faellig"].orEmpty(), heute)
        val genommen = Auswertung.bild(daten["supp_genommen"].orEmpty(), heute)
        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_supplemente)))
        val supp = ctx.karte()
        if (faellig.anzahl == 0) {
            supp.addView(ctx.zart(ctx.getString(R.string.tr_supp_leer)))
        } else {
            val genommenTag = genommen.profil.associate { it.tag to it.mittel }
            supp.addView(ctx.saeulenbild(
                faellig.profil.map { p ->
                    Saeule(kurz(p.tag), p.mittel, hervor = p.tag == heute.dayOfWeek,
                           innen = genommenTag[p.tag])
                }
            ))
            supp.addView(ctx.zart(ctx.getString(R.string.tr_supp_legende)))
            val g = genommen.gesamt
            val f = faellig.gesamt
            supp.addView(ctx.fliesstext(
                if (g != null && f != null && f > 0)
                    ctx.getString(R.string.tr_supp_schnitt, zehntel(f), zehntel(g), Zahlen.ganz(g / f * 100) ?: "")
                else ctx.getString(R.string.tr_kein_schnitt)
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
     *
     * Die Namen sind die Formen fuer die Satzmitte ("mehr Schlaf", aber
     * "plus de sommeil") - klein ausser im Deutschen; der Titel bekommt
     * seinen Grossbuchstaben erst beim Zeigen.
     */
    private data class Paar(val nameX: Int, val spalteX: String, val nameY: Int, val spalteY: String)

    private val PAARE = listOf(
        Paar(R.string.n_schlaf, "schlaf", R.string.n_ruhepuls, "ruhepuls"),
        Paar(R.string.n_schlaf, "schlaf", R.string.n_hrv, "hrv"),
        Paar(R.string.n_tiefschlaf, "tief", R.string.n_hrv, "hrv"),
        Paar(R.string.n_schritte, "schritte", R.string.n_schlaf, "schlaf"),
        Paar(R.string.n_aktiv, "aktiv", R.string.n_ruhepuls, "ruhepuls"),
        // Die beiden, fuer die es das Eintragen von Hand ueberhaupt gibt.
        Paar(R.string.n_schlaf, "schlaf", R.string.n_energie, "energie"),
        Paar(R.string.n_koffein, "koffein_mg", R.string.n_tiefschlaf, "tief"),
    )

    /**
     * Die Zusammenhaenge dieser Seite - nur die, die schon etwas sagen.
     *
     * Solange zu wenige gemeinsame Tage da sind, steht EIN Satz dafuer da,
     * nicht eine Karte je Paar: auf jeder Seite drei Karten mit "noch zu
     * wenig" waeren mehr Rauschen als Auskunft.
     */
    private fun zusammenhaenge(
        ctx: Context,
        s: LinearLayout,
        paare: List<Paar>,
        daten: Trenddaten,
        heute: LocalDate,
    ) {
        if (paare.isEmpty()) return
        s.addView(ctx.abschnitt(ctx.getString(R.string.tr_zusammenhaenge)))

        var gezeigt = 0
        paare.forEach { paar ->
            val (_, spalteX, _, spalteY) = paar
            val nameX = ctx.getString(paar.nameX)
            val nameY = ctx.getString(paar.nameY)
            val bild = Auswertung.zusammenhang(
                reihe(daten, spalteX), reihe(daten, spalteY), heute
            )
            if (!bild.belastbar) return@forEach
            gezeigt++

            val karte = ctx.karte()
            karte.addView(ctx.kartentitel(gross(ctx.getString(R.string.tr_x_und_y, nameX, nameY))))
            karte.addView(ctx.streubild(bild, formel(spalteX), formel(spalteY)))
            karte.addView(ctx.zart(ctx.getString(R.string.tr_achsen, nameX, nameY)))
            val r = bild.r ?: 0.0
            val staerke = ctx.getString(when (bild.stufe) {
                0 -> R.string.tr_staerke_0
                1 -> R.string.tr_staerke_1
                2 -> R.string.tr_staerke_2
                else -> R.string.tr_staerke_3
            })
            karte.addView(ctx.fliesstext(
                ctx.getString(R.string.tr_r_satz, Zahlen.zwei(r), bild.n, staerke) +
                    if (kotlin.math.abs(r) >= 0.2)
                        " " + ctx.getString(
                            if (r > 0) R.string.tr_mehr_mehr else R.string.tr_mehr_weniger, nameX, nameY
                        )
                    else ""
            ))
            s.addView(karte)
        }

        if (gezeigt == 0) {
            val namen = gross(paare.joinToString(", ") {
                ctx.getString(R.string.tr_x_und_y, ctx.getString(it.nameX), ctx.getString(it.nameY))
            })
            s.addView(ctx.karte().apply {
                addView(ctx.zart(ctx.getString(R.string.tr_paare_spaeter, namen, Auswertung.PAARE_MINDESTENS)))
            })
            return
        }

        // EINMAL AM ENDE, nicht je Bild daneben: der Satz gilt fuer alle
        // Bilder hier, und wiederholt liest ihn niemand mehr.
        s.addView(ctx.zart(ctx.getString(R.string.tr_keine_ursache)))
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
            ctx.getString(
                if (mehr) R.string.tr_wochenende else R.string.tr_wochenende_weniger,
                name, form(w.wochenende), form(w.werktag), form(kotlin.math.abs(w.unterschied)),
            )
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
            ctx.getString(
                if (unterschied >= 0) R.string.tr_ideal_darueber else R.string.tr_ideal_darunter,
                Zahlen.dauer(ideal) ?: "", gut, naechte.size,
                Zahlen.dauer(kotlin.math.abs(unterschied)) ?: "",
            )
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
        ctx: Context,
        bild: Auswertung.Bild,
        form: (Double) -> String,
        einheit: String,
    ) = bild.gesamt?.let { ctx.getString(R.string.tr_gestrichelt, form(it) + einheit) }
        ?: ctx.getString(R.string.tr_kein_schnitt)

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
            ctx.getString(
                R.string.tr_extreme,
                lang(stark.tag), form(stark.mittel!!) + einheit,
                lang(schwach.tag), form(schwach.mittel!!) + einheit,
            )
        ))
    }

    /**
     * Die Richtung als Doppelpunktsatz.
     *
     * Kein Verb: "der Ruhepuls liegen" und "die vier Wochen liegt" sind beide
     * falsch, und die Zahl kann in beide Richtungen zeigen. Ein Doppelpunkt
     * stimmt immer.
     */
    private fun richtung(ctx: Context, bild: Auswertung.Bild, was: String = ctx.getString(R.string.tr_letzte_4w)) =
        bild.veraenderung?.let { v ->
            val vorzeichen = if (v >= 0) "+" else ""
            ctx.getString(R.string.tr_richtung, was, vorzeichen + Zahlen.ganz(v))
        } ?: ctx.getString(R.string.tr_kein_vergleich)

    // Wochentage in der Sprache des Telefons - "Mo" ist nur auf Deutsch Montag.
    private fun kurz(tag: DayOfWeek) =
        tag.getDisplayName(TextStyle.SHORT, Locale.getDefault())

    private fun lang(tag: DayOfWeek) =
        tag.getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** Der erste Buchstabe gross - die Namen im Satz sind ausser im Deutschen klein. */
    private fun gross(s: String) = s.replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /** Ein Datum, wie man es im Land des Telefons schreibt. */
    private fun datum(d: LocalDate?): String = d?.format(
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
    ) ?: ""

    private fun kw(montag: LocalDate) =
        montag.get(WeekFields.ISO.weekOfWeekBasedYear()).toString()
}
