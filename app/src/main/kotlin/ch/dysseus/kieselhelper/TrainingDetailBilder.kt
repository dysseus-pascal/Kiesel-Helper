package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Die Bilder der Trainings-Detailseite: Zonen, Verlaeufe ueber die Strecke,
 * die Karte mit dem Tempo als Farbe.
 *
 * DIE FARBEN DER ZONEN SIND DIE DER UHR: wer auf dem Schirm der Uhr Zone 3
 * gruen-gelb gesehen hat, findet sie hier wieder.
 */
object Zonenfarben {
    val FARBEN = intArrayOf(
        Color.rgb(0x9E, 0x9E, 0x9E),   // unter Zone 1
        Color.rgb(0x55, 0xAA, 0xFF),   // 1 PictonBlue
        Color.rgb(0x00, 0xAA, 0x55),   // 2 JaegerGreen
        Color.rgb(0xAA, 0xAA, 0x00),   // 3 Limerick
        Color.rgb(0xFF, 0xAA, 0x00),   // 4 ChromeYellow
        Color.rgb(0xFF, 0x00, 0x55),   // 5 Folly
    )
    val NAMEN = arrayOf("darunter", "Erholung", "Grundlage", "Ausdauer", "Schwelle", "Maximal")

    /** Langsam blau, schnell rot - ueber Cyan, Gruen und Gelb, damit man die Mitte sieht. */
    fun tempofarbe(anteil: Float): Int {
        val a = anteil.coerceIn(0f, 1f)
        return Color.HSVToColor(floatArrayOf(220f - 220f * a, 0.85f, 0.9f))
    }
}

// --- Die Zonen als Balken ------------------------------------------------------

class ZonenView(ctx: Context, private val sekunden: LongArray) : View(ctx) {
    private val pinsel = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(leinwand: Canvas) {
        val summe = sekunden.sum().coerceAtLeast(1)
        var x = 0f
        val r = context.dp(6f).toFloat()
        val pfad = Path().apply {
            addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, Path.Direction.CW)
        }
        leinwand.save()
        leinwand.clipPath(pfad)
        for (z in 0..5) {
            val b = width * sekunden[z].toFloat() / summe
            pinsel.color = Zonenfarben.FARBEN[z]
            leinwand.drawRect(x, 0f, x + b, height.toFloat(), pinsel)
            x += b
        }
        leinwand.restore()
    }
}

fun Context.zonenbalken(sekunden: LongArray): View = ZonenView(this, sekunden).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18f))
        .apply { topMargin = dp(10f); bottomMargin = dp(6f) }
}

/** Eine Zeile je Zone: Farbe, Name, Grenzen, Minuten, Anteil. */
fun Context.zonenliste(sekunden: LongArray, maxpuls: Int): LinearLayout {
    val s = spalte()
    val summe = sekunden.sum().coerceAtLeast(1)
    for (z in 5 downTo 0) {
        if (sekunden[z] == 0L) continue
        val von = if (z == 0) 0 else maxpuls * Trainingsanalyse.ZONEN_PROZENT[z - 1] / 100
        val bis = if (z == 0) maxpuls * 50 / 100 - 1 else if (z == 5) maxpuls else maxpuls * Trainingsanalyse.ZONEN_PROZENT[z] / 100 - 1
        val anteil = sekunden[z] * 100 / summe
        s.addView(reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(3f), 0, dp(3f))
            addView(View(this@zonenliste).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12f), dp(12f)).apply { rightMargin = dp(10f) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Zonenfarben.FARBEN[z])
                }
            })
            addView(fliesstext(if (z == 0) "unter Zone 1" else "Zone $z · " + Zonenfarben.NAMEN[z]).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(zart("$von–$bis  ").apply { minWidth = dp(64f) })
            addView(fliesstext((Zahlen.dauer(sekunden[z] / 60.0) ?: "0 min") + "  ").apply {
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(zart("$anteil %").apply { minWidth = dp(40f); gravity = android.view.Gravity.END })
        })
    }
    return s
}

// --- Ein Verlauf ueber die Strecke ---------------------------------------------

/**
 * Eine Linie ueber der Strecke, wahlweise mit Flaeche und Zonenbaendern.
 *
 * `baender` sind waagrechte Streifen (von, bis, Farbe) hinter der Linie -
 * beim Puls die Zonen. `farbeJeWert` faerbt die Linie nach dem Wert, beim
 * Tempo blau bis rot wie auf der Karte.
 */
class StreckenverlaufView(
    ctx: Context,
    private val punkte: List<Trainingsanalyse.Verlaufspunkt>,
    private val ton: Int,
    private val einheit: String,
    private val baender: List<Triple<Double, Double, Int>> = emptyList(),
    private val farbeJeWert: ((Double) -> Int)? = null,
    private val flaeche: Boolean = true,
    private val nachkomma: Int = 0,
) : View(ctx) {

    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2.5f).toFloat()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = ton
    }
    private val fuellung = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val raster = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f).toFloat()
        color = ctx.farbe(R.color.linie)
        pathEffect = DashPathEffect(floatArrayOf(ctx.dp(3f).toFloat(), ctx.dp(3f).toFloat()), 0f)
    }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics)
        color = ctx.farbe(R.color.schrift_zart)
    }

    override fun onDraw(leinwand: Canvas) {
        if (punkte.size < 2) return
        val links = context.dp(30f).toFloat()
        val fuss = context.dp(16f).toFloat()
        val kopf = context.dp(8f).toFloat()
        val boden = height - fuss
        val ende = punkte.last().meter.coerceAtLeast(1.0)
        var tief = punkte.minOf { it.wert }
        var hoch = punkte.maxOf { it.wert }
        val luft = ((hoch - tief) * 0.1).coerceAtLeast(1.0)
        tief -= luft; hoch += luft
        val spanne = (hoch - tief).coerceAtLeast(0.1)

        fun x(m: Double) = links + (width - links) * (m / ende).toFloat()
        fun y(w: Double) = kopf + (boden - kopf) * (1f - ((w - tief) / spanne).toFloat())

        // Baender hinter allem
        baender.forEach { (von, bis, farbe) ->
            val yo = y(bis.coerceAtMost(hoch)).coerceAtLeast(kopf)
            val yu = y(von.coerceAtLeast(tief)).coerceAtMost(boden)
            if (yu > yo) {
                band.color = farbe and 0x00FFFFFF or 0x22000000
                leinwand.drawRect(links, yo, width.toFloat(), yu, band)
            }
        }

        // Drei Rasterlinien mit Wert
        schrift.textAlign = Paint.Align.RIGHT
        for (k in 0..2) {
            val w = tief + spanne * k / 2
            val yy = y(w)
            leinwand.drawLine(links, yy, width.toFloat(), yy, raster)
            leinwand.drawText(String.format("%.${nachkomma}f", w), links - context.dp(4f), yy + context.dp(3f), schrift)
        }

        val pfad = Path()
        punkte.forEachIndexed { i, p ->
            if (i == 0) pfad.moveTo(x(p.meter), y(p.wert)) else pfad.lineTo(x(p.meter), y(p.wert))
        }
        if (flaeche) {
            val f = Path(pfad).apply {
                lineTo(x(punkte.last().meter), boden)
                lineTo(x(punkte.first().meter), boden)
                close()
            }
            fuellung.shader = LinearGradient(
                0f, kopf, 0f, boden,
                ton and 0x00FFFFFF or 0x55000000, ton and 0x00FFFFFF,
                Shader.TileMode.CLAMP,
            )
            leinwand.drawPath(f, fuellung)
        }
        val faerbe = farbeJeWert
        if (faerbe == null) {
            leinwand.drawPath(pfad, linie)
        } else {
            // Stueck fuer Stueck in der Farbe des Werts
            for (i in 1 until punkte.size) {
                linie.color = faerbe(punkte[i].wert)
                leinwand.drawLine(x(punkte[i - 1].meter), y(punkte[i - 1].wert), x(punkte[i].meter), y(punkte[i].wert), linie)
            }
            linie.color = ton
        }

        // Die Achse: Kilometer
        val unten = height - context.dp(3f).toFloat()
        val km = ende / 1000
        val schritt = when {
            km > 40 -> 10; km > 20 -> 5; km > 8 -> 2; km > 3 -> 1; else -> 0
        }
        schrift.textAlign = Paint.Align.CENTER
        if (schritt > 0) {
            var k = schritt
            while (k < km) {
                leinwand.drawText("$k", x(k * 1000.0), unten, schrift)
                k += schritt
            }
        }
        schrift.textAlign = Paint.Align.RIGHT
        leinwand.drawText(Zahlen.eine(km) + " km", width.toFloat(), unten, schrift)
        schrift.textAlign = Paint.Align.LEFT
        leinwand.drawText(einheit, links + context.dp(4f), kopf + context.dp(10f), schrift)
    }
}

fun Context.streckenverlauf(
    punkte: List<Trainingsanalyse.Verlaufspunkt>,
    ton: Int,
    einheit: String,
    baender: List<Triple<Double, Double, Int>> = emptyList(),
    farbeJeWert: ((Double) -> Int)? = null,
    flaeche: Boolean = true,
    nachkomma: Int = 0,
    hoehe: Float = 160f,
): View = StreckenverlaufView(this, punkte, ton, einheit, baender, farbeJeWert, flaeche, nachkomma).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(hoehe))
        .apply { topMargin = dp(8f) }
}

// --- Die Karte mit dem Tempo als Farbe -----------------------------------------

/**
 * Die Strecke auf der Karte, gefaerbt nach Tempo: langsam blau, schnell rot.
 *
 * NICHT JEDES STUECK EINE EIGENE LINIE. Tausend Polylines machen die Karte
 * zaeh; aufeinander folgende Punkte mit derselben Farbstufe (zwoelf Stufen)
 * werden zu einer Linie zusammengefasst - ein paar Dutzend statt Tausend.
 */
fun Context.tempokarte(strecke: List<Trainingsanalyse.Streckenpunkt>, karten: MutableList<MapView>): MapView {
    Configuration.getInstance().userAgentValue = packageName
    Configuration.getInstance().osmdroidBasePath = cacheDir
    Configuration.getInstance().osmdroidTileCache = java.io.File(cacheDir, "kacheln")

    val ansicht = MapView(this)
    ansicht.setTileSource(TileSourceFactory.MAPNIK)
    ansicht.setMultiTouchControls(true)
    ansicht.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300f))
        .apply { topMargin = dp(10f) }

    val (langsam, schnell) = Trainingsanalyse.tempoSpanne(strecke)
    fun stufe(t: Double) = (((t - langsam) / (schnell - langsam)).coerceIn(0.0, 1.0) * 11).toInt()

    // Ein weisser Saum darunter, damit die bunte Linie auf jeder Karte steht.
    val saum = Polyline(ansicht).apply {
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = dp(8f).toFloat()
        setPoints(strecke.map { GeoPoint(it.lat, it.lon) })
    }
    ansicht.overlays.add(saum)

    var i = 0
    while (i < strecke.size - 1) {
        val s = stufe(strecke[i].tempo)
        var j = i + 1
        while (j < strecke.size - 1 && stufe(strecke[j].tempo) == s) j++
        val stueck = strecke.subList(i, j + 1).map { GeoPoint(it.lat, it.lon) }
        ansicht.overlays.add(Polyline(ansicht).apply {
            outlinePaint.color = Zonenfarben.tempofarbe(s / 11f)
            outlinePaint.strokeWidth = dp(5f).toFloat()
            setPoints(stueck)
        })
        i = j
    }

    fun marke(p: Trainingsanalyse.Streckenpunkt, text: String, ton: Int) {
        val m = Marker(ansicht)
        m.position = GeoPoint(p.lat, p.lon)
        m.title = text
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        m.icon = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(ton)
            setStroke(dp(2f), Color.WHITE)
            setSize(dp(14f), dp(14f))
        }
        ansicht.overlays.add(m)
    }
    marke(strecke.first(), "Start", Color.rgb(0x2E, 0x7D, 0x32))
    marke(strecke.last(), "Ende", Color.rgb(0xC6, 0x28, 0x28))

    ansicht.post {
        ansicht.zoomToBoundingBox(BoundingBox.fromGeoPoints(saum.actualPoints).increaseByScale(1.25f), false)
    }
    karten.add(ansicht)
    return ansicht
}

/** Die Legende zur Tempokarte: der Farbverlauf mit den Werten an den Enden. */
class TempolegendeView(ctx: Context) : View(ctx) {
    private val pinsel = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(leinwand: Canvas) {
        val stufen = 24
        val b = width.toFloat() / stufen
        for (k in 0 until stufen) {
            pinsel.color = Zonenfarben.tempofarbe(k / (stufen - 1f))
            leinwand.drawRect(k * b, 0f, (k + 1) * b + 1, height.toFloat(), pinsel)
        }
    }
}

fun Context.tempolegende(langsam: String, schnell: String): LinearLayout = spalte().apply {
    addView(TempolegendeView(this@tempolegende).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8f))
            .apply { topMargin = dp(8f) }
    })
    addView(reihe().apply {
        addView(zart(langsam).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(zart(schnell).apply { gravity = android.view.Gravity.END })
    })
}
