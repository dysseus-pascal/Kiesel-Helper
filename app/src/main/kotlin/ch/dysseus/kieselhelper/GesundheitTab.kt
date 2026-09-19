package ch.dysseus.kieselhelper

import android.content.Context
import android.widget.LinearLayout

/**
 * Der Gesundheits-Schirm: vier Karten, vier Bilder.
 *
 * DAS IST DER GANZE PUNKT. Die Gesundheitsakte kann alles und zeigt darum
 * nichts zuerst - man sucht sich durch Listen zu einer Zahl, die man taeglich
 * wissen will. Hier stehen sie auf einem Schirm, in der Reihenfolge, in der
 * man sie braucht: erst was man selbst tut (Bewegung), dann was der Koerper
 * meldet (Schlaf, Herz), zuletzt was man nachfuellt (Wasser).
 *
 * ZU JEDER ZAHL EIN BILD. Eine Zahl allein sagt nicht, ob sie hoch ist - 7985
 * Schritte sind viel oder wenig, je nachdem, was die Woche davor war. Das Bild
 * daneben beantwortet das ohne ein Wort.
 *
 * Gebaut, nicht gezeichnet: die Werte kommen aus [Gesundheit], und fehlt einer,
 * steht ein Strich statt einer Null.
 */
object GesundheitTab {

    fun baue(
        ctx: Context,
        stand: Gesundheit.Stand?,
        profilHeute: List<Gesundheit.Punkt> = emptyList(),
        profilTypisch: List<Gesundheit.Punkt> = emptyList(),
    ): LinearLayout {
        val s = ctx.spalte()

        if (stand == null) {
            val k = ctx.karte()
            k.addView(ctx.schild(false, "Gesundheitsakte nicht verfügbar"))
            k.addView(ctx.zart(
                "Ohne Health Connect gibt es nichts zu lesen. Die App trägt " +
                    "dann auch nichts ein; die Navigation zur Uhr läuft trotzdem."
            ))
            s.addView(k)
            return s
        }

        // --- Bewegung ---
        s.addView(ctx.abschnitt("BEWEGUNG"))
        val bewegung = ctx.karte()
        bewegung.addView(ctx.messreihe(
            ctx.wert(stand.schritte, Zahlen.ganz(stand.schritte.zahl), ""),
            ctx.wert(stand.aktiv, Zahlen.ganz(stand.aktiv.zahl), "min"),
        ))
        bewegung.addView(ctx.messreihe(
            ctx.wert(stand.distanz, Zahlen.eine(stand.distanz.zahl), "km"),
            ctx.wert(stand.kalorien, Zahlen.ganz(stand.kalorien.zahl), "kcal"),
        ))
        // OHNE HEUTE. Ein halber Tag neben ganzen liest sich wie ein
        // schwacher Tag; oben steht der laufende Stand ohnehin, und zwar
        // als das, was er ist.
        bewegung.addView(ctx.zart("Schritte, die sieben Tage davor"))
        bewegung.addView(ctx.wochenbild(
            stand.wocheSchritte.dropLast(1), Gesundheit.ZIEL_SCHRITTE
        ))

        if (profilHeute.isNotEmpty() || profilTypisch.isNotEmpty()) {
            bewegung.addView(ctx.zart(
                "Schritte über den Tag, halbstündlich. Blass dahinter der " +
                    "Schnitt der letzten zwei Wochen — so sieht man, ob die " +
                    "Bewegung fehlt oder nur noch nicht da war."
            ))
            bewegung.addView(ctx.tagesprofil(
                profilHeute, profilTypisch, Gesundheit.STUFE_MIN,
                Einstellungen.tagesgrenze(ctx) * 60,
            ))
        }
        s.addView(bewegung)

        // --- Schlaf ---
        s.addView(ctx.abschnitt("SCHLAF"))
        val schlaf = ctx.karte()
        schlaf.addView(ctx.messreihe(
            ctx.wert(stand.schlaf, Zahlen.dauer(stand.schlaf.zahl), ""),
            ctx.messwert("Tiefschlaf", Zahlen.dauer(stand.phasen?.tief), "", 0f, false),
        ))
        // WANN, nicht nur wie lange. Die Schlafmitte ist der stabilere Wert:
        // wer jede Nacht gleich lang, aber zu anderen Zeiten schlaeft, hat
        // einen unauffaelligen Mittelwert und trotzdem etwas zu sehen.
        stand.nachtzeiten?.let { z ->
            schlaf.addView(ctx.fliesstext(
                "Von " + (Zahlen.uhrzeitAb18(z.von) ?: "") + " bis " +
                    (Zahlen.uhrzeitAb18(z.bis) ?: "") + ", Mitte " +
                    (Zahlen.uhrzeitAb18(z.mitte) ?: "") + "."
            ))
        }
        if (stand.phasen != null) {
            schlaf.addView(ctx.phasenbild(stand.phasen))
        } else {
            // KEIN GEVIERTELTER BALKEN, wenn niemand Phasen eingetragen hat.
            // Ein Bild, das die Nacht gleichmaessig aufteilt, waere huebsch
            // und erfunden.
            schlaf.addView(ctx.zart(
                "Keine Phasen eingetragen — die Akte kennt für diese Nacht nur " +
                    "die Dauer."
            ))
        }
        val ideal = Einstellungen.schlafziel(ctx).toDouble()
        // OHNE DIE LETZTE NACHT, wie bei den Schritten: oben steht sie
        // ohnehin, und im Bild stuende sie neben sieben abgeschlossenen.
        schlaf.addView(ctx.zart(
            "Die sieben Nächte davor. Die Linie ist dein Ideal von " +
                (Zahlen.dauer(ideal) ?: "") + "; was darüber liegt, steht " +
                "in eigener Farbe."
        ))
        schlaf.addView(ctx.wochenbild(
            stand.wocheSchlaf.dropLast(1), ziel = ideal, marke = ideal
        ) { Zahlen.dauer(it) ?: "" })
        s.addView(schlaf)

        // --- Herz ---
        s.addView(ctx.abschnitt("HERZ"))
        val herz = ctx.karte()
        herz.addView(ctx.messreihe(
            ctx.wert(stand.ruhepuls, Zahlen.ganz(stand.ruhepuls.zahl), "bpm"),
            ctx.wert(stand.hrv, Zahlen.ganz(stand.hrv.zahl), "ms"),
        ))
        herz.addView(ctx.messreihe(
            ctx.wert(stand.pulsTief, Zahlen.ganz(stand.pulsTief.zahl), "bpm"),
            ctx.wert(stand.pulsHoch, Zahlen.ganz(stand.pulsHoch.zahl), "bpm"),
        ))
        herz.addView(ctx.zart(
            "Die letzten 24 Stunden — die Tagesgrenze ist eine Zählgrenze, " +
                "kein Sichtschutz. Jeder Punkt eine Messung, die Linie der " +
                "gleitende Median, gestrichelt der Ruhepuls."
        ))
        herz.addView(ctx.pulsbild(
            stand.pulsverlauf, stand.ruhepuls.zahl, beginnMinute = stand.pulsBeginn
        ))
        if (stand.ruhepuls.geschaetzt) {
            herz.addView(ctx.zart(
                "Das ≈ beim Ruhepuls heisst: niemand hat einen eingetragen. " +
                    "Gezeigt wird der Durchschnitt der zehn tiefsten Messungen " +
                    "der Nacht — nah dran, aber nicht dasselbe."
            ))
        }
        s.addView(herz)

        // --- Ernaehrung ---
        s.addView(ctx.abschnitt("ERNÄHRUNG"))
        val ernaehrung = ctx.karte()
        ernaehrung.addView(ctx.messreihe(
            ctx.wert(stand.wasser, Zahlen.ganz(stand.wasser.zahl), "ml"),
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
            ernaehrung.addView(liste)
        }
        ernaehrung.addView(ctx.zart("Wasser, sieben Tage"))
        ernaehrung.addView(ctx.wochenbild(stand.wocheWasser.takeLast(Gesundheit.TAGE), 8 * 300.0))

        if (stand.wocheSuppFaellig.any { it.zahl != null }) {
            ernaehrung.addView(ctx.zart("Supplemente: hell geplant, dunkel genommen"))
            // Der genommene Teil sitzt IM geplanten. Zwei Balken nebeneinander
            // liessen offen, ob "3 genommen" von drei oder von acht war.
            val genommen = stand.wocheSuppGenommen.associate { it.tag to it.zahl }
            ernaehrung.addView(ctx.saeulenbild(
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
            ernaehrung.addView(ctx.zart(
                "Von SupCycle kam noch nichts. Die Uhr meldet ihren Stand, " +
                    "sobald dort etwas abgehakt wird — rückwirkend gibt es " +
                    "nichts zu holen, das fängt ab der ersten Einnahme an."
            ))
        }
        s.addView(ernaehrung)

        // Woher die Zahlen kommen - und warum manche fehlen. Ohne diese Zeile
        // haelt man ein leeres Feld fuer einen Fehler der App.
        s.addView(ctx.zart(
            "Alles aus Health Connect. Ein Strich heisst: dort steht nichts — " +
                "nicht, dass der Wert null ist. Wasser und HRV trägt diese App " +
                "selbst ein, den Rest müssen Uhr oder andere Apps liefern. Was " +
                "dort wirklich steht, sagt der Technik-Reiter."
        ))
        return s
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

    /**
     * Ein Wert samt Balken - und mit dem Ungefaehr-Zeichen, wo geschaetzt wurde.
     *
     * Das Zeichen ist der ganze Unterschied zwischen "gemessen" und
     * "hergeleitet". Es kostet ein Zeichen und erspart eine falsche Gewissheit.
     */
    private fun Context.wert(w: Gesundheit.Wert, text: String?, einheit: String) =
        messwert(
            w.name,
            if (text != null && w.geschaetzt) "≈$text" else text,
            einheit,
            w.balkenAnteil,
            w.ziel != null && w.da,
            w.balkenUeber,
        )
}
