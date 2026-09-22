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

        s.addView(ctx.abschnittTipp("WASSER UND PRÄPARATE") {
            TrendActivity.zeige(ctx, TrendActivity.ERNAEHRUNG)
        })
        val karte = ctx.karte()
        karte.setOnClickListener { TrendActivity.zeige(ctx, TrendActivity.ERNAEHRUNG) }
        karte.addView(ctx.messreihe(
            ctx.messwert(
                stand.wasser.name, Zahlen.ganz(stand.wasser.zahl), "ml",
                stand.wasser.balkenAnteil,
                stand.wasser.ziel != null && stand.wasser.da,
                stand.wasser.balkenUeber,
            ),
            ctx.messwert(
                "Supplemente", quote(stand), "",
                stand.suppGenommen.balkenAnteil,
                stand.suppGenommen.da && stand.suppFaellig.da,
                stand.suppGenommen.balkenUeber,
            ),
        ))
        // WAS HEUTE ANSTEHT, namentlich. Eine Quote sagt, wie viel fehlt;
        // sie sagt nicht, WAS fehlt - und danach greift man, wenn man vor dem
        // Schrank steht.
        if (stand.suppListe.isNotEmpty()) {
            val liste = ctx.spalte()
            stand.suppListe.forEach { eintrag ->
                liste.addView(ctx.zart(
                    (if (eintrag.genommen) "✓ " else "○ ") + eintrag.name
                ))
            }
            karte.addView(liste)
        }
        karte.addView(ctx.zartMitHinweis(
            "Wasser, sieben Tage",
            "Jedes Glas meldet Drinktervall von der Uhr, mit dem Zeitpunkt. " +
            "Der Balken ist voll bei acht Gläsern à 3 dl — eine Hausnummer, " +
            "keine Vorschrift. Heute zählt mit, anders als bei Schritten und " +
            "Schlaf: ein halber Tag Wasser ist kein schwacher Tag, sondern " +
            "der Stand, nach dem man greift."
        ))
        karte.addView(ctx.wochenbild(
            stand.wocheWasser.takeLast(Gesundheit.TAGE), 8 * 300.0
        ))

        if (stand.wocheSuppFaellig.any { it.zahl != null }) {
            karte.addView(ctx.zartMitHinweis(
                "Supplemente: hell geplant, dunkel genommen",
                "Der genommene Teil sitzt IM geplanten. Zwei Balken " +
                "nebeneinander liessen offen, ob »3 genommen« von drei oder " +
                "von acht war. Was heute ansteht, weiss SupCycle; hier steht " +
                "nur, was davon abgehakt wurde."
            ))
            val genommen = stand.wocheSuppGenommen.associate { it.tag to it.zahl }
            karte.addView(ctx.saeulenbild(
                stand.wocheSuppFaellig.takeLast(Gesundheit.TAGE).map { t ->
                    Saeule(
                        t.tag.dayOfWeek.getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()
                        ),
                        t.zahl,
                        hervor = t.tag == Einstellungen.heute(ctx),
                        innen = genommen[t.tag],
                    )
                }
            ))
        } else {
            // KEIN LEERES BILD, sondern der Grund. Wer nichts sieht, sucht
            // sonst den Fehler bei sich.
            karte.addView(ctx.zart(
                "Von SupCycle kam noch nichts. Die Uhr meldet ihren Stand, " +
                    "sobald dort etwas abgehakt wird — rückwirkend gibt es " +
                    "nichts zu holen, das fängt ab der ersten Einnahme an."
            ))
        }
        s.addView(karte)

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
        k.addView(ctx.fliesstext(
            if (stand.koffeinMg == null || stand.koffeinMg <= 0) "Heute noch keines."
            else (Zahlen.ganz(stand.koffeinMg) ?: "") + " mg" +
                (Zahlen.uhrzeit(stand.koffeinLetzt)?.let { ", zuletzt um " + it } ?: "")
        ))
        // ZWEI REIHEN ZU ZWEIT, nicht vier nebeneinander. Bei vier Knoepfen
        // in einer Zeile brach schon "Espresso" um, und ein Knopf, dessen
        // Beschriftung auf zwei Zeilen steht, sieht kaputt aus.
        listOf(
            listOf("Kaffee" to 80, "Espresso" to 60),
            listOf("Tee" to 40, "Energy" to 80),
        ).forEach { paar ->
            val zeile = ctx.reihe()
            paar.forEach { (name, mg) ->
                zeile.addView(ctx.knopfLeise("+ " + name) { eingaben.fuegeKoffein(mg) }.apply {
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    ).apply {
                        marginEnd = ctx.dp(6f)
                        topMargin = ctx.dp(4f)
                    }
                })
            }
            k.addView(zeile)
        }
        k.addView(ctx.zartMitHinweis(
            "Kaffee 80 mg, Espresso 60, Tee 40, Energy 80",
            "Hausnummern für eine übliche Portion — auf zehn Milligramm kommt " +
                "es nicht an. Für die Frage »Koffein nach 16 Uhr gegen " +
                "Tiefschlaf« zählt ohnehin vor allem der Zeitpunkt des " +
                "letzten, und den merkt die App sich selbst. Alles geht auch " +
                "in die Gesundheitsakte."
        ))
        return k
    }

    /**
     * "3 / 5" statt einer nackten Zahl.
     *
     * Drei genommene Praeparate sind ein Erfolg oder eine Luecke, je nachdem,
     * wie viele anstanden. Die Zahl allein sagt das nicht.
     */
    private fun quote(stand: Gesundheit.Stand): String? {
        val genommen = stand.suppGenommen.zahl ?: return null
        val faellig = stand.suppFaellig.zahl ?: return null
        return (Zahlen.ganz(genommen) ?: "") + " / " + (Zahlen.ganz(faellig) ?: "")
    }
}
