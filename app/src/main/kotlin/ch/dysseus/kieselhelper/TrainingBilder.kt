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
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.health.connect.client.records.ExerciseSessionRecord
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Die Bilder des Trainings-Reiters.
 *
 * DREI ZAHLEN WAREN ZU WENIG. "3 Trainings, 2:40 h" sagt, DASS man etwas
 * getan hat, aber nicht, wie es sich verteilt: ob die Woche drei Laeufe am
 * Stueck hatte oder jeden zweiten Tag etwas anderes. Das sieht man nur im
 * Bild - und jede Art hat dafuer ihre eigene Farbe, damit man es sieht,
 * ohne die Legende zu lesen.
 */
object Sportart {

    data class Art(val name: String, val zeichen: String, val farbe: Int)

    private val LAUF = Art("Laufen", "🏃", R.color.sport_lauf)
    private val BIKE = Art("Bike", "🚴", R.color.sport_bike)
    private val MTB = Art("Bike MTB", "🚵", R.color.sport_bike)
    private val WANDERN = Art("Wandern", "🥾", R.color.sport_wandern)
    private val KRAFT = Art("Kraft", "🏋️", R.color.sport_kraft)
    private val YOGA = Art("Yoga", "🧘", R.color.sport_yoga)
    private val SCHWIMMEN = Art("Schwimmen", "🏊", R.color.sport_schwimmen)

    /** Die Reihenfolge in Legenden - immer dieselbe, damit man sie lernt. */
    val ALLE = listOf(LAUF, BIKE, MTB, WANDERN, KRAFT, YOGA, SCHWIMMEN)

    /**
     * Die Art aus der Akte, nicht aus dem Titel.
     *
     * NUR MTB STEHT IM TITEL: die Akte kennt kein Mountainbike, Kieselsport
     * traegt es als Velo ein und schreibt den Unterschied in den Namen.
     */
    fun von(s: ExerciseSessionRecord): Art = when (s.exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> LAUF
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY ->
            if (s.title?.contains("MTB") == true) MTB else BIKE
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> WANDERN
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> KRAFT
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> YOGA
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> SCHWIMMEN
        else -> Art(s.title ?: "Training", "⏱", R.color.sport_anderes)
    }

    /** Arten, bei denen eine Strecke etwas aussagt - und das GPS mitlaeuft. */
    fun mitStrecke(s: ExerciseSessionRecord): Boolean = when (s.exerciseType) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> true
        else -> false
    }

    fun minuten(s: ExerciseSessionRecord): Long =
        Duration.between(s.startTime, s.endTime).toMinutes()
}

// --- Das Zeichen -------------------------------------------------------------

/**
 * Das Zeichen einer Art: ein Kreis in ihrer Farbe, darin das Symbol.
 *
 * Der Kreis ist blass, das Symbol voll - so bleibt es ein Zeichen und wird
 * kein Knopf, auf den man tippen moechte.
 */
fun Context.sportzeichen(art: Sportart.Art, groesse: Float = 40f): TextView =
    TextView(this).apply {
        text = art.zeichen
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, groesse * 0.45f)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(farbe(art.farbe) and 0x00FFFFFF or 0x33000000)
        }
        layoutParams = LinearLayout.LayoutParams(dp(groesse), dp(groesse)).apply {
            marginEnd = dp(12f)
        }
    }

/** Zeichen, Titel und Unterzeile nebeneinander - der Kopf einer Sitzungskarte. */
fun Context.sportkopf(art: Sportart.Art, titel: String, unter: String, gross: Boolean): LinearLayout =
    reihe().apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(sportzeichen(art, if (gross) 48f else 38f))
        addView(spalte().apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(
                if (gross) kartentitel(titel)
                else fliesstext(titel).apply { setTypeface(typeface, Typeface.BOLD) }
            )
            addView(zart(unter))
        })
    }

// --- Vier Wochen als Kalender ------------------------------------------------

/** Ein Trainingstag: die Farben seiner Arten, die laengste zuerst, und die Minuten. */
data class Trainingstag(val farben: List<Int>, val minuten: Long)

/**
 * Vier Wochen, ein Punkt je Tag.
 *
 * PUNKTE STATT KACHELN. Ein Raster aus vollen Feldern mit Zahlen darin war
 * laut und sah nach Tabelle aus - achtundzwanzig Kaesten, von denen die
 * meisten grau waren. Jetzt ist ein leerer Tag ein kleiner Punkt, ein
 * Trainingstag ein Kreis in der Farbe seiner Art, und die GROESSE sagt, wie
 * lang: zwanzig Minuten Yoga sind ein kleiner Kreis, zwei Stunden Rad ein
 * grosser. Kam an einem Tag eine zweite Art dazu, liegt ihr Ring aussen.
 */
class KalenderView(
    ctx: Context,
    private val heute: LocalDate,
    private val tage: Map<LocalDate, Trainingstag>,
) : View(ctx) {

    private val wochen = 4
    private val zeile = ctx.dp(34f).toFloat()
    private val kopfHoehe = ctx.dp(20f).toFloat()
    private val randLinks = ctx.dp(34f).toFloat()
    private val start = heute.with(DayOfWeek.MONDAY).minusWeeks((wochen - 1).toLong())

    private val fuellung = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2f).toFloat()
    }
    private val heuteRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1.5f).toFloat()
        color = ctx.farbe(R.color.schrift_zart)
    }
    private val leer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ctx.farbe(R.color.schrift_zart)
        alpha = 70
    }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics)
        color = ctx.farbe(R.color.schrift_zart)
    }
    private val fett = Paint(schrift).apply {
        color = ctx.farbe(R.color.schrift)
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onMeasure(breiteSpec: Int, hoeheSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(breiteSpec), (kopfHoehe + wochen * zeile).toInt())
    }

    override fun onDraw(leinwand: Canvas) {
        val fach = (width - randLinks) / 7f
        val gross = minOf(fach, zeile) / 2f - context.dp(3f)
        val klein = gross * 0.42f

        schrift.textAlign = Paint.Align.CENTER
        fett.textAlign = Paint.Align.CENTER
        DayOfWeek.values().forEachIndexed { i, tag ->
            val x = randLinks + fach * i + fach / 2
            val name = tag.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2)
            leinwand.drawText(name, x, kopfHoehe - context.dp(7f), if (tag == heute.dayOfWeek) fett else schrift)
        }

        for (w in 0 until wochen) {
            val montag = start.plusWeeks(w.toLong())
            val my = kopfHoehe + w * zeile + zeile / 2

            // Die Kalenderwoche links, klein: wer plant, plant in Wochen.
            schrift.textAlign = Paint.Align.LEFT
            leinwand.drawText(
                "KW " + montag.get(WeekFields.ISO.weekOfWeekBasedYear()),
                0f, my + schrift.textSize / 3, schrift,
            )

            for (t in 0 until 7) {
                val datum = montag.plusDays(t.toLong())
                val mx = randLinks + fach * t + fach / 2
                val tag = tage[datum]

                when {
                    datum.isAfter(heute) -> {
                        leer.alpha = 30
                        leinwand.drawCircle(mx, my, context.dp(2f).toFloat(), leer)
                    }
                    tag == null -> {
                        leer.alpha = 70
                        leinwand.drawCircle(mx, my, context.dp(2.5f).toFloat(), leer)
                    }
                    else -> {
                        // Die Flaeche waechst mit der Zeit, nicht der Radius:
                        // doppelt so lang soll doppelt so viel Farbe sein.
                        val anteil = Math.sqrt((tag.minuten / 120.0).coerceIn(0.0, 1.0)).toFloat()
                        val r = klein + (gross - klein) * anteil
                        fuellung.color = tag.farben.first()
                        leinwand.drawCircle(mx, my, r, fuellung)
                        tag.farben.getOrNull(1)?.let { zweite ->
                            ring.color = zweite
                            leinwand.drawCircle(mx, my, r + ring.strokeWidth * 1.3f, ring)
                        }
                    }
                }
                if (datum == heute) {
                    leinwand.drawCircle(mx, my, gross + context.dp(1.5f), heuteRing)
                }
            }
        }
    }
}

/** Den Kalender aus den Sitzungen bauen. */
fun Context.kalenderbild(sitzungen: List<ExerciseSessionRecord>, heute: LocalDate): View {
    val zone = ZoneId.systemDefault()
    val tage = sitzungen.groupBy { it.startTime.atZone(zone).toLocalDate() }
        .mapValues { (_, liste) ->
            val farben = liste.groupBy { Sportart.von(it) }
                .map { (art, s) -> art to s.sumOf { Sportart.minuten(it) } }
                .sortedByDescending { it.second }
                .map { farbe(it.first.farbe) }
                .distinct()
            Trainingstag(farben, liste.sumOf { Sportart.minuten(it) })
        }
    return KalenderView(this, heute, tage).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16f); bottomMargin = dp(4f) }
    }
}

// --- Acht Wochen als gestapelte Saeulen --------------------------------------

/**
 * Acht Wochen, je eine Saeule, gestapelt nach Art.
 *
 * GESTAPELT UND NICHT NEBENEINANDER: die Frage ist zuerst "wie viel war es",
 * dann "woraus bestand es". Die Hoehe beantwortet die erste, die Farben die
 * zweite - beides in einem Blick.
 */
class WochenView(
    ctx: Context,
    private val wochen: List<Pair<LocalDate, List<Pair<Int, Long>>>>,
    private val diese: LocalDate,
) : View(ctx) {

    private val stift = Paint(Paint.ANTI_ALIAS_FLAG)
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics)
        color = ctx.farbe(R.color.schrift_zart)
        textAlign = Paint.Align.CENTER
    }
    private val fett = Paint(schrift).apply {
        color = ctx.farbe(R.color.schrift)
        typeface = Typeface.DEFAULT_BOLD
    }
    private val leer = ctx.farbe(R.color.linie)

    override fun onDraw(leinwand: Canvas) {
        if (wochen.isEmpty()) return
        val fuss = context.dp(16f).toFloat()
        val kopf = context.dp(16f).toFloat()
        val boden = height - fuss
        val hoehe = boden - kopf
        val spitze = maxOf(wochen.maxOf { (_, t) -> t.sumOf { it.second } }, 60L).toFloat()
        val fach = width.toFloat() / wochen.size
        val balken = minOf(fach * 0.55f, context.dp(26f).toFloat())
        val ecke = context.dp(4f).toFloat()

        wochen.forEachIndexed { i, (montag, teile) ->
            val mitte = fach * i + fach / 2
            val links = mitte - balken / 2
            val summe = teile.sumOf { it.second }

            // Die leere Spur zeigt, dass die Woche da war - auch ohne Training.
            stift.color = leer
            stift.alpha = 255
            leinwand.drawRoundRect(RectF(links, kopf, links + balken, boden), ecke, ecke, stift)

            if (summe > 0) {
                val oben = boden - hoehe * (summe / spitze)
                // Erst rund ausschneiden, dann die Schichten hinein: so hat
                // der ganze Stapel runde Ecken und nicht jede Schicht.
                leinwand.save()
                val form = Path().apply {
                    addRoundRect(RectF(links, oben, links + balken, boden), ecke, ecke, Path.Direction.CW)
                }
                leinwand.clipPath(form)
                var y = boden
                teile.forEach { (farbe, minuten) ->
                    val h = hoehe * (minuten / spitze)
                    stift.color = farbe
                    stift.alpha = if (montag == diese) 255 else 215
                    leinwand.drawRect(links, y - h, links + balken, y, stift)
                    y -= h
                }
                leinwand.restore()
                leinwand.drawText(Zahlen.dauer(summe.toDouble()) ?: "", mitte, oben - context.dp(4f), schrift)
            }

            val kw = montag.get(WeekFields.ISO.weekOfWeekBasedYear()).toString()
            leinwand.drawText(kw, mitte, height - context.dp(3f).toFloat(), if (montag == diese) fett else schrift)
        }
    }
}

fun Context.wochenstapel(sitzungen: List<ExerciseSessionRecord>, heute: LocalDate): View {
    val zone = ZoneId.systemDefault()
    val diese = heute.with(DayOfWeek.MONDAY)
    val wochen = (7 downTo 0).map { diese.minusWeeks(it.toLong()) }.map { montag ->
        val drin = sitzungen.filter {
            val tag = it.startTime.atZone(zone).toLocalDate()
            !tag.isBefore(montag) && tag.isBefore(montag.plusWeeks(1))
        }
        val teile = Sportart.ALLE.mapNotNull { art ->
            val min = drin.filter { Sportart.von(it) == art }.sumOf { Sportart.minuten(it) }
            if (min > 0) farbe(art.farbe) to min else null
        } + drin.filter { Sportart.von(it) !in Sportart.ALLE }
            .sumOf { Sportart.minuten(it) }
            .let { if (it > 0) listOf(farbe(R.color.sport_anderes) to it) else emptyList() }
        montag to teile
    }
    return WochenView(this, wochen, diese).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(160f)
        ).apply { topMargin = dp(8f) }
    }
}

// --- Woraus es besteht -------------------------------------------------------

/**
 * Die Arten eines Vierteljahres: ein Band und darunter die Legende.
 *
 * EIN BAND STATT EINES KUCHENS. Anteile liest man an Laengen besser als an
 * Winkeln, und das Band braucht keinen eigenen Platz neben der Legende.
 */
fun Context.verteilung(sitzungen: List<ExerciseSessionRecord>): LinearLayout {
    val s = spalte()
    val jeArt = sitzungen.groupBy { Sportart.von(it) }
        .map { (art, liste) -> Triple(art, liste.size, liste.sumOf { Sportart.minuten(it) }) }
        .filter { it.third > 0 }
        .sortedByDescending { it.third }
    if (jeArt.isEmpty()) return s
    val gesamt = jeArt.sumOf { it.third }.toFloat()

    val band = reihe().apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(14f)
        ).apply { topMargin = dp(12f); bottomMargin = dp(10f) }
        background = GradientDrawable().apply {
            cornerRadius = dp(7f).toFloat()
            setColor(farbe(R.color.linie))
        }
        clipToOutline = true
    }
    jeArt.forEachIndexed { i, (art, _, minuten) ->
        band.addView(View(this).apply {
            setBackgroundColor(farbe(art.farbe))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, minuten / gesamt).apply {
                if (i > 0) marginStart = dp(2f)
            }
        })
    }
    s.addView(band)

    jeArt.forEach { (art, anzahl, minuten) ->
        s.addView(reihe().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4f), 0, dp(4f))
            addView(sportzeichen(art, 30f))
            addView(fliesstext(art.name).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(zart(
                anzahl.toString() + "×  ·  " + (Zahlen.dauer(minuten.toDouble()) ?: "") +
                    "  ·  " + Math.round(minuten / gesamt * 100) + " %"
            ))
        })
    }
    return s
}

// --- Der Puls eines Trainings ------------------------------------------------

/** Ein Pulswert, gezaehlt ab dem Start des Trainings. */
data class Pulspunkt(val sekunde: Long, val bpm: Long)

/**
 * Der Puls ueber das Training, als Flaeche.
 *
 * DIE FLAECHE UND NICHT NUR DIE LINIE: ein Training ist Anstrengung ueber
 * Zeit, und die Flaeche darunter ist genau das. Oben die Linie fuer den
 * Verlauf, gestrichelt der Schnitt, und die Spitze bekommt einen Punkt.
 */
class TrainingspulsView(
    ctx: Context,
    private val punkte: List<Pulspunkt>,
    private val ton: Int,
) : View(ctx) {

    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2f).toFloat()
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = ton
    }
    private val flaeche = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val schnitt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(1f).toFloat()
        color = ctx.farbe(R.color.schrift_zart)
        pathEffect = DashPathEffect(floatArrayOf(ctx.dp(4f).toFloat(), ctx.dp(3f).toFloat()), 0f)
    }
    private val punkt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ton }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics)
        color = ctx.farbe(R.color.schrift_zart)
    }
    private val fett = Paint(schrift).apply {
        color = ctx.farbe(R.color.schrift)
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(leinwand: Canvas) {
        if (punkte.size < 2) return
        val fuss = context.dp(16f).toFloat()
        val kopf = context.dp(16f).toFloat()
        val boden = height - fuss
        val ende = punkte.last().sekunde.coerceAtLeast(1)
        val tief = punkte.minOf { it.bpm } - 5
        val hoch = punkte.maxOf { it.bpm } + 5
        val spanne = (hoch - tief).coerceAtLeast(1).toFloat()

        fun x(s: Long) = width * (s.toFloat() / ende)
        fun y(b: Long) = kopf + (boden - kopf) * (1f - (b - tief) / spanne)

        val pfad = Path()
        punkte.forEachIndexed { i, p ->
            if (i == 0) pfad.moveTo(x(p.sekunde), y(p.bpm)) else pfad.lineTo(x(p.sekunde), y(p.bpm))
        }
        val flaechenpfad = Path(pfad).apply {
            lineTo(x(punkte.last().sekunde), boden)
            lineTo(x(punkte.first().sekunde), boden)
            close()
        }
        flaeche.shader = LinearGradient(
            0f, kopf, 0f, boden,
            ton and 0x00FFFFFF or 0x66000000, ton and 0x00FFFFFF,
            Shader.TileMode.CLAMP,
        )
        leinwand.drawPath(flaechenpfad, flaeche)
        leinwand.drawPath(pfad, linie)

        val mittel = punkte.map { it.bpm }.average()
        val my = y(Math.round(mittel))
        leinwand.drawLine(0f, my, width.toFloat(), my, schnitt)
        schrift.textAlign = Paint.Align.LEFT
        leinwand.drawText("Ø " + Math.round(mittel), context.dp(2f).toFloat(), my - context.dp(3f), schrift)

        val spitze = punkte.maxByOrNull { it.bpm }!!
        val sx = x(spitze.sekunde)
        val sy = y(spitze.bpm)
        leinwand.drawCircle(sx, sy, context.dp(4f).toFloat(), punkt)
        fett.textAlign = when {
            sx < width * 0.15f -> Paint.Align.LEFT
            sx > width * 0.85f -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }
        leinwand.drawText("max " + spitze.bpm, sx, sy - context.dp(7f), fett)

        // Die Zeitachse: Anfang, Mitte, Ende - mehr liest beim Training
        // niemand ab.
        val unten = height - context.dp(3f).toFloat()
        schrift.textAlign = Paint.Align.LEFT
        leinwand.drawText("0", 0f, unten, schrift)
        schrift.textAlign = Paint.Align.CENTER
        leinwand.drawText((ende / 120).toString(), width / 2f, unten, schrift)
        schrift.textAlign = Paint.Align.RIGHT
        leinwand.drawText((ende / 60).toString() + " min", width.toFloat(), unten, schrift)
    }
}

fun Context.trainingspuls(punkte: List<Pulspunkt>, ton: Int): View =
    TrainingspulsView(this, punkte, ton).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(150f)
        ).apply { topMargin = dp(10f) }
    }

// --- Saetze auf der Zeitachse ------------------------------------------------

/** Ein Satz: wann er begann, wie lange er dauerte, wie viele Wiederholungen. */
data class Satz(val beginn: Long, val dauer: Long, val wiederholungen: Int)

/**
 * Die Saetze eines Krafttrainings, so wie sie in der Zeit lagen.
 *
 * DIE PAUSEN SIND DER PUNKT. Die Balken zeigen die Saetze - hoch, wer viele
 * Wiederholungen hatte, breit, wer lange dauerte -, und dazwischen steht, wie
 * lange Ruhe war. Das ist die Zahl, die ueber das Training entscheidet, und
 * in einer Liste ging sie unter.
 */
class SatzView(
    ctx: Context,
    private val saetze: List<Satz>,
    private val ton: Int,
) : View(ctx) {

    private val stift = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ton }
    private val pause = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ctx.dp(2f).toFloat()
        color = ctx.farbe(R.color.linie)
        pathEffect = DashPathEffect(floatArrayOf(ctx.dp(3f).toFloat(), ctx.dp(3f).toFloat()), 0f)
    }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, ctx.resources.displayMetrics)
        color = ctx.farbe(R.color.schrift_zart)
        textAlign = Paint.Align.CENTER
    }
    private val fett = Paint(schrift).apply {
        color = ctx.farbe(R.color.schrift)
        typeface = Typeface.DEFAULT_BOLD
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, ctx.resources.displayMetrics)
    }

    override fun onDraw(leinwand: Canvas) {
        if (saetze.isEmpty()) return
        val anfang = saetze.first().beginn
        val ende = saetze.maxOf { it.beginn + it.dauer }
        val spanne = (ende - anfang).coerceAtLeast(1).toFloat()
        val kopf = context.dp(18f).toFloat()
        val fuss = context.dp(16f).toFloat()
        val boden = height - fuss
        val spitze = saetze.maxOf { it.wiederholungen }.coerceAtLeast(1).toFloat()
        val mindestens = context.dp(10f).toFloat()
        val ecke = context.dp(3f).toFloat()

        fun x(s: Long) = width * ((s - anfang) / spanne)

        saetze.forEachIndexed { i, satz ->
            val links = x(satz.beginn)
            val rechts = maxOf(x(satz.beginn + satz.dauer), links + mindestens)
            val oben = boden - (boden - kopf) * (satz.wiederholungen / spitze)
            stift.alpha = 200 + (55 * satz.wiederholungen / spitze).toInt()
            leinwand.drawRoundRect(RectF(links, oben, rechts, boden), ecke, ecke, stift)
            leinwand.drawText(satz.wiederholungen.toString(), (links + rechts) / 2, oben - context.dp(4f), fett)

            val naechster = saetze.getOrNull(i + 1) ?: return@forEachIndexed
            val pvon = rechts + context.dp(2f)
            val pbis = x(naechster.beginn) - context.dp(2f)
            if (pbis - pvon > context.dp(6f)) {
                val py = boden - context.dp(2f)
                leinwand.drawLine(pvon, py, pbis, py, pause)
                val sek = naechster.beginn - (satz.beginn + satz.dauer)
                if (pbis - pvon > context.dp(22f) && sek > 0) {
                    leinwand.drawText(sek.toString() + " s", (pvon + pbis) / 2, height - context.dp(3f).toFloat(), schrift)
                }
            }
        }
    }
}

fun Context.satzbild(saetze: List<Satz>, ton: Int): View =
    SatzView(this, saetze, ton).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(130f)
        ).apply { topMargin = dp(10f) }
    }
