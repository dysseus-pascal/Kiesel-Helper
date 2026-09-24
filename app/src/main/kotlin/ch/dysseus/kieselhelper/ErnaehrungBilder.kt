package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.ZoneId

/**
 * Die Bilder des Ernaehrungs-Reiters.
 *
 * WAS MAN SELBST TUT, SOLL MAN SEHEN. Ein Glas Wasser, eine Tablette, ein
 * Espresso sind Entscheidungen - und eine Zahl wie "1200 ml" sagt nicht, ob
 * das der halbe Weg ist oder fast geschafft. Ein Glas, das sich fuellt, sagt
 * es ohne Lesen.
 */

private fun Context.sp(wert: Float) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, wert, resources.displayMetrics)

/** Eine Farbe mit anderer Deckkraft, 0 bis 255. */
private fun Int.mitAlpha(a: Int): Int = this and 0x00FFFFFF or (a shl 24)

/** Ein Stoff als Zeichen im blassen Kreis seiner Farbe - wie die Sportarten. */
fun Context.stoffzeichen(zeichen: String, ton: Int): android.widget.TextView =
    android.widget.TextView(this).apply {
        text = zeichen
        gravity = android.view.Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(ton.mitAlpha(0x33))
        }
        layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginEnd = dp(14f) }
    }

/**
 * Ein Haken: voll, wenn genommen, sonst ein leerer Ring.
 *
 * Der leere Ring ist die Aufforderung - er sieht aus wie etwas, das noch
 * gefuellt werden will.
 */
fun Context.haken(genommen: Boolean, ton: Int): android.widget.TextView =
    android.widget.TextView(this).apply {
        text = if (genommen) "✓" else ""
        gravity = android.view.Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(android.graphics.Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            if (genommen) setColor(ton) else setStroke(dp(2f), ton)
        }
        layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { marginEnd = dp(10f) }
    }

// --- Das Glas ----------------------------------------------------------------

/**
 * Ein Glas, gefuellt bis zum heutigen Stand.
 *
 * DIE OBERFLAECHE IST EINE WELLE und keine Linie: es soll nach Wasser
 * aussehen, nicht nach einem Fortschrittsbalken, der hochkant steht.
 * Laeuft es ueber das Ziel, bleibt das Glas voll - mehr zeigt die Zahl.
 */
class GlasView(
    ctx: Context,
    private val anteil: Float,
    private val ton: Int,
    private val mitText: String? = null,
) : View(ctx) {

    private val rand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2f).toFloat()
        strokeJoin = Paint.Join.ROUND
        color = ctx.farbe(R.color.schrift_zart).mitAlpha(140)
    }
    private val wasser = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glanz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE.mitAlpha(70)
    }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = ctx.sp(15f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = ctx.farbe(R.color.schrift)
    }

    override fun onDraw(leinwand: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val oben = h * 0.04f
        val unten = h * 0.97f
        // Ein Becher, oben breiter als unten.
        val schraeg = w * 0.12f
        val form = Path().apply {
            moveTo(w * 0.06f, oben)
            lineTo(w * 0.06f + schraeg, unten)
            lineTo(w * 0.94f - schraeg, unten)
            lineTo(w * 0.94f, oben)
        }

        val a = anteil.coerceIn(0f, 1f)
        if (a > 0f) {
            val pegel = unten - (unten - oben) * a
            val welle = h * 0.035f
            val wasserform = Path().apply {
                moveTo(0f, pegel)
                val schritte = 24
                for (i in 0..schritte) {
                    val x = w * i / schritte
                    val y = pegel + welle * Math.sin(i / schritte.toDouble() * Math.PI * 3).toFloat()
                    lineTo(x, y)
                }
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            wasser.shader = LinearGradient(
                0f, pegel, 0f, unten, ton.mitAlpha(150), ton, Shader.TileMode.CLAMP
            )
            leinwand.save()
            leinwand.clipPath(Path(form).apply { close() })
            leinwand.drawPath(wasserform, wasser)
            // Ein heller Streifen links - der Glanz, an dem man Glas erkennt.
            leinwand.drawRoundRect(
                RectF(w * 0.2f, pegel + welle * 2, w * 0.26f, unten - h * 0.06f),
                w * 0.03f, w * 0.03f, glanz,
            )
            leinwand.restore()
        }
        leinwand.drawPath(form, rand)

        mitText?.let {
            leinwand.drawText(it, w / 2, h * 0.58f, schrift)
        }
    }
}

fun Context.glas(anteil: Float, ton: Int, breite: Float, hoehe: Float, text: String? = null): View =
    GlasView(this, anteil, ton, text).apply {
        layoutParams = LinearLayout.LayoutParams(dp(breite), dp(hoehe))
    }

/**
 * Die Glaeser des Tages als Reihe: volle, ein angefangenes, leere.
 *
 * DAS ZIEL IN GLAESERN, NICHT IN MILLILITERN. "Noch drei" ist eine Auskunft,
 * nach der man handelt; "noch 900 ml" muss man erst umrechnen.
 */
fun Context.glaeserreihe(ml: Double, glasMl: Double, ziel: Double, ton: Int): LinearLayout {
    val anzahl = Math.ceil(maxOf(ziel, ml) / glasMl).toInt().coerceIn(1, 14)
    return reihe().apply {
        setPadding(0, dp(12f), 0, 0)
        for (i in 0 until anzahl) {
            val fuell = ((ml - i * glasMl) / glasMl).coerceIn(0.0, 1.0).toFloat()
            addView(glas(fuell, ton, 20f, 28f).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(5f)
                // Was ueber das Ziel hinausgeht, steht leiser da.
                if (i * glasMl >= ziel) alpha = 0.55f
            })
        }
    }
}

// --- Wann getrunken wurde ----------------------------------------------------

/**
 * Der Tag als Leiste, ein Tropfen je Glas.
 *
 * DIE SUMME VERSCHWEIGT DEN NACHMITTAG. 1,5 Liter bis zehn Uhr und dann
 * nichts mehr sind ein anderer Tag als ueber den Tag verteilt - und nur die
 * Zeitpunkte zeigen den Unterschied.
 */
class TrinkleisteView(
    ctx: Context,
    private val glaeser: List<Gesundheit.Punkt>,
    private val jetzt: Int,
    private val ton: Int,
) : View(ctx) {

    private val von = 6 * 60
    private val bis = 24 * 60

    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = ctx.dp(3f).toFloat()
        strokeCap = Paint.Cap.ROUND
        color = ctx.farbe(R.color.linie)
    }
    private val vergangen = Paint(linie).apply { color = ton.mitAlpha(90) }
    private val tropfen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ton }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = ctx.sp(10f)
        color = ctx.farbe(R.color.schrift_zart)
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(leinwand: Canvas) {
        val rand = context.dp(10f).toFloat()
        val mitte = height * 0.42f
        fun x(min: Int) = rand + (width - 2 * rand) * ((min.coerceIn(von, bis) - von) / (bis - von).toFloat())

        leinwand.drawLine(x(von), mitte, x(bis), mitte, linie)
        leinwand.drawLine(x(von), mitte, x(jetzt), mitte, vergangen)

        glaeser.forEach { g ->
            // Die Flaeche waechst mit der Menge; ein Schluck ist klein, ein
            // halber Liter gross.
            val r = context.dp(4f) + context.dp(5f) * Math.sqrt((g.wert / 500.0).coerceIn(0.0, 1.0)).toFloat()
            val cx = x(g.minute)
            val pfad = Path().apply {
                // Ein Tropfen: Kreis unten, Spitze oben.
                moveTo(cx, mitte - r * 2.1f)
                cubicTo(cx + r * 0.3f, mitte - r * 1.2f, cx + r, mitte - r * 0.6f, cx + r, mitte)
                arcTo(RectF(cx - r, mitte - r, cx + r, mitte + r), 0f, 180f)
                cubicTo(cx - r, mitte - r * 0.6f, cx - r * 0.3f, mitte - r * 1.2f, cx, mitte - r * 2.1f)
                close()
            }
            leinwand.drawPath(pfad, tropfen)
        }

        listOf(6, 9, 12, 15, 18, 21, 24).forEach { stunde ->
            leinwand.drawText(stunde.toString(), x(stunde * 60), height - context.dp(3f).toFloat(), schrift)
        }
    }
}

fun Context.trinkleiste(glaeser: List<Gesundheit.Punkt>, ton: Int): View {
    val jetzt = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    return TrinkleisteView(this, glaeser, jetzt, ton).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56f)
        ).apply { topMargin = dp(10f) }
    }
}

// --- Der Ring der Praeparate -------------------------------------------------

/**
 * Wie viel von dem, was heute ansteht, genommen ist - als Ring.
 *
 * EIN RING, WEIL ES EIN ABSCHLUSS IST. Ein Tag mit fuenf Praeparaten ist
 * erledigt oder nicht, und der Ring schliesst sich, wenn er es ist.
 */
class RingView(
    ctx: Context,
    private val genommen: Int,
    private val faellig: Int,
    private val ton: Int,
) : View(ctx) {

    private val grund = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(9f).toFloat()
        color = ctx.farbe(R.color.linie)
    }
    private val bogen = Paint(grund).apply {
        color = ton
        strokeCap = Paint.Cap.ROUND
    }
    private val gross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = ctx.sp(20f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = ctx.farbe(R.color.schrift)
    }
    private val klein = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = ctx.sp(11f)
        textAlign = Paint.Align.CENTER
        color = ctx.farbe(R.color.schrift_zart)
    }

    override fun onDraw(leinwand: Canvas) {
        val d = minOf(width, height).toFloat() - grund.strokeWidth
        val rechteck = RectF(
            (width - d) / 2, (height - d) / 2, (width + d) / 2, (height + d) / 2
        )
        leinwand.drawArc(rechteck, 0f, 360f, false, grund)
        if (faellig > 0 && genommen > 0) {
            val winkel = 360f * (genommen.toFloat() / faellig).coerceIn(0f, 1f)
            leinwand.drawArc(rechteck, -90f, winkel, false, bogen)
        }
        leinwand.drawText("$genommen/$faellig", width / 2f, height / 2f + gross.textSize * 0.2f, gross)
        leinwand.drawText(
            context.getString(if (faellig in 1..genommen) R.string.eb_fertig else R.string.eb_genommen),
            width / 2f, height / 2f + gross.textSize * 0.2f + klein.textSize * 1.4f, klein,
        )
    }
}

fun Context.praeparatring(genommen: Int, faellig: Int, ton: Int): View =
    RingView(this, genommen, faellig, ton).apply {
        layoutParams = LinearLayout.LayoutParams(dp(96f), dp(96f)).apply { marginEnd = dp(16f) }
    }

// --- Das Koffein ueber den Tag -----------------------------------------------

/**
 * Wie viel Koffein gerade wirkt - und wie viel noch zur Schlafenszeit.
 *
 * DIE KURVE STATT DER SUMME. 240 mg am Tag sind harmlos, wenn das letzte um
 * zehn kam, und nicht, wenn es um fuenf kam. Der Koerper baut Koffein mit
 * einer Halbwertszeit von rund fuenf Stunden ab; jede Tasse ist deshalb ein
 * Sprung und danach ein langsames Abklingen, und die Summe der Kurven ist,
 * was im Blut ist.
 *
 * DIE FUENF STUNDEN SIND EIN MITTEL. Sie schwanken von Mensch zu Mensch
 * zwischen etwa drei und sieben; die Kurve zeigt die Form, keine Messung.
 */
class KoffeinView(
    ctx: Context,
    private val dosen: List<Gesundheit.Dosis>,
    private val bett: Int,
    private val ton: Int,
) : View(ctx) {

    private val von = 6 * 60
    private val bis = maxOf(24 * 60, bett + 60)
    private val mitternacht = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
    private val jetzt = java.time.Duration.between(mitternacht, java.time.Instant.now()).toMinutes().toInt()

    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2f).toFloat()
        strokeJoin = Paint.Join.ROUND
        color = ton
    }
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strich = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f).toFloat()
        color = ctx.farbe(R.color.schrift_zart)
        pathEffect = DashPathEffect(floatArrayOf(ctx.dp(3f).toFloat(), ctx.dp(3f).toFloat()), 0f)
    }
    private val tasse = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ton }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = ctx.sp(10f)
        color = ctx.farbe(R.color.schrift_zart)
    }
    private val fett = Paint(schrift).apply {
        color = ctx.farbe(R.color.schrift)
        typeface = Typeface.DEFAULT_BOLD
    }

    /** Was um diese Minute des Tages noch wirkt. */
    fun wirkt(minute: Int): Double = dosen.sumOf { d ->
        val ab = java.time.Duration.between(d.zeit, mitternacht.plusSeconds(minute * 60L)).toMinutes()
        if (ab < 0) 0.0 else d.mg * Math.pow(0.5, ab / HALBWERT)
    }

    override fun onDraw(leinwand: Canvas) {
        val kopf = context.dp(18f).toFloat()
        val fuss = context.dp(16f).toFloat()
        val boden = height - fuss
        val werte = (von..bis step 5).map { it to wirkt(it) }
        val spitze = maxOf(werte.maxOf { it.second }, 100.0)
        fun x(m: Int) = width * ((m - von) / (bis - von).toFloat())
        fun y(v: Double) = (boden - (boden - kopf) * (v / spitze)).toFloat()

        val pfad = Path()
        werte.forEachIndexed { i, (m, v) -> if (i == 0) pfad.moveTo(x(m), y(v)) else pfad.lineTo(x(m), y(v)) }
        val unter = Path(pfad).apply {
            lineTo(x(bis), boden); lineTo(x(von), boden); close()
        }
        flaeche.shader = LinearGradient(0f, kopf, 0f, boden, ton.mitAlpha(110), ton.mitAlpha(10), Shader.TileMode.CLAMP)
        leinwand.drawPath(unter, flaeche)
        leinwand.drawPath(pfad, linie)

        // Jede Tasse ein Punkt unten an der Achse, dort, wo sie kam.
        dosen.forEach { d ->
            val m = java.time.Duration.between(mitternacht, d.zeit).toMinutes().toInt()
            if (m in von..bis) leinwand.drawCircle(x(m), boden, context.dp(4f).toFloat(), tasse)
        }

        // Jetzt, und die Schlafenszeit.
        if (jetzt in von..bis) {
            leinwand.drawLine(x(jetzt), kopf, x(jetzt), boden, strich)
            schrift.textAlign = Paint.Align.CENTER
            leinwand.drawText(context.getString(R.string.jetzt), x(jetzt), kopf - context.dp(5f), schrift)
        }
        val bx = x(bett)
        leinwand.drawLine(bx, kopf, bx, boden, strich)
        fett.textAlign = if (bx > width * 0.8f) Paint.Align.RIGHT else Paint.Align.CENTER
        leinwand.drawText("☾ " + Math.round(wirkt(bett)) + " mg", bx, kopf - context.dp(5f), fett)

        schrift.textAlign = Paint.Align.CENTER
        listOf(6, 9, 12, 15, 18, 21, 24).forEach { h ->
            if (h * 60 in von..bis) leinwand.drawText(h.toString(), x(h * 60), height - context.dp(3f).toFloat(), schrift)
        }
    }

    companion object {
        /** Halbwertszeit in Minuten. */
        const val HALBWERT = 300.0
    }
}

fun Context.koffeinkurve(dosen: List<Gesundheit.Dosis>, bett: Int, ton: Int): KoffeinView =
    KoffeinView(this, dosen, bett, ton).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(140f)
        ).apply { topMargin = dp(10f) }
    }
