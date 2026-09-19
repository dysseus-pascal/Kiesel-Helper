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
    }
}

/** Ein Saeulenbild, fertig eingehaengt. */
fun Context.saeulenbild(saeulen: List<Saeule>, ziel: Double? = null): View =
    SaeulenView(this, saeulen, ziel).apply {
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
    beschriftung: (Double) -> String = { Zahlen.ganz(it) ?: "" },
): View {
    val heute = LocalDate.now()
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
    )
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

// --- Pulsverlauf ------------------------------------------------------------

/**
 * Der Puls ueber den Tag.
 *
 * Der Tag steht IMMER ganz da, von null bis vierundzwanzig Uhr - eine Linie,
 * die sich auf die gemessene Spanne streckt, sieht um acht Uhr morgens aus wie
 * ein ganzer Tag. Die Stundenstriche bei 6, 12 und 18 Uhr sind das Gegenmittel.
 */
class PulsView(
    ctx: Context,
    private val punkte: List<Gesundheit.Punkt>,
    private val ruhe: Double?,
) : View(ctx) {

    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2f).toFloat()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = context.farbe(R.color.akzent)
    }
    private val fuellung = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.farbe(R.color.akzent)
        alpha = 40
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

        if (punkte.isEmpty()) {
            schrift.textAlign = Paint.Align.LEFT
            leinwand.drawText("Keine Pulsmessungen heute", 0f, height / 2f, schrift)
            return
        }

        val hoechster = punkte.maxOf { it.wert }
        val tiefster = minOf(punkte.minOf { it.wert }, ruhe ?: Double.MAX_VALUE)
        val oben = hoechster + 5
        val unten = maxOf(tiefster - 5, 0.0)
        val spanne = maxOf(oben - unten, 1.0)
        fun y(wert: Double) = (boden - ((wert - unten) / spanne) * boden).toFloat()
        fun x(minute: Int) = breite * (minute / 1440f)

        listOf(6, 12, 18).forEach { stunde ->
            val sx = x(stunde * 60)
            leinwand.drawLine(sx, 0f, sx, boden, gitter)
            schrift.textAlign = Paint.Align.CENTER
            leinwand.drawText("$stunde", sx, height - context.dp(2f).toFloat(), schrift)
        }

        val weg = Path()
        val flaeche = Path()
        punkte.forEachIndexed { i, p ->
            val px = x(p.minute)
            val py = y(p.wert)
            if (i == 0) { weg.moveTo(px, py); flaeche.moveTo(px, boden); flaeche.lineTo(px, py) }
            else { weg.lineTo(px, py); flaeche.lineTo(px, py) }
        }
        flaeche.lineTo(x(punkte.last().minute), boden)
        flaeche.close()
        leinwand.drawPath(flaeche, fuellung)
        leinwand.drawPath(weg, linie)

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
}

fun Context.pulsbild(punkte: List<Gesundheit.Punkt>, ruhe: Double?): View =
    PulsView(this, punkte, ruhe).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(110f)
        ).apply { topMargin = dp(10f) }
    }
