package ch.dysseus.kieselhelper

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Die Bausteine der Oberflaeche an einem Ort.
 *
 * Nach wie vor ohne Compose und ohne Layout-Dateien, aber nicht mehr ohne Form:
 * die erste Fassung schrieb rohe Pixelwerte hin und ueberliess die Farben dem
 * System - auf einem Telefon mit Material You wurde daraus eine rosa Wand mit
 * Schrift, die kaum vom Grund abstand.
 *
 * Zwei Dinge sind deshalb Regel:
 *  1. Masse immer in dp, nie in Pixeln. 48 Pixel sind auf dem einen Telefon
 *     ein Rand und auf dem anderen ein Fingerbreit.
 *  2. Farben aus colors.xml, nie geerbt.
 */

fun Context.dp(wert: Float): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, wert, resources.displayMetrics
).toInt()

fun Context.farbe(id: Int): Int = resources.getColor(id, theme)

/** Senkrechter Kasten, so breit wie sein Platz. */
fun Context.spalte(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

fun Context.reihe(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

/**
 * Eine Karte: weisser Grund, weiche Ecken, ein Hauch Schatten.
 *
 * Der Schatten braucht keinen eigenen Umriss - GradientDrawable liefert ihn
 * mit, sonst zeichnete Android einen rechteckigen Schatten unter die runden
 * Ecken.
 */
fun Context.karte(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = GradientDrawable().apply {
        setColor(farbe(R.color.karte))
        cornerRadius = dp(14f).toFloat()
        setStroke(dp(1f), farbe(R.color.linie))
    }
    elevation = dp(1.5f).toFloat()
    val p = dp(16f)
    setPadding(p, p, p, p)
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(12f) }
}

/** Der Name der App bzw. des Bildschirms, ganz oben. */
fun Context.kopf(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(farbe(R.color.schrift))
    letterSpacing = -0.01f
}

/** Abschnittsueberschrift ueber einer Gruppe von Karten. */
fun Context.abschnitt(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(farbe(R.color.schrift_zart))
    letterSpacing = 0.08f
    setPadding(dp(4f), dp(20f), 0, dp(8f))
}

/** Titel einer Karte. */
fun Context.kartentitel(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(farbe(R.color.schrift))
}

fun Context.fliesstext(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setTextColor(farbe(R.color.schrift))
    setLineSpacing(dp(3f).toFloat(), 1f)
}

fun Context.zart(text: String): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    setTextColor(farbe(R.color.schrift_zart))
    setLineSpacing(dp(2f).toFloat(), 1f)
}

/** Duenner Trennstrich innerhalb einer Karte. */
fun Context.strich(): View = View(this).apply {
    setBackgroundColor(farbe(R.color.linie))
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
        .apply { topMargin = dp(12f); bottomMargin = dp(12f) }
}

/**
 * Das Schildchen am Fuss einer Beschreibungskarte.
 *
 * Es steht bei der Beschreibung, die die Erlaubnis braucht, und nicht in einer
 * allgemeinen Liste: so sieht man, WELCHE Beschreibung gerade nicht arbeiten
 * kann.
 */
fun Context.schild(gut: Boolean, text: String): TextView = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(farbe(if (gut) R.color.gut_schrift else R.color.warn_schrift))
    background = GradientDrawable().apply {
        setColor(farbe(if (gut) R.color.gut_grund else R.color.warn_grund))
        cornerRadius = dp(99f).toFloat()
    }
    setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

/** Gefuellter Knopf fuer die eine Handlung, um die es auf dem Bildschirm geht. */
fun Context.knopfHaupt(text: String, breit: Boolean = false, tue: () -> Unit): Button =
    baueKnopf(text, breit, tue).apply {
        setTextColor(farbe(R.color.akzent_schrift))
        background = RippleDrawable(
            ColorStateList.valueOf(farbe(R.color.akzent_gedrueckt)),
            GradientDrawable().apply {
                setColor(farbe(R.color.akzent))
                cornerRadius = dp(10f).toFloat()
            },
            null,
        )
    }

/** Umrandeter Knopf fuer alles Weitere. */
fun Context.knopfLeise(text: String, warnend: Boolean = false, tue: () -> Unit): Button =
    baueKnopf(text, false, tue).apply {
        val ton = farbe(if (warnend) R.color.warn_schrift else R.color.akzent)
        setTextColor(ton)
        background = RippleDrawable(
            ColorStateList.valueOf(farbe(R.color.linie)),
            GradientDrawable().apply {
                setColor(farbe(R.color.karte))
                cornerRadius = dp(10f).toFloat()
                setStroke(dp(1f), ton)
            },
            null,
        )
    }

private fun Context.baueKnopf(text: String, breit: Boolean, tue: () -> Unit): Button =
    Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTypeface(typeface, Typeface.BOLD)
        stateListAnimator = null      // sonst huepft der Knopf beim Druecken
        minHeight = dp(46f)
        minimumHeight = dp(46f)
        setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
        setOnClickListener { tue() }
        layoutParams = LinearLayout.LayoutParams(
            if (breit) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.START }
    }

/** Eingabefeld mit Rahmen statt der blossen Linie darunter. */
fun Context.eingabefeld(hinweis: String): EditText = EditText(this).apply {
    hint = hinweis
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    setTextColor(farbe(R.color.schrift))
    setHintTextColor(farbe(R.color.schrift_zart))
    setSingleLine(true)
    background = GradientDrawable().apply {
        setColor(farbe(R.color.karte))
        cornerRadius = dp(10f).toFloat()
        setStroke(dp(1f), farbe(R.color.linie))
    }
    setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

/** Abstand zwischen zwei Dingen im selben Kasten. */
fun LinearLayout.luft(hoehe: Float) {
    addView(View(context), LinearLayout.LayoutParams(1, context.dp(hoehe)))
}

/**
 * Platz fuer Statusleiste und Systemtasten lassen.
 *
 * Seit targetSdk 35 zeichnet Android jede App von Kante zu Kante - man kann
 * das nicht mehr abwaehlen. Ohne diese Zeilen stuende der Titel unter der Uhr
 * und die unterste Karte unter den Systemtasten; genau so sah es in der ersten
 * Fassung aus.
 *
 * Gemerkt wird der Rand, den die Ansicht selbst mitbringt: der Aufruf kann
 * mehrfach kommen (Drehen, Tastatur), und wer jedes Mal aufaddiert, schiebt
 * den Inhalt Stueck fuer Stueck nach unten.
 */
fun View.randUmSystemleisten() {
    val links = paddingLeft
    val rechts = paddingRight
    val oben = paddingTop
    val unten = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { ansicht, fenster ->
        val leisten = fenster.getInsets(WindowInsetsCompat.Type.systemBars())
        ansicht.setPadding(
            links + leisten.left,
            oben + leisten.top,
            rechts + leisten.right,
            unten + leisten.bottom,
        )
        fenster
    }
}
