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
            k.addView(ctx.schild(false, "Gesundheitsakte nicht verfügbar"))
            k.addView(ctx.zart(
                "Ohne Health Connect gibt es nichts zu lesen und nichts " +
                    "einzutragen."
            ))
            s.addView(k)
            return s
        }

        s.addView(ctx.abschnittTipp("WASSER") {
            TrendActivity.zeige(ctx, TrendActivity.ERNAEHRUNG)
        })
        s.addView(wasserkarte(ctx, stand).apply {
            setOnClickListener { TrendActivity.zeige(ctx, TrendActivity.ERNAEHRUNG) }
        })

        s.addView(ctx.abschnittTipp("PRÄPARATE") {
            TrendActivity.zeige(ctx, TrendActivity.ERNAEHRUNG)
        })
        s.addView(praeparatkarte(ctx, stand))

        if (eingaben != null) {
            s.addView(ctx.abschnitt("KOFFEIN"))
            s.addView(koffeinkarte(ctx, stand, eingaben))
        }

        s.addView(ctx.zartMitHinweis(
            "Alles geht in die Gesundheitsakte",
            "Wasser, Koffein und Präparate trägt diese App selbst dort ein — " +
                "sie sind damit auch für andere Apps da und überleben eine " +
                "Neuinstallation. Das Wasser meldet Drinktervall von der Uhr, " +
                "die Präparate SupCycle. Auf jedem Eintrag liegt ein Riegel " +
                "gegen Doppelte."
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
                    if (stand.koffeinMg == null || stand.koffeinMg <= 0) "Heute noch keines"
                    else (Zahlen.ganz(stand.koffeinMg) ?: "") + " mg"
                ))
                addView(ctx.zart(
                    "wirkt gerade: " + Math.round(kurve.wirkt(jetztMinute())) + " mg" +
                        (Zahlen.uhrzeit(stand.koffeinLetzt)?.let { "  ·  zuletzt um " + it } ?: "")
                ))
            })
        })
        if (stand.koffeinDosen.isNotEmpty()) {
            k.addView(kurve)
            k.addView(ctx.zartMitHinweis(
                "Was im Blut ist, und ☾ zur Schlafenszeit",
                "Jede Tasse ist ein Sprung und danach ein langsames Abklingen: " +
                    "der Körper baut Koffein mit einer Halbwertszeit von rund " +
                    "fünf Stunden ab. Ein Espresso um 17 Uhr ist um 22 Uhr noch " +
                    "zur Hälfte da. Die fünf Stunden sind ein Mittel — sie " +
                    "schwanken zwischen etwa drei und sieben —, die Kurve zeigt " +
                    "die Form, keine Messung. Die Schlafenszeit ist die von " +
                    "letzter Nacht."
            ))
        }

        // ZWEI REIHEN ZU ZWEIT, nicht vier nebeneinander. Bei vier Knoepfen
        // in einer Zeile brach schon "Espresso" um, und ein Knopf, dessen
        // Beschriftung auf zwei Zeilen steht, sieht kaputt aus.
        listOf(
            listOf("☕ Kaffee" to 80, "☕ Espresso" to 60),
            listOf("🍵 Tee" to 40, "⚡ Energy" to 80),
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
            "Kaffee 80 mg, Espresso 60, Tee 40, Energy 80",
            "Hausnummern für eine übliche Portion — auf zehn Milligramm kommt " +
                "es nicht an. Für die Frage »Koffein nach 16 Uhr gegen " +
                "Tiefschlaf« zählt ohnehin vor allem der Zeitpunkt, und den " +
                "merkt die App sich selbst. Alles geht auch in die " +
                "Gesundheitsakte."
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
        val ziel = stand.wasser.ziel ?: (8 * GLAS)
        val fehlt = Math.ceil((ziel - ml) / GLAS).toInt()

        k.addView(ctx.reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(ctx.glas((ml / ziel).toFloat(), ton, 76f, 96f).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = ctx.dp(16f)
            })
            addView(ctx.spalte().apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(ctx.kartentitel(
                    if (stand.wasser.zahl == null) "—"
                    else String.format("%.1f l", ml / 1000).replace('.', ',')
                ))
                addView(ctx.zart(
                    "von " + String.format("%.1f l", ziel / 1000).replace('.', ',') + "  ·  " +
                        when {
                            fehlt <= 0 -> "geschafft"
                            fehlt == 1 -> "noch ein Glas"
                            else -> "noch $fehlt Gläser"
                        }
                ))
                addView(ctx.glaeserreihe(ml, GLAS, ziel, ton))
            })
        })

        if (stand.glaeser.isNotEmpty()) {
            k.addView(ctx.trinkleiste(stand.glaeser, ton))
            k.addView(ctx.zart("Heute, Glas für Glas — je grösser der Tropfen, desto mehr"))
        }

        k.addView(ctx.wochenbild(
            stand.wocheWasser.takeLast(Gesundheit.TAGE), ziel, farbe = ton
        ).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ctx.dp(16f) })
        k.addView(ctx.zartMitHinweis(
            "Sieben Tage, gestrichelt das Ziel",
            "Jedes Glas meldet Drinktervall von der Uhr, mit dem Zeitpunkt. " +
                "Das Ziel sind acht Gläser à 3 dl, wenn Drinktervall kein " +
                "anderes schickt — eine Hausnummer, keine Vorschrift. Heute " +
                "zählt mit, anders als bei Schritten und Schlaf: ein halber " +
                "Tag Wasser ist kein schwacher Tag, sondern der Stand, nach " +
                "dem man greift."
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
            k.addView(ctx.zart(
                "Von SupCycle kam noch nichts. Die Uhr meldet ihren Stand, " +
                    "sobald dort etwas abgehakt wird — rückwirkend gibt es " +
                    "nichts zu holen, das fängt ab der ersten Einnahme an."
            ))
            return k
        }

        k.addView(ctx.reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(ctx.praeparatring(genommen, faellig, ton))
            addView(ctx.spalte().apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (stand.suppListe.isEmpty()) {
                    addView(ctx.zart("Was heute ansteht, weiss SupCycle."))
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
                            java.time.format.TextStyle.SHORT, java.util.Locale.GERMAN
                        ),
                        t.zahl,
                        hervor = t.tag == Einstellungen.heute(ctx),
                        innen = genommenJeTag[t.tag],
                    )
                }
            ).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ctx.dp(16f) })
            k.addView(ctx.zartMitHinweis(
                "Sieben Tage: hell geplant, dunkel genommen",
                "Der genommene Teil sitzt IM geplanten. Zwei Balken " +
                    "nebeneinander liessen offen, ob »3 genommen« von drei oder " +
                    "von acht war. Was heute ansteht, weiss SupCycle; hier steht " +
                    "nur, was davon abgehakt wurde."
            ))
        }
        return k
    }

    /** Ein Glas Drinktervall, in Millilitern. */
    private const val GLAS = 300.0

}
