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

    fun baue(ctx: Context, stand: Gesundheit.Stand?): LinearLayout {
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
        bewegung.addView(ctx.zart("Schritte, sieben Tage"))
        bewegung.addView(ctx.wochenbild(stand.wocheSchritte, Gesundheit.ZIEL_SCHRITTE))
        s.addView(bewegung)

        // --- Schlaf ---
        s.addView(ctx.abschnitt("SCHLAF"))
        val schlaf = ctx.karte()
        schlaf.addView(ctx.messreihe(
            ctx.wert(stand.schlaf, Zahlen.dauer(stand.schlaf.zahl), ""),
            ctx.messwert("Tiefschlaf", Zahlen.dauer(stand.phasen?.tief), "", 0f, false),
        ))
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
        schlaf.addView(ctx.zart("Sieben Nächte"))
        schlaf.addView(ctx.wochenbild(
            stand.wocheSchlaf, Gesundheit.ZIEL_SCHLAF_H * 60
        ) { Zahlen.dauer(it) ?: "" })
        s.addView(schlaf)

        // --- Herz ---
        s.addView(ctx.abschnitt("HERZ"))
        val herz = ctx.karte()
        herz.addView(ctx.messreihe(
            ctx.wert(stand.ruhepuls, Zahlen.ganz(stand.ruhepuls.zahl), "bpm"),
            ctx.wert(stand.hrv, Zahlen.ganz(stand.hrv.zahl), "ms"),
        ))
        herz.addView(ctx.zart("Puls heute, gestrichelt der Ruhepuls"))
        herz.addView(ctx.pulsbild(stand.pulsverlauf, stand.ruhepuls.zahl))
        if (stand.ruhepuls.geschaetzt) {
            herz.addView(ctx.zart(
                "Das ≈ beim Ruhepuls heisst: niemand hat einen eingetragen. " +
                    "Gezeigt wird der tiefste gemessene Puls der Nacht — nah " +
                    "dran, aber nicht dasselbe."
            ))
        }
        s.addView(herz)

        // --- Wasser ---
        s.addView(ctx.abschnitt("WASSER"))
        val wasser = ctx.karte()
        wasser.addView(ctx.reihe().apply {
            addView(ctx.wert(stand.wasser, Zahlen.ganz(stand.wasser.zahl), "ml"))
        })
        wasser.addView(ctx.zart("Sieben Tage"))
        wasser.addView(ctx.wochenbild(stand.wocheWasser, 8 * 300.0))
        s.addView(wasser)

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
            w.anteil,
            w.ziel != null && w.da,
        )
}
