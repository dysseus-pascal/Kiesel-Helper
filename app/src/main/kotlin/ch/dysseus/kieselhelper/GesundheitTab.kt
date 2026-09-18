package ch.dysseus.kieselhelper

import android.content.Context
import android.widget.LinearLayout

/**
 * Der Gesundheits-Schirm: acht Zahlen und sonst nichts.
 *
 * DAS IST DER GANZE PUNKT. Die Gesundheitsakte kann alles und zeigt darum
 * nichts zuerst - man sucht sich durch Listen zu einer Zahl, die man taeglich
 * wissen will. Hier stehen sie auf einem Schirm, in der Reihenfolge, in der
 * man sie braucht: erst was man selbst tut (Bewegung), dann was der Koerper
 * meldet (Herz, Schlaf).
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

        s.addView(ctx.abschnitt("BEWEGUNG"))
        val bewegung = ctx.karte()
        bewegung.addView(ctx.messreihe(
            ctx.messwert("Schritte", Zahlen.ganz(stand.schritte.zahl), "",
                         stand.schritte.anteil, stand.schritte.da),
            ctx.messwert("Aktiv", Zahlen.ganz(stand.aktiv.zahl), "min",
                         stand.aktiv.anteil, stand.aktiv.da),
        ))
        bewegung.addView(ctx.messreihe(
            ctx.messwert("Distanz", Zahlen.eine(stand.distanz.zahl), "km", 0f, false),
            ctx.messwert("Kalorien", Zahlen.ganz(stand.kalorien.zahl), "kcal", 0f, false),
        ))
        s.addView(bewegung)

        s.addView(ctx.abschnitt("HERZ UND SCHLAF"))
        val herz = ctx.karte()
        herz.addView(ctx.messreihe(
            ctx.messwert("Schlaf", Zahlen.dauer(stand.schlaf.zahl), "",
                         stand.schlaf.anteil, stand.schlaf.da),
            ctx.messwert("Wasser", Zahlen.ganz(stand.wasser.zahl), "ml",
                         stand.wasser.anteil, stand.wasser.da),
        ))
        herz.addView(ctx.messreihe(
            ctx.messwert("Ruhepuls", Zahlen.ganz(stand.ruhepuls.zahl), "bpm", 0f, false),
            ctx.messwert("HRV", Zahlen.ganz(stand.hrv.zahl), "ms", 0f, false),
        ))
        s.addView(herz)

        // Woher die Zahlen kommen - und warum manche fehlen. Ohne diese Zeile
        // haelt man ein leeres Feld fuer einen Fehler der App.
        s.addView(ctx.zart(
            "Alles aus Health Connect. Ein Strich heisst: dort steht nichts — " +
                "nicht, dass der Wert null ist. Wasser und HRV trägt diese App " +
                "selbst ein, den Rest müssen Uhr oder andere Apps liefern."
        ))
        return s
    }
}
