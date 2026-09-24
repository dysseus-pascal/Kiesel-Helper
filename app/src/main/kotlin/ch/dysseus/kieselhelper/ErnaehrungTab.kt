package ch.dysseus.kieselhelper

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * Was hineingeht: Wasser, Präparate, Koffein.
 *
 * EIN EIGENER REITER, WEIL ES DIE EINZIGEN ZAHLEN SIND, DIE MAN SELBST MACHT.
 * Schritte und Puls passieren; ein Glas Wasser und die Tablette am Morgen
 * sind Entscheidungen. Sie standen bisher als vierte Karte unter Bewegung,
 * Schlaf und Herz - ganz unten, hinter drei Bildern, und damit genau dort,
 * wo man sie zum Eintragen nicht findet.
 *
 * DER KOFFEIN-KNOPF GEHOERT HIERHER UND NICHT ZU "WIE WAR DER TAG?". Wie man
 * sich fuehlt, ist eine Beobachtung des Tages; ein Espresso ist etwas, das
 * man tut - und zwar mehrmals, weshalb der Knopf nah liegen muss.
 */
object ErnaehrungTab {

    fun baue(
        ctx: Context,
        stand: Gesundheit.Stand?,
        eingaben: Eingaben? = null,
    ): LinearLayout {
        val s = ctx.spalte()

        if (stand == null) {
            val k = ctx.karte()
            k.addView(ctx.schild(false, ctx.getString(R.string.g_akte_fehlt)))
            k.addView(ctx.zart(ctx.getString(R.string.e_akte_fehlt_text)))
            s.addView(k)
            return s
        }

        s.addView(ctx.abschnittTipp(ctx.getString(R.string.e_wasser)) {
            TrendActivity.zeige(ctx, TrendActivity.ERNAEHRUNG)
        })
        s.addView(wasserkarte(ctx, stand).apply {
            setOnClickListener { TrendActivity.zeige(ctx, TrendActivity.ERNAEHRUNG) }
        })

        s.addView(ctx.abschnittTipp(ctx.getString(R.string.e_praeparate)) {
            TrendActivity.zeige(ctx, TrendActivity.ERNAEHRUNG)
        })
        s.addView(praeparatkarte(ctx, stand))

        if (eingaben != null) {
            s.addView(ctx.abschnitt(ctx.getString(R.string.e_koffein)))
            s.addView(koffeinkarte(ctx, stand, eingaben))
        }

        s.addView(ctx.zartMitHinweis(
            ctx.getString(R.string.e_alles_akte),
            ctx.getString(R.string.e_alles_akte_lang)
        ))
        return s
    }

    /**
     * Koffein - ein Tipp je Tasse.
     *
     * DER ZEITPUNKT IST DIE INTERESSANTE HAELFTE. Wie viel Koffein ein Tag
     * hatte, sagt wenig; wann das letzte kam, erklaert die Nacht. Deshalb
     * merkt sich die App die Uhrzeit mit - ohne dass jemand sie eintippt.
     */
    private fun koffeinkarte(
        ctx: Context,
        stand: Gesundheit.Stand,
        eingaben: Eingaben,
    ): LinearLayout {
        val k = ctx.karte()
        val ton = ctx.farbe(R.color.koffein)
        // Die Schlafenszeit von letzter Nacht - sie ist die beste Schaetzung
        // fuer heute. Ohne sie: 23 Uhr.
        val bett = stand.nachtzeiten?.let { (18 * 60 + it.von).toInt() } ?: (23 * 60)
        val kurve = ctx.koffeinkurve(stand.koffeinDosen, bett, ton)

        k.addView(ctx.reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(ctx.stoffzeichen("☕", ton))
            addView(ctx.spalte().apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(ctx.kartentitel(
                    if (stand.koffeinMg == null || stand.koffeinMg <= 0) ctx.getString(R.string.e_heute_keines)
                    else (Zahlen.ganz(stand.koffeinMg) ?: "") + " mg"
                ))
                addView(ctx.zart(
                    ctx.getString(R.string.e_wirkt_gerade, Math.round(kurve.wirkt(jetztMinute())).toInt()) +
                        (Zahlen.uhrzeit(stand.koffeinLetzt)?.let {
                            "  ·  " + ctx.getString(R.string.e_zuletzt_um, it)
                        } ?: "")
                ))
            })
        })
        if (stand.koffeinDosen.isNotEmpty()) {
            k.addView(kurve)
            k.addView(ctx.zartMitHinweis(
                ctx.getString(R.string.e_im_blut),
                ctx.getString(R.string.e_im_blut_lang)
            ))
        }

        // ZWEI REIHEN ZU ZWEIT, nicht vier nebeneinander. Bei vier Knoepfen
        // in einer Zeile brach schon "Espresso" um, und ein Knopf, dessen
        // Beschriftung auf zwei Zeilen steht, sieht kaputt aus.
        listOf(
            listOf(ctx.getString(R.string.e_kaffee) to 80, ctx.getString(R.string.e_espresso) to 60),
            listOf(ctx.getString(R.string.e_tee) to 40, ctx.getString(R.string.e_energy) to 80),
        ).forEach { paar ->
            val zeile = ctx.reihe()
            paar.forEach { (name, mg) ->
                zeile.addView(ctx.knopfLeise(name) { eingaben.fuegeKoffein(mg) }.apply {
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ).apply {
                        marginEnd = ctx.dp(6f)
                        topMargin = ctx.dp(6f)
                    }
                })
            }
            k.addView(zeile)
        }
        k.addView(ctx.zartMitHinweis(
            ctx.getString(R.string.e_portionen),
            ctx.getString(R.string.e_portionen_lang)
        ))
        return k
    }

    private fun jetztMinute(): Int = java.time.LocalTime.now().let { it.hour * 60 + it.minute }

    /**
     * Wasser: ein Glas, das sich fuellt, die Glaeser des Tages, wann sie
     * kamen - und die Woche.
     */
    private fun wasserkarte(ctx: Context, stand: Gesundheit.Stand): LinearLayout {
        val k = ctx.karte()
        val ton = ctx.farbe(R.color.wasser)
        val ml = stand.wasser.zahl ?: 0.0
        // Glasgroesse und Ziel kommen von Drinktervall - dort werden sie
        // eingestellt, und "Ziel+" erhoeht das Ziel fuer heute.
        val glas = Einstellungen.glasMl(ctx).toDouble()
        val ziel = stand.wasser.ziel ?: Einstellungen.wasserzielMl(ctx)
        val fehlt = Math.ceil((ziel - ml) / glas).toInt()

        k.addView(ctx.reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(ctx.glas((ml / ziel).toFloat(), ton, 76f, 96f).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = ctx.dp(16f)
            })
            addView(ctx.spalte().apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(ctx.kartentitel(
                    if (stand.wasser.zahl == null) "—"
                    else (Zahlen.eine(ml / 1000) ?: "") + " l"
                ))
                addView(ctx.zart(
                    ctx.getString(R.string.e_von_ziel, Zahlen.eine(ziel / 1000) ?: "") + "  ·  " +
                        if (fehlt <= 0) ctx.getString(R.string.e_geschafft)
                        else ctx.resources.getQuantityString(R.plurals.e_noch_glaeser, fehlt, fehlt)
                ))
                addView(ctx.glaeserreihe(ml, glas, ziel, ton))
            })
        })

        if (stand.glaeser.isNotEmpty()) {
            k.addView(ctx.trinkleiste(stand.glaeser, ton))
            k.addView(ctx.zart(ctx.getString(R.string.e_glas_fuer_glas)))
        }

        k.addView(ctx.wochenbild(
            stand.wocheWasser.takeLast(Gesundheit.TAGE), ziel, farbe = ton
        ).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ctx.dp(16f) })
        k.addView(ctx.zartMitHinweis(
            ctx.getString(R.string.e_sieben_tage_ziel),
            ctx.getString(R.string.e_sieben_tage_ziel_lang)
        ))
        return k
    }

    /**
     * Praeparate: ein Ring, der sich schliesst, und daneben die Liste.
     *
     * WAS HEUTE ANSTEHT, NAMENTLICH. Eine Quote sagt, wie viel fehlt; sie
     * sagt nicht, WAS fehlt - und danach greift man, wenn man vor dem
     * Schrank steht.
     */
    private fun praeparatkarte(ctx: Context, stand: Gesundheit.Stand): LinearLayout {
        val k = ctx.karte()
        val ton = ctx.akzentfarbe()
        val faellig = stand.suppFaellig.zahl?.toInt() ?: stand.suppListe.size
        val genommen = stand.suppGenommen.zahl?.toInt() ?: stand.suppListe.count { it.genommen }

        if (faellig == 0 && stand.wocheSuppFaellig.none { it.zahl != null }) {
            // KEIN LEERES BILD, sondern der Grund. Wer nichts sieht, sucht
            // sonst den Fehler bei sich.
            k.addView(ctx.zart(ctx.getString(R.string.e_supp_leer)))
            return k
        }

        k.addView(ctx.reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(ctx.praeparatring(genommen, faellig, ton))
            addView(ctx.spalte().apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (stand.suppListe.isEmpty()) {
                    addView(ctx.zart(ctx.getString(R.string.e_supcycle_weiss)))
                }
                stand.suppListe.forEach { eintrag ->
                    addView(ctx.reihe().apply {
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, ctx.dp(3f), 0, ctx.dp(3f))
                        addView(ctx.haken(eintrag.genommen, ton))
                        addView(
                            if (eintrag.genommen) ctx.zart(eintrag.name)
                            else ctx.fliesstext(eintrag.name)
                        )
                    })
                }
            })
        })

        if (stand.wocheSuppFaellig.any { it.zahl != null }) {
            val genommenJeTag = stand.wocheSuppGenommen.associate { it.tag to it.zahl }
            k.addView(ctx.saeulenbild(
                stand.wocheSuppFaellig.takeLast(Gesundheit.TAGE).map { t ->
                    Saeule(
                        t.tag.dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()
                        ),
                        t.zahl,
                        hervor = t.tag == Einstellungen.heute(ctx),
                        innen = genommenJeTag[t.tag],
                    )
                }
            ).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ctx.dp(16f) })
            k.addView(ctx.zartMitHinweis(
                ctx.getString(R.string.e_sieben_tage_supp),
                ctx.getString(R.string.e_sieben_tage_supp_lang)
            ))
        }
        return k
    }

    /** Ein Glas Drinktervall, in Millilitern. */

}
