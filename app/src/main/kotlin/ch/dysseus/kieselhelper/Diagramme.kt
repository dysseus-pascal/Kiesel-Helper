package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Die Bilder: Wochenbalken, Schlafphasen, Pulsverlauf.
 *
 * SELBST GEZEICHNET, ohne Diagramm-Bibliothek. Nicht aus Stolz: die drei
 * Bilder hier sind zusammen kuerzer als die Einrichtung einer Bibliothek, und
 * jede Bibliothek braechte ihre eigenen Farben und Schriften mit - genau das,
 * was diese App bei Material You schon einmal rosa gemacht hat.
 *
 * EIN BILD BEHAUPTET SCHNELL MEHR, ALS ES WEISS. Deshalb gilt hier dasselbe
 * wie fuer die Zahlen: ein Tag ohne Eintrag bekommt keinen Balken der Hoehe
 * null, sondern gar keinen; eine Nacht ohne Phasen bekommt keinen geviertelten
 * Balken; und ein Bild ohne Daten sagt das, statt leer dazustehen.
 */

// --- Saeulen ----------------------------------------------------------------

/**
 * Eine Saeule: Beschriftung unten, Wert, und wahlweise eine Zahl obendrueber.
 *
 * Ein Wert von `null` heisst: kein Balken. Nicht einer der Hoehe null - der
 * sagt "an dem Tag nichts getan", und gemeint ist "an dem Tag nichts
 * gemessen".
 */
data class Saeule(
    val beschriftung: String,
    val wert: Double?,
    val hervor: Boolean = false,
    val oben: String? = null,
    /**
     * Ein Anteil INNERHALB des Wertes, von unten gemessen - Tiefschlaf im
     * Schlaf. Zwei Balken nebeneinander waeren hier falsch: der eine steckt
     * im anderen, und nebeneinander sieht es aus wie zwei Dinge.
     */
    val innen: Double? = null,
    /**
     * Die Spanne, aus der der Wert gemittelt wurde. Ein Mittelwert ohne sie
     * ist eine halbe Aussage: drei Mittwoche mit 4000, 8000 und 12000
     * Schritten ergeben denselben Schnitt wie drei mit je 8000.
     */
    val kleinster: Double? = null,
    val groesster: Double? = null,
)

/**
 * Saeulen nebeneinander, mit gestrichelter Ziellinie.
 *
 * Dasselbe Bild traegt drei Dinge: die Woche in Tagen, das Wochenprofil ueber
 * Monate und den Verlauf in Wochen. Eine Zeichnung statt dreier - so gilt
 * ueberall dieselbe Regel, was ein fehlender Wert bedeutet und woran die Hoehe
 * haengt.
 */
class SaeulenView(
    ctx: Context,
    private val saeulen: List<Saeule>,
    private val ziel: Double?,
    /**
     * Eine zweite Linie in der Akzentfarbe - der selbst gesetzte Wert.
     *
     * Sie ist absichtlich anders gefaerbt als die gestrichelte Ziellinie:
     * die eine ist gerechnet (ein Schnitt), die andere gesetzt (ein Anspruch).
     * Zwei graue Striche waeren zwei Aussagen in einer Farbe.
     */
    private val marke: Double? = null,
) : View(ctx) {

    private val stift = Paint(Paint.ANTI_ALIAS_FLAG)
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = context.farbe(R.color.schrift_zart)
    }
    private val zielstift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f).toFloat()
        color = context.farbe(R.color.schrift_zart)
        pathEffect = DashPathEffect(
            floatArrayOf(context.dp(3f).toFloat(), context.dp(3f).toFloat()), 0f
        )
    }
    private val markenstift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1.5f).toFloat()
        color = context.farbe(R.color.akzent)
    }

    private fun sp(wert: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, wert, resources.displayMetrics
    )

    override fun onDraw(leinwand: Canvas) {
        if (saeulen.isEmpty()) {
            schrift.textAlign = Paint.Align.LEFT
            leinwand.drawText("Noch keine Tage im Speicher", 0f, height / 2f, schrift)
            return
        }

        val fussHoehe = context.dp(16f).toFloat()
        val kopfHoehe = sp(11f)
        val hoehe = height - fussHoehe - kopfHoehe
        val breite = width.toFloat()
        val fach = breite / saeulen.size
        val balken = minOf(fach * 0.52f, context.dp(22f).toFloat())
        val ecke = balken / 2.5f

        // Die Spitze des Bildes: das Ziel, sonst der hoechste Wert. Sonst
        // waere eine schwache Woche optisch eine starke - jedes Bild
        // skalierte sich seine eigene Bestleistung zurecht.
        val groesster = saeulen.mapNotNull { it.wert }.maxOrNull() ?: 0.0
        val spitze = maxOf(ziel ?: 0.0, groesster, 1.0) * 1.05

        saeulen.forEachIndexed { i, saeule ->
            val mitte = fach * i + fach / 2
            val links = mitte - balken / 2

            // Die leere Spur - sie zeigt, dass das Fach da ist, auch ohne Wert.
            stift.color = context.farbe(R.color.linie)
            leinwand.drawRoundRect(
                RectF(links, kopfHoehe, links + balken, kopfHoehe + hoehe), ecke, ecke, stift
            )

            val zahl = saeule.wert
            if (zahl != null && zahl > 0) {
                val anteil = (zahl / spitze).coerceIn(0.0, 1.0).toFloat()
                val oben = kopfHoehe + hoehe * (1f - anteil)
                stift.color = context.farbe(R.color.akzent)
                stift.alpha = if (saeule.hervor) 255 else 150
                leinwand.drawRoundRect(
                    RectF(links, oben, links + balken, kopfHoehe + hoehe), ecke, ecke, stift
                )
                stift.alpha = 255

                // WAS UEBER DEM EIGENEN IDEAL LIEGT, bekommt eine eigene
                // Farbe - leise, denn es ist eine Auskunft und kein Lob. Ohne
                // sie muesste man die Balkenspitze mit der Linie vergleichen;
                // mit ihr sieht man es im Vorbeigehen.
                if (marke != null && marke > 0 && zahl > marke) {
                    val markenY = kopfHoehe + hoehe *
                        (1f - (marke / spitze).coerceIn(0.0, 1.0).toFloat())
                    stift.color = context.farbe(R.color.ueber_ziel)
                    stift.alpha = if (saeule.hervor) 255 else 170
                    leinwand.drawRoundRect(
                        RectF(links, oben, links + balken, markenY), ecke, ecke, stift
                    )
                    stift.alpha = 255
                }

                // Der Anteil sitzt IM Balken, von unten. Er wird dunkler
                // gezeichnet, nicht schmaler - sonst waere es ein zweiter
                // Balken, und der behauptete etwas anderes.
                saeule.innen?.let { anteilswert ->
                    if (anteilswert > 0) {
                        val a = (anteilswert / spitze).coerceIn(0.0, 1.0).toFloat()
                        stift.color = context.farbe(R.color.phase_tief)
                        leinwand.drawRoundRect(
                            RectF(
                                links, kopfHoehe + hoehe * (1f - a),
                                links + balken, kopfHoehe + hoehe
                            ), ecke, ecke, stift
                        )
                    }
                }
            }

            // Der Fuehler: von der kleinsten bis zur groessten Messung dieses
            // Fachs, mit Querstrichen an den Enden.
            val von = saeule.kleinster
            val bis = saeule.groesster
            if (von != null && bis != null && bis > von) {
                stift.color = context.farbe(R.color.schrift_zart)
                val oben = kopfHoehe + hoehe * (1f - (bis / spitze).coerceIn(0.0, 1.0).toFloat())
                val unten = kopfHoehe + hoehe * (1f - (von / spitze).coerceIn(0.0, 1.0).toFloat())
                val dick = context.dp(1f).toFloat()
                leinwand.drawRect(mitte - dick / 2, oben, mitte + dick / 2, unten, stift)
                val kappe = balken * 0.3f
                leinwand.drawRect(mitte - kappe, oben - dick, mitte + kappe, oben, stift)
                leinwand.drawRect(mitte - kappe, unten, mitte + kappe, unten + dick, stift)
            }

            saeule.oben?.let { text ->
                schrift.textAlign = Paint.Align.CENTER
                schrift.color = context.farbe(R.color.schrift)
                leinwand.drawText(text, mitte, kopfHoehe - sp(2f), schrift)
                schrift.color = context.farbe(R.color.schrift_zart)
            }

            schrift.textAlign = Paint.Align.CENTER
            leinwand.drawText(
                saeule.beschriftung, mitte, height - context.dp(3f).toFloat(), schrift
            )
        }

        if (ziel != null && ziel > 0) {
            val y = kopfHoehe + hoehe * (1f - (ziel / spitze).toFloat())
            val weg = Path().apply { moveTo(0f, y); lineTo(breite, y) }
            leinwand.drawPath(weg, zielstift)
        }

        // Die eigene Marke zuletzt, damit sie oben liegt.
        if (marke != null && marke > 0 && marke <= spitze) {
            val y = kopfHoehe + hoehe * (1f - (marke / spitze).toFloat())
            leinwand.drawLine(0f, y, breite, y, markenstift)
        }
    }
}

/** Ein Saeulenbild, fertig eingehaengt. */
fun Context.saeulenbild(
    saeulen: List<Saeule>,
    ziel: Double? = null,
    marke: Double? = null,
): View =
    SaeulenView(this, saeulen, ziel, marke).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(96f)
        ).apply { topMargin = dp(10f) }
    }

/**
 * Sieben Tage: der heutige voll im Akzent, die sechs davor blasser.
 *
 * KURZ UND NICHT SCHMAL als Beschriftung: im Deutschen heissen Dienstag und
 * Donnerstag beide "D", Samstag und Sonntag beide "S". Ein Buchstabe spart
 * Platz und kostet die Aussage.
 */
fun Context.wochenbild(
    werte: List<Gesundheit.Tageswert>,
    ziel: Double? = null,
    marke: Double? = null,
    beschriftung: (Double) -> String = { Zahlen.ganz(it) ?: "" },
): View {
    val heute = Einstellungen.heute(this)
    return saeulenbild(
        werte.map { tag ->
            Saeule(
                tag.tag.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                tag.zahl,
                hervor = tag.tag == heute,
                oben = if (tag.tag == heute && tag.zahl != null) beschriftung(tag.zahl) else null,
            )
        },
        ziel,
        marke,
    )
}


// --- Spannen ----------------------------------------------------------------

/**
 * Eine Spanne: von tief bis hoch, mit einer Marke dazwischen.
 *
 * Fuer den Puls ist das die richtige Form. Ein Mittelwert je Tag verschweigt
 * genau das Interessante - ob der Tag zwischen 55 und 60 lag oder zwischen 48
 * und 160.
 */
data class Spanne(
    val beschriftung: String,
    val tief: Double?,
    val hoch: Double?,
    val marke: Double?,
    val hervor: Boolean = false,
)

/**
 * Senkrechte Spannen nebeneinander, mit einem Strich auf der Marke.
 *
 * Die Achse ist fuer alle Spalten DIESELBE - sonst waere die hoechste Saeule
 * immer gleich hoch, egal was drinsteht.
 */
class SpannenView(
    ctx: Context,
    private val spannen: List<Spanne>,
) : View(ctx) {

    private val stift = Paint(Paint.ANTI_ALIAS_FLAG)
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics
        )
        color = ctx.farbe(R.color.schrift_zart)
    }

    override fun onDraw(leinwand: Canvas) {
        val gueltig = spannen.filter { it.tief != null && it.hoch != null }
        if (gueltig.isEmpty()) {
            schrift.textAlign = Paint.Align.LEFT
            leinwand.drawText("Noch keine Pulswerte", 0f, height / 2f, schrift)
            return
        }

        val fussHoehe = context.dp(16f).toFloat()
        val randRechts = context.dp(24f).toFloat()
        val boden = height - fussHoehe
        val breite = width - randRechts
        val oben = gueltig.maxOf { it.hoch!! } + 4
        val unten = maxOf(gueltig.minOf { it.tief!! } - 4, 0.0)
        val spanne = maxOf(oben - unten, 1.0)
        fun y(wert: Double) = (boden - ((wert - unten) / spanne) * boden).toFloat()

        val fach = breite / spannen.size
        val balken = minOf(fach * 0.34f, context.dp(14f).toFloat())

        spannen.forEachIndexed { i, s ->
            val mitte = fach * i + fach / 2
            schrift.textAlign = Paint.Align.CENTER
            leinwand.drawText(
                s.beschriftung, mitte, height - context.dp(3f).toFloat(), schrift
            )
            if (s.tief == null || s.hoch == null) return@forEachIndexed

            stift.color = context.farbe(R.color.akzent)
            stift.alpha = if (s.hervor) 255 else 130
            leinwand.drawRoundRect(
                RectF(mitte - balken / 2, y(s.hoch), mitte + balken / 2, y(s.tief)),
                balken / 2, balken / 2, stift,
            )
            stift.alpha = 255

            // Die Marke ist der Ruhepuls: ein heller Strich quer durch die
            // Spanne. Ohne ihn saehe eine ruhige Nacht aus wie ein ruhiger Tag.
            s.marke?.let { marke ->
                stift.color = context.farbe(R.color.karte)
                val my = y(marke)
                leinwand.drawRect(
                    mitte - balken / 2, my - context.dp(1f),
                    mitte + balken / 2, my + context.dp(1f), stift,
                )
            }
        }

        schrift.textAlign = Paint.Align.LEFT
        leinwand.drawText(
            Zahlen.ganz(gueltig.maxOf { it.hoch!! }) ?: "",
            breite + context.dp(3f), y(gueltig.maxOf { it.hoch!! }) + schrift.textSize / 3, schrift
        )
        leinwand.drawText(
            Zahlen.ganz(gueltig.minOf { it.tief!! }) ?: "",
            breite + context.dp(3f), y(gueltig.minOf { it.tief!! }) + schrift.textSize / 3, schrift
        )
    }
}

fun Context.spannenbild(spannen: List<Spanne>): View =
    SpannenView(this, spannen).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(104f)
        ).apply { topMargin = dp(10f) }
    }

// --- Schlafphasen -----------------------------------------------------------

/**
 * Eine Nacht als ein Balken, in vier Stuecken.
 *
 * Die Reihenfolge ist fest - tief, REM, leicht, wach - und NICHT die
 * zeitliche: ein zeitlicher Verlauf braeuchte die einzelnen Abschnitte, und
 * die liefert nicht jede Uhr mit. Was hier steht, sind Anteile.
 */
class PhasenView(
    ctx: Context,
    private val phasen: Gesundheit.Phasen,
) : View(ctx) {

    private val stift = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(leinwand: Canvas) {
        val summe = phasen.summe
        if (summe <= 0) return

        val ecke = height / 2f
        val stuecke = listOf(
            phasen.tief to R.color.phase_tief,
            phasen.rem to R.color.phase_rem,
            phasen.leicht to R.color.phase_leicht,
            phasen.wach to R.color.phase_wach,
        )

        // Erst den ganzen Balken mit runden Ecken, dann die Stuecke hinein -
        // so bleiben aussen die Rundungen und innen die Kanten scharf.
        leinwand.save()
        val weg = Path().apply {
            addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), ecke, ecke, Path.Direction.CW)
        }
        leinwand.clipPath(weg)

        var x = 0f
        stuecke.forEach { (minuten, farbId) ->
            if (minuten <= 0) return@forEach
            val bis = x + (minuten / summe).toFloat() * width
            stift.color = context.farbe(farbId)
            leinwand.drawRect(x, 0f, bis, height.toFloat(), stift)
            x = bis
        }
        leinwand.restore()
    }
}

/** Der Phasenbalken samt Beschriftung darunter. */
fun Context.phasenbild(phasen: Gesundheit.Phasen): LinearLayout {
    val kasten = spalte()
    kasten.addView(PhasenView(this, phasen).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(14f)
        ).apply { topMargin = dp(10f) }
    })

    val zeile = reihe()
    zeile.setPadding(0, dp(8f), 0, 0)
    listOf(
        Triple("Tief", phasen.tief, R.color.phase_tief),
        Triple("REM", phasen.rem, R.color.phase_rem),
        Triple("Leicht", phasen.leicht, R.color.phase_leicht),
        Triple("Wach", phasen.wach, R.color.phase_wach),
    ).forEach { (name, minuten, farbId) ->
        zeile.addView(phasenschild(name, minuten, farbId))
    }
    kasten.addView(zeile)
    return kasten
}

private fun Context.phasenschild(name: String, minuten: Double, farbId: Int): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
        addView(reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(View(this@phasenschild).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8f), dp(8f)).apply {
                    marginEnd = dp(5f)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(farbe(farbId))
                }
            })
            addView(android.widget.TextView(this@phasenschild).apply {
                text = name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(farbe(R.color.schrift_zart))
            })
        })
        addView(android.widget.TextView(this@phasenschild).apply {
            text = Zahlen.dauer(minuten) ?: "—"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(farbe(R.color.schrift))
        })
    }

// --- Pulswolke ---------------------------------------------------------------

/**
 * Der Puls ueber den Tag - als Wolke mit einem Faden hindurch.
 *
 * JEDER PUNKT IST EINE MESSUNG. Die Uhr misst alle zehn Minuten und waehrend
 * einer Anstrengung dauernd; eine geglaettete Linie machte daraus einen ruhigen
 * Verlauf und verschwiege genau die Ausschlaege, deretwegen man hinschaut.
 *
 * DIE TRENDLINIE IST EIN GLEITENDER MEDIAN, kein Mittel. Ein Mittel zoege eine
 * halbe Stunde Sport eine Stunde lang mit sich; der Median haelt die Linie
 * dort, wo die meisten Punkte liegen, und laesst die Spitzen Spitzen sein.
 *
 * Der Tag steht IMMER ganz da, von null bis vierundzwanzig Uhr - eine Wolke,
 * die sich auf die gemessene Spanne streckt, sieht um acht Uhr morgens aus wie
 * ein ganzer Tag. Die Stundenstriche bei 6, 12 und 18 Uhr sind das Gegenmittel.
 */
class PulsView(
    ctx: Context,
    private val punkte: List<Gesundheit.Punkt>,
    private val ruhe: Double?,
    /**
     * Welche Uhrzeit am linken Rand steht, als Minute des Tages.
     *
     * Das Bild zeigt nicht "den Tag", sondern ein Fenster von
     * vierundzwanzig Stunden - die Tagesgrenze ist eine Zaehlgrenze und
     * kein Sichtschutz. Die Stundenstriche nennen deshalb die echte Uhrzeit.
     */
    private val beginnMinute: Int = 0,
    /**
     * Fruehere Tage, blass dahinter - der "typische Tag".
     *
     * Ist etwas da, zieht die Trendlinie durch die FRUEHEREN Punkte, nicht
     * durch die heutigen: gefragt ist dann, wie ein Tag normalerweise
     * verlaeuft, und heute liegt als einzelner Fall darueber.
     */
    private val frueher: List<Gesundheit.Punkt> = emptyList(),
) : View(ctx) {

    /** Halbe Fensterbreite des gleitenden Medians, in Minuten. */
    private val fenster = 30

    private val tupfen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.farbe(R.color.akzent)
        alpha = 110
    }
    private val schatten = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.farbe(R.color.schrift_zart)
        alpha = 45
    }
    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2f).toFloat()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = context.farbe(R.color.akzent)
    }
    private val gitter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f).toFloat()
        color = context.farbe(R.color.linie)
    }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 10f, context.resources.displayMetrics
        )
        color = context.farbe(R.color.schrift_zart)
    }
    private val ruhestift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(1f).toFloat()
        color = context.farbe(R.color.schrift_zart)
        pathEffect = DashPathEffect(
            floatArrayOf(context.dp(3f).toFloat(), context.dp(3f).toFloat()), 0f
        )
    }

    override fun onDraw(leinwand: Canvas) {
        val fuss = context.dp(14f).toFloat()
        val rand = context.dp(26f).toFloat()      // Platz fuer die Zahlen rechts
        val boden = height - fuss
        val breite = width - rand

        val alle = punkte + frueher
        if (alle.isEmpty()) {
            schrift.textAlign = Paint.Align.LEFT
            leinwand.drawText("Keine Pulsmessungen", 0f, height / 2f, schrift)
            return
        }

        val hoechster = alle.maxOf { it.wert }
        val tiefster = minOf(alle.minOf { it.wert }, ruhe ?: Double.MAX_VALUE)
        val oben = hoechster + 5
        val unten = maxOf(tiefster - 5, 0.0)
        val spanne = maxOf(oben - unten, 1.0)
        fun y(wert: Double) = (boden - ((wert - unten) / spanne) * boden).toFloat()
        fun x(minute: Int) = breite * (minute / 1440f)

        // Alle sechs Stunden ein Strich, beschriftet mit der Uhrzeit, die
        // dort wirklich war.
        listOf(360, 720, 1080).forEach { versatz ->
            val sx = x(versatz)
            leinwand.drawLine(sx, 0f, sx, boden, gitter)
            schrift.textAlign = Paint.Align.CENTER
            val stunde = ((beginnMinute + versatz) / 60) % 24
            leinwand.drawText(
                String.format("%02d", stunde), sx,
                height - context.dp(2f).toFloat(), schrift,
            )
        }

        val punktgroesse = context.dp(1.7f).toFloat()
        frueher.forEach { p ->
            leinwand.drawCircle(x(p.minute), y(p.wert), punktgroesse * 0.8f, schatten)
        }
        punkte.forEach { p ->
            leinwand.drawCircle(x(p.minute), y(p.wert), punktgroesse, tupfen)
        }

        // Die Linie folgt den frueheren Tagen, wenn es welche gibt - sonst dem
        // heutigen.
        val grundlage = if (frueher.isNotEmpty()) frueher.sortedBy { it.minute } else punkte
        val faden = Path()
        var erster = true
        var minute = grundlage.first().minute
        while (minute <= grundlage.last().minute) {
            val imFenster = grundlage.filter { it.minute >= minute - fenster && it.minute <= minute + fenster }
            // WENIGER ALS DREI PUNKTE SIND KEIN TREND. Eine Linie ueber eine
            // Luecke hinweg behauptete Messungen, die es nicht gibt.
            if (imFenster.size >= 3) {
                val my = y(median(imFenster.map { it.wert }))
                val mx = x(minute)
                if (erster) { faden.moveTo(mx, my); erster = false } else faden.lineTo(mx, my)
            } else {
                erster = true
            }
            minute += 15
        }
        leinwand.drawPath(faden, linie)

        schrift.textAlign = Paint.Align.LEFT
        leinwand.drawText(
            Zahlen.ganz(hoechster) ?: "", breite + context.dp(4f).toFloat(),
            y(hoechster) + schrift.textSize / 3, schrift
        )

        if (ruhe != null) {
            val ry = y(ruhe)
            leinwand.drawLine(0f, ry, breite, ry, ruhestift)
            leinwand.drawText(
                Zahlen.ganz(ruhe) ?: "", breite + context.dp(4f).toFloat(),
                ry + schrift.textSize / 3, schrift
            )
        }
    }

    private fun median(werte: List<Double>): Double {
        val sortiert = werte.sorted()
        val m = sortiert.size / 2
        return if (sortiert.size % 2 == 1) sortiert[m]
               else (sortiert[m - 1] + sortiert[m]) / 2
    }
}

fun Context.pulsbild(
    punkte: List<Gesundheit.Punkt>,
    ruhe: Double?,
    frueher: List<Gesundheit.Punkt> = emptyList(),
    beginnMinute: Int = 0,
): View =
    PulsView(this, punkte, ruhe, beginnMinute, frueher).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(124f)
        ).apply { topMargin = dp(10f) }
    }

// --- Streubild --------------------------------------------------------------

/**
 * Zwei Groessen gegeneinander: eine Wolke mit einer Geraden hindurch.
 *
 * DIE ACHSEN BEGINNEN NICHT BEI NULL. Bei einem Ruhepuls zwischen 48 und 62
 * waere die untere Haelfte des Bildes leer, und die Punkte klebten in einer
 * Linie zusammen - man saehe nichts. Die Randbeschriftung nennt deshalb die
 * tatsaechlichen Grenzen; ohne sie waere der Ausschnitt eine stille Luege.
 */
class StreuView(
    ctx: Context,
    private val bild: Auswertung.Zusammenhang,
    private val formX: (Double) -> String,
    private val formY: (Double) -> String,
) : View(ctx) {

    private val tupfen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ctx.farbe(R.color.akzent)
        alpha = 150
    }
    private val gerade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2f).toFloat()
        color = ctx.farbe(R.color.akzent)
    }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics
        )
        color = ctx.farbe(R.color.schrift_zart)
    }
    private val rahmen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f).toFloat()
        color = ctx.farbe(R.color.linie)
    }

    override fun onDraw(leinwand: Canvas) {
        val punkte = bild.punkte
        if (punkte.size < 3) {
            schrift.textAlign = Paint.Align.LEFT
            leinwand.drawText("Noch zu wenige gemeinsame Tage", 0f, height / 2f, schrift)
            return
        }

        val fuss = context.dp(14f).toFloat()
        val links = context.dp(30f).toFloat()
        val boden = height - fuss
        val breite = width.toFloat()

        val xVon = punkte.minOf { it.first }
        val xBis = punkte.maxOf { it.first }
        val yVon = punkte.minOf { it.second }
        val yBis = punkte.maxOf { it.second }
        val xSpanne = maxOf(xBis - xVon, 1e-9)
        val ySpanne = maxOf(yBis - yVon, 1e-9)
        val luft = 0.06f

        fun x(w: Double) = links + (breite - links) *
            (luft + (1 - 2 * luft) * ((w - xVon) / xSpanne).toFloat())
        fun y(w: Double) = boden * (1f - (luft + (1 - 2 * luft) *
            ((w - yVon) / ySpanne).toFloat()))

        leinwand.drawLine(links, boden, breite, boden, rahmen)
        leinwand.drawLine(links, 0f, links, boden, rahmen)

        val gross = context.dp(2.6f).toFloat()
        punkte.forEach { (a, b) -> leinwand.drawCircle(x(a), y(b), gross, tupfen) }

        // Die Gerade nur ueber den gemessenen Bereich. Darueber hinaus
        // verlaengert waere sie eine Vorhersage, und dafuer taugt sie nicht.
        if (bild.belastbar) {
            leinwand.drawLine(
                x(xVon), y(bild.achse + bild.steigung * xVon),
                x(xBis), y(bild.achse + bild.steigung * xBis),
                gerade,
            )
        }

        schrift.textAlign = Paint.Align.LEFT
        leinwand.drawText(formY(yBis), 0f, schrift.textSize, schrift)
        leinwand.drawText(formY(yVon), 0f, boden - context.dp(1f).toFloat(), schrift)
        leinwand.drawText(
            formX(xVon), links + context.dp(2f), (height - context.dp(2f)).toFloat(), schrift
        )
        schrift.textAlign = Paint.Align.RIGHT
        leinwand.drawText(formX(xBis), breite, (height - context.dp(2f)).toFloat(), schrift)
    }
}

fun Context.streubild(
    bild: Auswertung.Zusammenhang,
    formX: (Double) -> String,
    formY: (Double) -> String,
): View = StreuView(this, bild, formX, formY).apply {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(150f)
    ).apply { topMargin = dp(10f) }
}

// --- Tagesprofil -------------------------------------------------------------

/**
 * Was ueber den Tag verteilt geschieht - in Stufen, nicht als Linie.
 *
 * SCHRITTE SIND KEINE KURVE. Zwischen zwei Messungen liegt bei einem Puls ein
 * Verlauf, bei Schritten eine Summe; sie zu verbinden hiesse, zwischen zehn
 * und halb elf etwas zu behaupten. Deshalb Balken, eine halbe Stunde breit.
 *
 * Das typische Profil steht BLASS DAHINTER, der heutige Tag davor. So sieht
 * man auf einen Blick, ob die Bewegung fehlt oder nur noch nicht da war.
 */
class TagesprofilView(
    ctx: Context,
    private val heute: List<Gesundheit.Punkt>,
    private val typisch: List<Gesundheit.Punkt>,
    private val stufe: Int,
    private val beginnMinute: Int,
) : View(ctx) {

    private val stift = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gitter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f).toFloat()
        color = ctx.farbe(R.color.linie)
    }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics
        )
        color = ctx.farbe(R.color.schrift_zart)
    }

    override fun onDraw(leinwand: Canvas) {
        val alle = heute + typisch
        if (alle.isEmpty()) {
            schrift.textAlign = Paint.Align.LEFT
            leinwand.drawText("Noch keine Schritte gemessen", 0f, height / 2f, schrift)
            return
        }

        val fuss = context.dp(14f).toFloat()
        val boden = height - fuss
        val breite = width.toFloat()
        val spitze = maxOf(alle.maxOf { it.wert }, 1.0)
        val fach = breite * (stufe / 1440f)
        val balken = maxOf(fach - context.dp(1f), context.dp(2f).toFloat())

        fun x(minute: Int): Float {
            // Rechnet die Uhrzeit auf die Stelle im Bild um - das Bild faengt
            // an der Tagesgrenze an, nicht um Mitternacht.
            val versatz = ((minute - beginnMinute) + 1440) % 1440
            return breite * (versatz / 1440f)
        }

        listOf(360, 720, 1080).forEach { versatz ->
            val sx = breite * (versatz / 1440f)
            leinwand.drawLine(sx, 0f, sx, boden, gitter)
            schrift.textAlign = Paint.Align.CENTER
            val stunde = ((beginnMinute + versatz) / 60) % 24
            leinwand.drawText(
                String.format("%02d", stunde), sx,
                height - context.dp(2f).toFloat(), schrift,
            )
        }

        typisch.forEach { p ->
            stift.color = context.farbe(R.color.schrift_zart)
            stift.alpha = 60
            val h = (boden * (p.wert / spitze)).toFloat()
            leinwand.drawRect(x(p.minute), boden - h, x(p.minute) + balken, boden, stift)
        }
        stift.alpha = 255
        heute.forEach { p ->
            stift.color = context.farbe(R.color.akzent)
            val h = (boden * (p.wert / spitze)).toFloat()
            leinwand.drawRect(x(p.minute), boden - h, x(p.minute) + balken, boden, stift)
        }
    }
}

fun Context.tagesprofil(
    heute: List<Gesundheit.Punkt>,
    typisch: List<Gesundheit.Punkt>,
    stufe: Int,
    beginnMinute: Int,
): View = TagesprofilView(this, heute, typisch, stufe, beginnMinute).apply {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(104f)
    ).apply { topMargin = dp(10f) }
}
