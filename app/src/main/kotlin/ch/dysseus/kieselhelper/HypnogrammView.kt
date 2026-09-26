package ch.dysseus.kieselhelper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt

/**
 * Der Verlauf einer Nacht als Bild.
 *
 * OBEN DIE PHASEN, darunter auf DERSELBEN Zeitachse, was die Uhr gemessen hat:
 * Bewegung, Puls, HRV, SpO2. Erst nebeneinander erklaeren sie sich - eine
 * Wachphase um halb zwei steht genau ueber dem Ausschlag der Bewegung, der
 * sie ausgeloest hat, und ueber dem Puls, der dabei stieg.
 *
 * KLEIN (Karte Gesundheit) nur die Phasen und die Stunden. GROSS (Seite
 * "Nacht") mit Beschriftung links, den Spuren und einem Zeiger: Antippen
 * oder Ziehen meldet die Zeit an [beiZeit], und die Seite schreibt darunter,
 * was um diese Minute war.
 */
@SuppressLint("ViewConstructor")
class HypnogrammView(
    ctx: Context,
    private val h: Hypnogramm,
    private val bild: Gesundheit.NachtBild?,
    private val gross: Boolean,
    private val beiZeit: ((Instant) -> Unit)? = null,
) : View(ctx) {

    private val zone = ZoneId.systemDefault()
    private val links = if (gross) ctx.dp(46f).toFloat() else 0f
    private val bahnenHoehe = ctx.dp(if (gross) 150f else 62f).toFloat()
    private val spurHoehe = ctx.dp(40f).toFloat()
    private val spurLuft = ctx.dp(12f).toFloat()
    private val fuss = ctx.dp(16f).toFloat()

    /** Welche Spuren es gibt - nur die mit Daten. */
    private val spuren: List<String> = if (!gross || bild == null) emptyList() else buildList {
        if (bild.bewegung.isNotEmpty()) add(BEWEGUNG)
        if (bild.puls.isNotEmpty()) add(PULS)
        if (bild.hrvFenster.isNotEmpty()) add(HRV)
        if (bild.spo2.isNotEmpty()) add(SPO2)
    }

    val hoehe: Int
        get() = (bahnenHoehe + spuren.size * (spurHoehe + spurLuft) + fuss).toInt()

    private var zeiger: Instant? = null

    private val fuellung = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strich = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f).toFloat()
    }
    private val gestrichelt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f).toFloat()
        color = ctx.farbe(R.color.schrift_zart)
        pathEffect = DashPathEffect(floatArrayOf(ctx.dp(3f).toFloat(), ctx.dp(3f).toFloat()), 0f)
    }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics)
        color = ctx.farbe(R.color.schrift_zart)
    }

    private val farben = intArrayOf(
        ctx.farbe(R.color.phase_wach),
        ctx.farbe(R.color.phase_rem),
        ctx.farbe(R.color.phase_leicht),
        ctx.farbe(R.color.phase_tief),
    )

    private val dauerS: Float get() = maxOf(60f, (h.bis.epochSecond - h.von.epochSecond).toFloat())
    private fun x(t: Instant): Float =
        links + (width - links) * ((t.epochSecond - h.von.epochSecond) / dauerS).coerceIn(0f, 1f)

    override fun onDraw(leinwand: Canvas) {
        zeichneStunden(leinwand)
        zeichneBahnen(leinwand)
        var oben = bahnenHoehe + spurLuft
        spuren.forEach { spur ->
            zeichneSpur(leinwand, spur, oben)
            oben += spurHoehe + spurLuft
        }
        zeiger?.let {
            strich.color = context.farbe(R.color.schrift)
            strich.alpha = 150
            leinwand.drawLine(x(it), 0f, x(it), height - fuss, strich)
            strich.alpha = 255
        }
    }

    // Ein Strich je volle Stunde, beschriftet mit der Stunde. Bei mehr als
    // zehn Stunden jede zweite, sonst liefen die Zahlen ineinander.
    private fun zeichneStunden(leinwand: Canvas) {
        var t = h.von.atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
        val stunden = (h.bis.epochSecond - h.von.epochSecond) / 3600
        val schritt = if (stunden > 10) 2 else 1
        strich.color = context.farbe(R.color.linie)
        schrift.textAlign = Paint.Align.CENTER
        while (t.toInstant().isBefore(h.bis)) {
            val sx = x(t.toInstant())
            if (t.hour % schritt == 0) {
                leinwand.drawLine(sx, 0f, sx, height - fuss, strich)
                leinwand.drawText(String.format("%02d", t.hour), sx, height - context.dp(3f).toFloat(), schrift)
            }
            t = t.plusHours(1)
        }
    }

    private fun zeichneBahnen(leinwand: Canvas) {
        val bh = bahnenHoehe / 4
        if (gross) {
            schrift.textAlign = Paint.Align.LEFT
            listOf(R.string.phase_wach, R.string.phase_rem, R.string.phase_leicht, R.string.phase_tief)
                .forEachIndexed { i, id ->
                    leinwand.drawText(context.getString(id), 0f, i * bh + bh / 2 + schrift.textSize / 3, schrift)
                }
        }
        // Die Bahnen blass im Hintergrund, damit man die Hoehe ablesen kann.
        fuellung.color = context.farbe(R.color.linie)
        fuellung.alpha = 110
        for (i in 0 until 4) {
            leinwand.drawRoundRect(RectF(links, i * bh + bh * 0.2f, width.toFloat(), i * bh + bh * 0.8f),
                bh * 0.3f, bh * 0.3f, fuellung)
        }
        fuellung.alpha = 255
        // Senkrechte Verbindungen zwischen den Stufen - so liest sich das Bild
        // als Verlauf und nicht als lose Kloetze.
        strich.color = context.farbe(R.color.schrift_zart)
        strich.alpha = 110
        for (k in 1 until h.stufen.size) {
            val a = h.stufen[k - 1]
            val b = h.stufen[k]
            if (a.bis != b.von) continue
            leinwand.drawLine(x(b.von), a.bahn * bh + bh / 2, x(b.von), b.bahn * bh + bh / 2, strich)
        }
        strich.alpha = 255
        val ecke = minOf(context.dp(4f).toFloat(), bh * 0.3f)
        h.stufen.forEach { s ->
            fuellung.color = farben[s.bahn]
            val x1 = x(s.von)
            val x2 = maxOf(x(s.bis), x1 + context.dp(1.5f))
            leinwand.drawRoundRect(RectF(x1, s.bahn * bh + bh * 0.12f, x2, s.bahn * bh + bh * 0.88f), ecke, ecke, fuellung)
        }
    }

    private fun zeichneSpur(leinwand: Canvas, spur: String, oben: Float) {
        val b = bild ?: return
        val unten = oben + spurHoehe
        schrift.textAlign = Paint.Align.LEFT
        leinwand.drawText(name(spur), 0f, oben + spurHoehe / 2 + schrift.textSize / 3, schrift)
        when (spur) {
            BEWEGUNG -> {
                // Wurzel, damit ein Umdrehen neben einem Aufstehen sichtbar
                // bleibt - linear verschwaende es als Strich am Boden.
                val breit = maxOf(context.dp(1f).toFloat(), (width - links) / maxOf(1, b.bewegung.size))
                b.bewegung.forEach { (t, vmc) ->
                    if (vmc < 0) {
                        fuellung.color = context.farbe(R.color.phase_wach)
                        fuellung.alpha = 200
                        leinwand.drawRect(x(t), unten - context.dp(2f), x(t) + breit, unten, fuellung)
                    } else if (vmc > 0) {
                        fuellung.color = context.farbe(R.color.schrift_zart)
                        fuellung.alpha = 180
                        val hh = maxOf(context.dp(1.5f).toFloat(), spurHoehe * sqrt(minOf(vmc, 4032) / 4032f))
                        leinwand.drawRect(x(t), unten - hh, x(t) + breit, unten, fuellung)
                    }
                }
                fuellung.alpha = 255
            }
            PULS -> {
                val werte = b.puls.map { it.second }
                val tief = (minOf(werte.min(), b.ruhepuls?.toInt() ?: Int.MAX_VALUE) - 3).toFloat()
                val hoch = (werte.max() + 3).toFloat()
                fun y(p: Float) = unten - spurHoehe * (p - tief) / maxOf(1f, hoch - tief)
                b.ruhepuls?.let { r ->
                    val ry = y(r.toFloat())
                    leinwand.drawLine(links, ry, width.toFloat(), ry, gestrichelt)
                    schrift.textAlign = Paint.Align.RIGHT
                    leinwand.drawText(Zahlen.ganz(r) ?: "", width.toFloat(), ry - context.dp(2f), schrift)
                }
                fuellung.color = context.akzentfarbe()
                val r = context.dp(1.8f).toFloat()
                b.puls.forEach { (t, p) -> leinwand.drawCircle(x(t), y(p.toFloat()), r, fuellung) }
            }
            HRV -> linie(leinwand, b.hrvFenster.map { it.first to it.second.toFloat() }, oben, unten,
                context.farbe(R.color.phase_rem), "ms")
            SPO2 -> linie(leinwand, b.spo2.map { it.first to it.second.toFloat() }, oben, unten,
                context.farbe(R.color.phase_tief), "%")
        }
    }

    /** Eine Linie mit Punkten; oben rechts der hoechste Wert. */
    private fun linie(leinwand: Canvas, punkte: List<Pair<Instant, Float>>, oben: Float, unten: Float, farbe: Int, einheit: String) {
        if (punkte.isEmpty()) return
        val tief = punkte.minOf { it.second } - 2
        val hoch = punkte.maxOf { it.second } + 2
        fun y(w: Float) = unten - (unten - oben) * (w - tief) / maxOf(1f, hoch - tief)
        strich.color = farbe
        strich.strokeWidth = context.dp(1.6f).toFloat()
        val weg = Path()
        punkte.forEachIndexed { i, (t, w) -> if (i == 0) weg.moveTo(x(t), y(w)) else weg.lineTo(x(t), y(w)) }
        leinwand.drawPath(weg, strich)
        strich.strokeWidth = context.dp(1f).toFloat()
        fuellung.color = farbe
        punkte.forEach { (t, w) -> leinwand.drawCircle(x(t), y(w), context.dp(2.2f).toFloat(), fuellung) }
        schrift.textAlign = Paint.Align.RIGHT
        val hoechster = punkte.maxOf { it.second }
        leinwand.drawText("${Zahlen.ganz(hoechster.toDouble())} $einheit", width.toFloat(), oben + schrift.textSize, schrift)
    }

    private fun name(spur: String): String = context.getString(
        when (spur) {
            BEWEGUNG -> R.string.n_bewegung
            PULS -> R.string.puls
            HRV -> R.string.hrv
            else -> R.string.spo2
        }
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (beiZeit == null) return super.onTouchEvent(e)
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            // Die Seite soll beim Ziehen nicht mitscrollen.
            parent?.requestDisallowInterceptTouchEvent(true)
            val anteil = ((e.x - links) / maxOf(1f, width - links)).coerceIn(0f, 1f)
            val t = h.von.plusSeconds((anteil * dauerS).toLong())
            zeiger = t
            beiZeit.invoke(t)
            invalidate()
            return true
        }
        return super.onTouchEvent(e)
    }

    companion object {
        private const val BEWEGUNG = "bewegung"
        private const val PULS = "puls"
        private const val HRV = "hrv"
        private const val SPO2 = "spo2"
    }
}

/** Das Hypnogramm fuer eine Karte (klein) oder die Seite "Nacht" (gross). */
fun Context.hypnogrammbild(
    h: Hypnogramm,
    bild: Gesundheit.NachtBild? = null,
    beiZeit: ((Instant) -> Unit)? = null,
): View {
    val gross = bild != null
    val v = HypnogrammView(this, h, bild, gross, beiZeit)
    v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, v.hoehe)
        .apply { topMargin = dp(10f) }
    return v
}
