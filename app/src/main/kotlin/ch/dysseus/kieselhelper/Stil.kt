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
            ColorStateList.valueOf(farbe(Ton.gedrueckt)),
            GradientDrawable().apply {
                setColor(akzentfarbe())
                cornerRadius = dp(10f).toFloat()
            },
            null,
        )
    }

/** Umrandeter Knopf fuer alles Weitere. */
fun Context.knopfLeise(text: String, warnend: Boolean = false, tue: () -> Unit): Button =
    baueKnopf(text, false, tue).apply {
        val ton = if (warnend) farbe(R.color.warn_schrift) else akzentfarbe()
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
                    setColor(akzentfarbe())
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

    // JEDER REITER IN SEINEM EIGENEN TON, und zwar nur der gewaehlte. Drei
    // farbige Woerter nebeneinander waeren ein Farbkasten; eines, das die
    // Farbe des Schirms darueber hat, ist eine Auskunft.
    fun male(gewaehlt: Int) {
        felder.forEachIndexed { i, feld ->
            feld.setTextColor(
                if (i == gewaehlt) farbe(Ton.REITERFARBEN.getOrElse(i) { R.color.akzent })
                else farbe(R.color.schrift_zart)
            )
        }
        marken.forEachIndexed { i, marke ->
            marke.visibility = if (i == gewaehlt) View.VISIBLE else View.INVISIBLE
            // Auch der Strich traegt den Ton des Reiters - er wird beim
            // Faerben neu gesetzt und nicht beim Bauen, sonst haette jeder
            // den Ton, der gerade zufaellig galt.
            marke.background = GradientDrawable().apply {
                setColor(farbe(Ton.REITERFARBEN.getOrElse(i) { R.color.akzent }))
                cornerRadius = dp(2f).toFloat()
            }
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
                setColor(akzentfarbe())
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
        setTextColor(akzentfarbe())
        setPadding(dp(8f), dp(20f), dp(4f), dp(8f))
    })
    setOnClickListener { tue() }
}

/**
 * Welcher Reiter gerade gilt - und damit, welche Farbe.
 *
 * DREI REITER, DREI TOENE. Ein Blick auf den Schirm soll sagen, wo man ist,
 * ohne dass man unten nachsehen muss. Der Wasserton bleibt der der
 * Gesundheit - er war zuerst da und passt; Training bekommt einen warmen
 * Erdton, Ernaehrung ein Moosgruen.
 *
 * ALS GLOBALE STELLE UND NICHT ALS PARAMETER, weil die Farbe tief unten in
 * den Bildern gebraucht wird: ein Saeulendiagramm faerbt seine Balken selbst.
 * Sie durch zwanzig Funktionen durchzureichen waere sauberer und niemand
 * haette es je gepflegt.
 *
 * WER EINEN SCHIRM BAUT, SETZT SIE VORHER. Wer es vergisst, bekommt den Ton
 * des zuletzt gebauten Reiters - deshalb setzen die eigenen Schirme
 * (Einstellungen, Verlauf, Kartenlink) ihn ausdruecklich zurueck.
 */
object Ton {
    var akzent: Int = R.color.akzent
        private set
    var gedrueckt: Int = R.color.akzent_gedrueckt
        private set

    const val GESUNDHEIT = 0
    const val TRAINING = 1
    const val ERNAEHRUNG = 2

    fun setze(reiter: Int) {
        when (reiter) {
            TRAINING -> {
                akzent = R.color.akzent_training
                gedrueckt = R.color.akzent_training_gedrueckt
            }
            ERNAEHRUNG -> {
                akzent = R.color.akzent_ernaehrung
                gedrueckt = R.color.akzent_ernaehrung_gedrueckt
            }
            else -> {
                akzent = R.color.akzent
                gedrueckt = R.color.akzent_gedrueckt
            }
        }
    }

    /** Die Farben der Reiter, in ihrer Reihenfolge - fuer die Fussleiste. */
    val REITERFARBEN = listOf(
        R.color.akzent, R.color.akzent_training, R.color.akzent_ernaehrung
    )
}

/** Der Akzent des gerade gebauten Reiters. */
fun Context.akzentfarbe(): Int = farbe(Ton.akzent)

/**
 * Das Info-Zeichen - und der lange Satz dahinter.
 *
 * DIESE APP ERKLAERT VIEL, und das ist Absicht: eine Zahl ohne ihre Herkunft
 * ist eine Behauptung. Nur standen die Erklaerungen bisher alle offen unter
 * den Bildern, und wer sie zum dritten Mal liest, liest sie gar nicht mehr -
 * sie wurden zu grauem Rauschen, durch das man zur naechsten Zahl scrollt.
 *
 * JETZT STEHT DA EIN ZEICHEN. Der kurze Satz bleibt sichtbar, weil er sagt,
 * WAS man sieht; das lange Warum kommt auf Tippen. Wer es schon kennt,
 * scrollt daran vorbei, ohne es wegzuwischen.
 *
 * EIN EIGENES FENSTER UND KEIN SYSTEMDIALOG. Ein AlertDialog nimmt das Thema
 * des Systems an - auf neueren Telefonen also Material You, und damit stuende
 * mitten in dieser App ein rosa Kasten. Dasselbe Kartenbild wie ueberall
 * sonst kostet zehn Zeilen mehr und passt.
 */
fun Context.hinweiszeichen(lang: String): TextView = TextView(this).apply {
    text = "i"
    gravity = Gravity.CENTER
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(farbe(R.color.schrift_zart))
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(farbe(R.color.grund))
        setStroke(dp(1f), farbe(R.color.linie))
    }
    // VIERUNDZWANZIG PUNKTE, nicht sechzehn: kleiner trifft im Gehen
    // niemand, und was man dreimal danebentippt, tippt man nicht mehr an.
    layoutParams = LinearLayout.LayoutParams(dp(24f), dp(24f)).apply {
        marginStart = dp(8f)
        topMargin = dp(2f)
    }
    contentDescription = "Erklärung"
    setOnClickListener { zeigeHinweis(it, lang) }
}

/**
 * Ein kurzer Satz mit dem Zeichen dahinter.
 *
 * DER KURZE BLEIBT STEHEN. Er benennt, was man sieht - ohne ihn waere das
 * Bild darunter ein Raetsel, und ein Raetsel mit Info-Zeichen ist keine
 * Verbesserung.
 */
fun Context.zartMitHinweis(kurz: String, lang: String): LinearLayout = reihe().apply {
    gravity = Gravity.CENTER_VERTICAL
    addView(zart(kurz).apply {
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
    })
    addView(hinweiszeichen(lang))
}

/** Dasselbe mit einem gewoehnlichen Satz statt einem zarten. */
fun Context.textMitHinweis(kurz: String, lang: String): LinearLayout = reihe().apply {
    gravity = Gravity.CENTER_VERTICAL
    addView(fliesstext(kurz).apply {
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        )
    })
    addView(hinweiszeichen(lang))
}

private fun Context.zeigeHinweis(anker: View, lang: String) {
    val inhalt = karte().apply {
        setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
        addView(fliesstext(lang))
        addView(zart("Tippen schliesst"))
    }
    val breite = resources.displayMetrics.widthPixels - dp(48f)
    val fenster = android.widget.PopupWindow(
        inhalt, breite.coerceAtMost(dp(360f)),
        ViewGroup.LayoutParams.WRAP_CONTENT, true,
    )
    fenster.elevation = dp(10f).toFloat()
    fenster.isOutsideTouchable = true
    inhalt.setOnClickListener { fenster.dismiss() }
    // IN DER MITTE, nicht am Zeichen: ein Fenster, das unter dem letzten
    // Zeichen einer Karte aufginge, laege halb ausserhalb des Schirms.
    fenster.showAtLocation(anker.rootView, Gravity.CENTER, 0, 0)
}

/**
 * Nachfragen, bevor etwas Grosses geschieht.
 *
 * DERSELBE KASTEN WIE DER HINWEIS, nur mit zwei Knoepfen. Ein AlertDialog
 * naehme das Thema des Systems an - und stuende als rosa Kasten mitten in
 * dieser App.
 */
fun Context.bestaetige(frage: String, tue: () -> Unit) {
    val inhalt = karte().apply { setPadding(dp(18f), dp(16f), dp(18f), dp(12f)) }
    inhalt.addView(fliesstext(frage))

    val fenster = android.widget.PopupWindow(
        inhalt,
        (resources.displayMetrics.widthPixels - dp(48f)).coerceAtMost(dp(360f)),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        true,
    )
    fenster.elevation = dp(10f).toFloat()
    fenster.isOutsideTouchable = true

    val reihe = reihe()
    reihe.addView(knopfLeise("Abbrechen") { fenster.dismiss() }.apply {
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = dp(6f); topMargin = dp(8f) }
    })
    reihe.addView(knopfHaupt("Weiter") {
        fenster.dismiss()
        tue()
    }.apply {
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { topMargin = dp(8f) }
    })
    inhalt.addView(reihe)

    val wurzel = (this as? android.app.Activity)?.window?.decorView ?: return
    fenster.showAtLocation(wurzel, Gravity.CENTER, 0, 0)
}
