package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Der Gesundheits-Schirm: drei Karten, drei Bilder.
 *
 * DAS IST DER GANZE PUNKT. Die Gesundheitsakte kann alles und zeigt darum
 * nichts zuerst - man sucht sich durch Listen zu einer Zahl, die man taeglich
 * wissen will. Hier stehen sie auf einem Schirm, in der Reihenfolge, in der
 * man sie braucht: erst was man selbst tut (Bewegung), dann was der Koerper
 * meldet (Schlaf, Herz).
 *
 * ZU JEDER ZAHL EIN BILD. Eine Zahl allein sagt nicht, ob sie hoch ist - 7985
 * Schritte sind viel oder wenig, je nachdem, was die Woche davor war. Das Bild
 * daneben beantwortet das ohne ein Wort.
 *
 * UND JEDE KARTE FUEHRT WEITER. Die naechste Frage nach "7985" ist immer
 * dieselbe: ist das viel? Sie wird hier nicht beantwortet, sondern eine
 * Beruehrung weiter - im Trend, und zwar gleich bei der richtigen Groesse.
 *
 * Gebaut, nicht gezeichnet: die Werte kommen aus [Gesundheit], und fehlt einer,
 * steht ein Strich statt einer Null.
 */
/** Was der Schirm zurueckmeldet, wenn jemand etwas eintraegt. */
interface Eingaben {
    fun setzeEnergie(wert: Int)
    fun fuegeKoffein(mg: Int)
}

object GesundheitTab {

    fun baue(
        ctx: Context,
        stand: Gesundheit.Stand?,
        profilHeute: List<Gesundheit.Punkt> = emptyList(),
        profilTypisch: List<Gesundheit.Punkt> = emptyList(),
        eingaben: Eingaben? = null,
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
        s.addView(ctx.abschnittTipp("BEWEGUNG") {
            TrendActivity.zeige(ctx, TrendActivity.SCHRITTE)
        })
        val bewegung = ctx.karte()
        bewegung.setOnClickListener { TrendActivity.zeige(ctx, TrendActivity.SCHRITTE) }
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
        s.addView(ctx.abschnittTipp("SCHLAF") {
            TrendActivity.zeige(ctx, TrendActivity.SCHLAF)
        })
        val schlaf = ctx.karte()
        schlaf.setOnClickListener { TrendActivity.zeige(ctx, TrendActivity.SCHLAF) }
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
        s.addView(ctx.abschnittTipp("HERZ") {
            TrendActivity.zeige(ctx, TrendActivity.HERZ)
        })
        val herz = ctx.karte()
        herz.setOnClickListener { TrendActivity.zeige(ctx, TrendActivity.HERZ) }
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
        // DIE STREUUNG IST NICHT DIE HRV. Sie steht deshalb als Satz da und
        // nicht als Kachel neben ihr - und der Satz sagt, was sie misst.
        stand.nachtStreuung.zahl?.let { sd ->
            herz.addView(ctx.fliesstext(
                "In der Nacht " + (Zahlen.ganz(stand.ruhepuls.zahl) ?: "") +
                    " ± " + (Zahlen.ganz(sd) ?: "") + " bpm, über " +
                    stand.nachtProben + " Messungen."
            ))
            herz.addView(ctx.zart(
                "Das ± ist die Streuung der Pulswerte über die Nacht — wie " +
                    "ruhig sie verlief. Es ist NICHT die HRV: die misst die " +
                    "Schwankung zwischen aufeinanderfolgenden Schlägen, und " +
                    "dafür braucht es deren Zeitpunkte, nicht ganze bpm."
            ))
        }

        if (stand.ruhepuls.geschaetzt) {
            herz.addView(ctx.zart(
                "Das ≈ beim Ruhepuls heisst: niemand hat einen eingetragen. " +
                    "Gezeigt wird der Durchschnitt der zehn tiefsten Messungen " +
                    "der Nacht — nah dran, aber nicht dasselbe."
            ))
        }
        s.addView(herz)

        if (eingaben != null) {
            s.addView(ctx.abschnitt("WIE WAR DER TAG?"))
            s.addView(energiekarte(ctx, stand, eingaben))
        }

        // Woher die Zahlen kommen - und warum manche fehlen. Ohne diese Zeile
        // haelt man ein leeres Feld fuer einen Fehler der App.
        s.addView(ctx.zart(
            "Alles aus Health Connect. Ein Strich heisst: dort steht nichts — " +
                "nicht, dass der Wert null ist. Schritte, Puls und Schlaf " +
                "müssen Uhr oder andere Apps liefern. Die Einschätzung von 1 " +
                "bis 5 bleibt hier: für »wie ich mich fühle« hat die Akte " +
                "keinen Satz. Was dort wirklich steht, sagen die Einstellungen."
        ))
        return s
    }

    /**
     * Was kein Sensor weiss.
     *
     * EINE UHR MISST, WIE LANGE MAN GESCHLAFEN HAT; ob man sich ausgeruht
     * FUEHLT, weiss nur der Mensch. Diese eine Zahl macht aus den
     * Zusammenhaengen erst eine Aussage - ohne sie laesst sich ausrechnen,
     * dass der Puls nach kurzen Naechten steigt, aber nicht, ob es einem
     * etwas ausmacht.
     *
     * Ein Tipp, nicht mehr. Was mehr kostet, traegt niemand drei Wochen lang
     * ein - und drei Wochen sind die Untergrenze, ab der sich etwas ablesen
     * laesst.
     */
    private fun energiekarte(
        ctx: Context,
        stand: Gesundheit.Stand,
        eingaben: Eingaben,
    ): LinearLayout {
        val k = ctx.karte()

        val reihe = ctx.reihe()
        (1..5).forEach { stufe ->
            val gewaehlt = stand.energie == stufe
            reihe.addView(TextView(ctx).apply {
                text = stufe.toString()
                gravity = android.view.Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.farbe(
                    if (gewaehlt) R.color.akzent_schrift else R.color.schrift_zart
                ))
                background = GradientDrawable().apply {
                    setColor(ctx.farbe(if (gewaehlt) R.color.akzent else R.color.karte))
                    cornerRadius = ctx.dp(10f).toFloat()
                    if (!gewaehlt) setStroke(ctx.dp(1f), ctx.farbe(R.color.linie))
                }
                layoutParams = LinearLayout.LayoutParams(
                    0, ctx.dp(44f), 1f
                ).apply { marginEnd = ctx.dp(6f) }
                setOnClickListener { eingaben.setzeEnergie(stufe) }
            })
        }
        k.addView(reihe)
        k.addView(ctx.zart(
            if (stand.energie == null)
                "1 heisst erschöpft, 5 heisst frisch. Eine Zahl am Tag, und in " +
                    "drei Wochen sieht man, woran sie hängt."
            else "Eingetragen. Ein Tippen ändert sie."
        ))
        return k
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
            if (text != null && w.geschaetzt) "≈" + text else text,
            einheit,
            w.balkenAnteil,
            w.ziel != null && w.da,
            w.balkenUeber,
        )
}
