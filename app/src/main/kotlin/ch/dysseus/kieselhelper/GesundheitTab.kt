package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDateTime
import java.time.ZoneId

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
            k.addView(ctx.schild(false, ctx.getString(R.string.g_akte_fehlt)))
            k.addView(ctx.zart(ctx.getString(R.string.g_akte_fehlt_text)))
            s.addView(k)
            return s
        }

        // --- Bewegung ---
        s.addView(ctx.abschnittTipp(ctx.getString(R.string.g_bewegung)) {
            TrendActivity.zeige(ctx, TrendActivity.BEWEGUNG)
        })
        val bewegung = ctx.karte()
        bewegung.setOnClickListener { TrendActivity.zeige(ctx, TrendActivity.BEWEGUNG) }
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
        bewegung.addView(ctx.zart(ctx.getString(R.string.g_schritte_7)))
        bewegung.addView(ctx.wochenbild(
            stand.wocheSchritte.dropLast(1), Gesundheit.ZIEL_SCHRITTE
        ))

        if (profilHeute.isNotEmpty() || profilTypisch.isNotEmpty()) {
            bewegung.addView(ctx.zartMitHinweis(
                ctx.getString(R.string.g_schritte_tag),
                ctx.getString(R.string.g_schritte_tag_lang)
            ))
            bewegung.addView(ctx.tagesprofil(
                profilHeute, profilTypisch, Gesundheit.STUFE_MIN,
                Einstellungen.tagesgrenze(ctx) * 60,
            ))
        }
        s.addView(bewegung)

        // --- Schlaf ---
        s.addView(ctx.abschnittTipp(ctx.getString(R.string.g_schlaf)) {
            TrendActivity.zeige(ctx, TrendActivity.SCHLAF)
        })
        val schlaf = ctx.karte()
        schlaf.setOnClickListener { TrendActivity.zeige(ctx, TrendActivity.SCHLAF) }
        schlaf.addView(ctx.messreihe(
            ctx.wert(stand.schlaf, Zahlen.dauer(stand.schlaf.zahl), ""),
            ctx.messwert(ctx.getString(R.string.tiefschlaf), Zahlen.dauer(stand.phasen?.tief), "", 0f, false),
        ))
        // WANN, nicht nur wie lange. Die Schlafmitte ist der stabilere Wert:
        // wer jede Nacht gleich lang, aber zu anderen Zeiten schlaeft, hat
        // einen unauffaelligen Mittelwert und trotzdem etwas zu sehen.
        stand.nachtzeiten?.let { z ->
            schlaf.addView(ctx.fliesstext(
                ctx.getString(
                    R.string.g_von_bis_mitte,
                    Zahlen.uhrzeitAb18(z.von) ?: "", Zahlen.uhrzeitAb18(z.bis) ?: "",
                    Zahlen.uhrzeitAb18(z.mitte) ?: "",
                )
            ))
        }
        // DER VERLAUF DER NACHT, nicht nur ihre Summen: vier Bahnen ueber die
        // Zeit. Darunter die Zeiten je Phase ohne den Balken - das Bild
        // zeigt die Anteile schon.
        stand.hypnogramm?.let { schlaf.addView(ctx.hypnogrammbild(it)) }
        if (stand.phasen != null) {
            schlaf.addView(ctx.phasenbild(stand.phasen, mitBalken = stand.hypnogramm == null))
        } else {
            // KEIN GEVIERTELTER BALKEN, wenn niemand Phasen eingetragen hat.
            // Ein Bild, das die Nacht gleichmaessig aufteilt, waere huebsch
            // und erfunden.
            schlaf.addView(ctx.zart(ctx.getString(R.string.g_keine_phasen)))
        }
        val ideal = Einstellungen.schlafziel(ctx).toDouble()
        // OHNE DIE LETZTE NACHT, wie bei den Schritten: oben steht sie
        // ohnehin, und im Bild stuende sie neben sieben abgeschlossenen.
        schlaf.addView(ctx.zartMitHinweis(
            ctx.getString(R.string.g_sieben_naechte),
            ctx.getString(R.string.g_sieben_naechte_lang, Zahlen.dauer(ideal) ?: "")
        ))
        schlaf.addView(ctx.wochenbild(
            stand.wocheSchlaf.dropLast(1), ziel = ideal, marke = ideal
        ) { Zahlen.dauer(it) ?: "" })
        // Zur Seite der Nacht - ein eigenes Ziel, die Karte selbst fuehrt
        // weiter zum Trend.
        if (stand.hypnogramm != null) {
            schlaf.addView(TextView(ctx).apply {
                text = ctx.getString(R.string.n_verlauf)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.akzentfarbe())
                gravity = android.view.Gravity.END
                setPadding(0, ctx.dp(12f), 0, ctx.dp(2f))
                setOnClickListener { NachtActivity.zeige(ctx) }
            })
        }
        s.addView(schlaf)

        // --- Herz ---
        s.addView(ctx.abschnittTipp(ctx.getString(R.string.g_herz)) {
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
        herz.addView(ctx.zartMitHinweis(
            ctx.getString(R.string.g_24h),
            ctx.getString(R.string.g_24h_lang)
        ))
        herz.addView(ctx.pulsbild(
            stand.pulsverlauf, stand.ruhepuls.zahl, beginnMinute = stand.pulsBeginn
        ))
        // DIE STREUUNG IST NICHT DIE HRV. Sie steht deshalb als Satz da und
        // nicht als Kachel neben ihr - und der Satz sagt, was sie misst.
        stand.nachtStreuung.zahl?.let { sd ->
            herz.addView(ctx.textMitHinweis(
                ctx.getString(
                    R.string.g_nacht_streuung,
                    Zahlen.ganz(stand.ruhepuls.zahl) ?: "", Zahlen.ganz(sd) ?: "", stand.nachtProben,
                ),
                ctx.getString(R.string.g_nacht_streuung_lang)
            ))
        }

        if (stand.ruhepuls.geschaetzt) {
            herz.addView(ctx.zartMitHinweis(
                ctx.getString(R.string.g_ruhepuls_geschaetzt),
                ctx.getString(R.string.g_ruhepuls_geschaetzt_lang)
            ))
        }
        s.addView(herz)

        // --- Blutsauerstoff ---
        //
        // DIE KARTE STEHT AUCH OHNE WERTE DA, mit einem Satz, woher sie kommen
        // muessten. Sonst suchte man nach einer Karte, die es nur gibt, wenn
        // schon alles laeuft.
        s.addView(ctx.abschnitt(ctx.getString(R.string.g_spo2)))
        val spo2 = ctx.karte()
        val o = stand.sauerstoff
        spo2.addView(ctx.messreihe(
            ctx.messwert(ctx.getString(R.string.spo2_mittel), Zahlen.ganz(o?.mittel), "%", 0f, false),
            ctx.messwert(ctx.getString(R.string.spo2_tiefster), Zahlen.ganz(o?.tiefster), "%", 0f, false),
        ))
        if (o != null) {
            val z = LocalDateTime.ofInstant(o.letzterZeit, ZoneId.systemDefault())
            spo2.addView(ctx.zartMitHinweis(
                ctx.getString(
                    R.string.g_spo2_letzte,
                    Zahlen.ganz(o.letzter) ?: "", String.format("%02d:%02d", z.hour, z.minute), o.anzahl,
                ),
                ctx.getString(R.string.g_spo2_lang)
            ))
            spo2.addView(ctx.spo2bild(o.verlauf, stand.pulsBeginn))
        } else {
            spo2.addView(ctx.zart(ctx.getString(R.string.g_spo2_leer)))
        }
        s.addView(spo2)

        if (eingaben != null) {
            s.addView(ctx.abschnitt(ctx.getString(R.string.g_wie_war_tag)))
            s.addView(energiekarte(ctx, stand, eingaben))
        }

        // Woher die Zahlen kommen - und warum manche fehlen. Ohne diese Zeile
        // haelt man ein leeres Feld fuer einen Fehler der App.
        s.addView(ctx.zartMitHinweis(
            ctx.getString(R.string.g_alles_hc),
            ctx.getString(R.string.g_alles_hc_lang)
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
            ctx.getString(if (stand.energie == null) R.string.g_energie_leer else R.string.g_energie_da)
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
