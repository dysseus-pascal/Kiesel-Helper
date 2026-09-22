package ch.dysseus.kieselhelper

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

/**
 * Eine Zahl mit Namen, Einheit und Balken.
 *
 * DER BALKEN ERSCHEINT NUR MIT ZIEL. Fuer einen Ruhepuls gibt es keins, und
 * ein Balken ohne Ziel waere eine Behauptung darueber, was gut ist.
 *
 * Fehlt der Wert ganz, steht das da - nicht eine Null. "Du bist heute keinen
 * Schritt gegangen" ist etwas anderes als "niemand hat Schritte eingetragen",
 * und eine Null sagt das Erste, wenn das Zweite gilt.
 */
fun Context.messwert(
    name: String,
    text: String?,
    einheit: String,
    anteil: Float,
    mitBalken: Boolean,
    /**
     * Was ueber das Ziel hinausgeht, als Anteil desselben Balkens.
     *
     * Ist er groesser als null, steht das Ziel als MARKE mitten im Balken und
     * nicht mehr an seinem Ende - genau wie die Linie im Wochenbild. Ohne
     * diese Marke sahen neun Stunden Schlaf bei acht Stunden Ideal aus wie
     * genau acht: der Balken war in beiden Faellen voll.
     */
    ueber: Float = 0f,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
    ).apply { setMargins(dp(4f), dp(6f), dp(4f), dp(6f)) }

    addView(TextView(this@messwert).apply {
        this.text = name
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(farbe(R.color.schrift_zart))
    })

    addView(LinearLayout(this@messwert).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.BOTTOM
        addView(TextView(this@messwert).apply {
            this.text = text ?: "—"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(farbe(if (text == null) R.color.schrift_zart else R.color.schrift))
        })
        if (text != null && einheit.isNotEmpty()) {
            addView(TextView(this@messwert).apply {
                this.text = " " + einheit
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(farbe(R.color.schrift_zart))
                setPadding(0, 0, 0, dp(3f))
            })
        }
    })

    if (mitBalken) {
        addView(View(this@messwert).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6f)
            ).apply { topMargin = dp(6f) }
            background = GradientDrawable().apply {
                setColor(farbe(R.color.linie))
                cornerRadius = dp(3f).toFloat()
            }
        })
        // Der gefuellte Teil liegt als eigene Sicht darueber, mit einem
        // Gewicht als Breite - so passt er sich der Spaltenbreite an, ohne
        // dass jemand Punkte ausrechnen muss.
        addView(LinearLayout(this@messwert).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6f)
            ).apply { topMargin = -dp(6f) }

            addView(View(this@messwert).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, dp(6f), anteil.coerceAtLeast(0.001f)
                )
                background = GradientDrawable().apply {
                    setColor(farbe(R.color.akzent))
                    cornerRadius = dp(3f).toFloat()
                }
            })

            if (ueber > 0f) {
                // Die Marke: ein schmaler Strich genau auf dem Ziel.
                addView(View(this@messwert).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(2f), dp(6f))
                    setBackgroundColor(farbe(R.color.schrift))
                })
                addView(View(this@messwert).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(6f), ueber)
                    background = GradientDrawable().apply {
                        setColor(farbe(R.color.ueber_ziel))
                        cornerRadius = dp(3f).toFloat()
                    }
                })
            }

            addView(View(this@messwert).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, dp(6f), (1f - anteil - ueber).coerceAtLeast(0.001f)
                )
            })
        })
    }
}

/** Zwei Messwerte nebeneinander. Mehr als zwei wird auf einem Telefon eng. */
fun Context.messreihe(links: View, rechts: View): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(links)
        addView(rechts)
    }

/**
 * Die Reiter sitzen unten.
 *
 * WEIL DER DAUMEN DORT IST. Oben waren sie zwar naeher am Titel, aber weiter
 * weg von der Hand - und auf einem Telefon dieser Groesse heisst "oben"
 * umgreifen. Unten ist ausserdem die Stelle, an der jede andere App ihre
 * Reiter hat; eine eigene Ordnung waere hier nur eine Huerde.
 *
 * Die Leiste liegt AUSSERHALB des Rollers und traegt eine Haarlinie nach oben,
 * damit sie sich von durchlaufendem Inhalt abhebt, ohne einen Schatten zu
 * brauchen.
 */
fun Context.fussleiste(namen: List<String>, waehle: (Int) -> Unit): LinearLayout {
    val kasten = spalte()
    kasten.addView(View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
        setBackgroundColor(farbe(R.color.linie))
    })

    val reihe = reihe()
    reihe.setBackgroundColor(farbe(R.color.karte))

    val marken = mutableListOf<View>()
    val felder = mutableListOf<TextView>()

    fun male(gewaehlt: Int) {
        felder.forEachIndexed { i, feld ->
            feld.setTextColor(farbe(if (i == gewaehlt) R.color.akzent else R.color.schrift_zart))
        }
        marken.forEachIndexed { i, marke ->
            marke.visibility = if (i == gewaehlt) View.VISIBLE else View.INVISIBLE
        }
    }

    namen.forEachIndexed { i, name ->
        val fach = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            setPadding(0, dp(8f), 0, dp(10f))
            setOnClickListener { male(i); waehle(i) }
        }
        // Ein kurzer Strich ueber dem gewaehlten Namen. Farbe allein traegt
        // die Aussage nicht - wer sie schlecht unterscheidet, sieht sonst
        // zwei gleich aussehende Woerter.
        val marke = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26f), dp(3f)).apply {
                bottomMargin = dp(5f)
            }
            background = GradientDrawable().apply {
                setColor(farbe(R.color.akzent))
                cornerRadius = dp(2f).toFloat()
            }
        }
        val feld = TextView(this).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        marken += marke
        felder += feld
        fach.addView(marke)
        fach.addView(feld)
        reihe.addView(fach)
    }

    kasten.addView(reihe)
    male(0)
    return kasten
}

/**
 * Ein Zahnrad - gezeichnet, nicht geladen.
 *
 * Dreissig Zeilen statt eines Satzes Bilddateien in fuenf Aufloesungen. Es
 * nimmt die Schriftfarbe an und stimmt damit bei Tag wie bei Nacht; ein
 * mitgeliefertes PNG haette eine feste Farbe und muesste eingefaerbt werden.
 *
 * Die Zaehne entstehen mit [Path.op] als VEREINIGUNG, das Loch als DIFFERENZ.
 * Mit einer Even-Odd-Fuellung waere es kuerzer und falsch: ueberlappende
 * Flaechen loeschten sich dort gegenseitig aus, und jeder Zahn risse ein Loch
 * in den Koerper.
 */
class ZahnradView(ctx: Context) : View(ctx) {

    private val stift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ctx.farbe(R.color.schrift_zart)
    }

    override fun onDraw(leinwand: Canvas) {
        val mx = width / 2f
        val my = height / 2f
        val spitze = minOf(width, height) / 2f * 0.60f
        val koerper = spitze * 0.74f
        val breite = spitze * 0.38f

        val weg = Path().apply { addCircle(mx, my, koerper, Path.Direction.CW) }
        val zahn = Path()
        val dreh = Matrix()
        for (i in 0 until 8) {
            zahn.reset()
            zahn.addRoundRect(
                RectF(mx - breite / 2, my - spitze, mx + breite / 2, my - koerper * 0.86f),
                breite * 0.3f, breite * 0.3f, Path.Direction.CW,
            )
            dreh.setRotate(i * 45f, mx, my)
            zahn.transform(dreh)
            weg.op(zahn, Path.Op.UNION)
        }
        weg.op(
            Path().apply { addCircle(mx, my, koerper * 0.40f, Path.Direction.CW) },
            Path.Op.DIFFERENCE,
        )
        leinwand.drawPath(weg, stift)
    }
}

/** Das Zahnrad als Schaltflaeche, mit rundem Druckschatten. */
fun Context.zahnradknopf(tue: () -> Unit): View = ZahnradView(this).apply {
    layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f))
    background = RippleDrawable(
        ColorStateList.valueOf(farbe(R.color.linie)),
        null,
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.WHITE)
        },
    )
    setOnClickListener { tue() }
}

/**
 * Die Kopfzeile: Name links, Zahnrad rechts.
 *
 * Sie scrollt NICHT mit. Wer die Einstellungen sucht, soll nicht erst
 * zurueckscrollen muessen, um sie zu finden.
 */
fun Context.kopfleiste(titel: String, zahnrad: () -> Unit): LinearLayout = reihe().apply {
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(16f), dp(18f), dp(10f), dp(6f))
    addView(kopf(titel).apply {
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
    })
    addView(zahnradknopf(zahnrad))
}

/**
 * Eine Abschnittsueberschrift, die weiterfuehrt.
 *
 * DER TIEFERE SCHIRM WAR VORHER EIN EIGENER REITER. Wer wissen wollte, ob
 * 7985 Schritte viel sind, musste unten umschalten und oben die Kategorie
 * suchen - zwei Bewegungen, zwischen denen man vergisst, was man wissen
 * wollte. Jetzt tippt man auf die Zahl, die die Frage ausgeloest hat.
 *
 * DER HINWEIS STEHT DA, WEIL EINE FLAECHE NICHT AUSSIEHT, ALS KOENNTE MAN SIE
 * TIPPEN. Eine Karte, die still auf eine Beruehrung wartet, wird nie
 * gefunden.
 */
fun Context.abschnittTipp(text: String, tue: () -> Unit): LinearLayout = reihe().apply {
    gravity = Gravity.CENTER_VERTICAL
    addView(abschnitt(text).apply {
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
    })
    addView(TextView(this@abschnittTipp).apply {
        this.text = "Trend ›"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(farbe(R.color.akzent))
        setPadding(dp(8f), dp(20f), dp(4f), dp(8f))
    })
    setOnClickListener { tue() }
}
