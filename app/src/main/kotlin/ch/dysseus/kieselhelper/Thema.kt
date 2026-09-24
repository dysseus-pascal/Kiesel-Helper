package ch.dysseus.kieselhelper

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

/**
 * Hell, dunkel oder wie das System - und auf Wunsch Material You.
 *
 * ZWEI SCHALTER, UNABHAENGIG VONEINANDER. Der Modus entscheidet, ob die hellen
 * oder die dunklen Farben gelten (values/ oder values-night/); Material You
 * ersetzt die Grundfarben - Grund, Karten, Schrift und die drei Akzente -
 * durch die Farben, die Android aus dem Hintergrundbild zieht.
 *
 * MATERIAL YOU NUR FUER DIE GRUNDFARBEN. Die Sportarten, die Schlafphasen,
 * die Pulszonen und Wasser und Koffein tragen Bedeutung: Zone 5 ist rot, weil
 * sie Zone 5 ist, nicht weil das Hintergrundbild rot ist. Sie bleiben.
 *
 * DER ERSTE VERSUCH MIT MATERIAL YOU WAR EINE ROSA WAND mit Schrift, die kaum
 * vom Grund abstand: das Thema DeviceDefault faerbte alles, auch die Schrift.
 * Hier wird jede Farbe einzeln einem Ton der Systempalette zugeordnet - die
 * Schrift dem dunkelsten (hell) oder hellsten (dunkel) neutralen Ton, der
 * Grund dem hellsten oder dunkelsten -, und der Abstand bleibt der, den die
 * eigenen Farben hatten.
 */
object Thema {

    const val SYSTEM = 0
    const val HELL = 1
    const val DUNKEL = 2

    fun materialYouMoeglich(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun dunkel(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Den Kontext einer Activity auf den gewaehlten Modus stellen.
     *
     * UEBER DIE KONFIGURATION, nicht ueber eigene Farbtabellen: so gilt die
     * Wahl fuer ALLES, was aus den Ressourcen kommt - values-night/, das
     * Thema, die Leistensymbole -, ohne dass eine Zeile davon es wissen muss.
     */
    fun umhuellen(base: Context): Context {
        val modus = Einstellungen.themaModus(base)
        if (modus == SYSTEM) return base
        val conf = Configuration(base.resources.configuration)
        val nacht = if (modus == DUNKEL) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nacht
        return base.createConfigurationContext(conf)
    }

    /** Woran sich eine offene Activity erkennt, dass sie neu gebaut werden muss. */
    fun signatur(ctx: Context): String =
        "${Einstellungen.themaModus(ctx)}/${Einstellungen.materialYou(ctx)}/${dunkel(ctx)}"

    /**
     * Eine Farbe fuer das Widget - als Paar fuer hell und dunkel.
     *
     * DAS WIDGET LEBT IM STARTBILDSCHIRM, nicht in dieser App: welcher Modus
     * gerade gilt, entscheidet dort das System. Deshalb bekommt es beide
     * Farben und waehlt selbst - ausser in den Einstellungen steht "Hell"
     * oder "Dunkel", dann ist es in beiden Faellen dieselbe.
     */
    fun widgetPaar(ctx: Context, id: Int): Pair<Int, Int> {
        fun eigene(nacht: Boolean): Int {
            val conf = Configuration(ctx.resources.configuration)
            conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (nacht) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            return ctx.createConfigurationContext(conf).getColor(id)
        }
        fun eine(nacht: Boolean): Int {
            if (materialYouMoeglich() && Einstellungen.materialYou(ctx)) {
                palette(id, nacht)?.let { return ctx.getColor(it) }
            }
            return eigene(nacht)
        }
        return when (Einstellungen.themaModus(ctx)) {
            HELL -> eine(false).let { it to it }
            DUNKEL -> eine(true).let { it to it }
            else -> eine(false) to eine(true)
        }
    }

    /** Die Farbe zu einer Ressource - mit Material You, wo es gilt. */
    fun farbe(ctx: Context, id: Int): Int {
        if (materialYouMoeglich() && Einstellungen.materialYou(ctx)) {
            dynamisch(id, dunkel(ctx))?.let { return ctx.resources.getColor(it, ctx.theme) }
        }
        return ctx.resources.getColor(id, ctx.theme)
    }

    /**
     * Welcher Ton der Systempalette an die Stelle einer eigenen Farbe tritt.
     *
     * Die Palette hat Stufen von 0 (weiss) bis 1000 (schwarz). Hell: Grund
     * 50, Karte 10, Schrift 900. Dunkel umgekehrt: Grund 900, Karte 800,
     * Schrift 100. Die drei Reiter bekommen die drei Akzentfamilien des
     * Systems - Gesundheit die erste, Ernaehrung die zweite, Training die
     * dritte -, damit sie unterscheidbar bleiben.
     */
    private fun dynamisch(id: Int, dunkel: Boolean): Int? {
        if (!materialYouMoeglich()) return null
        return palette(id, dunkel)
    }

    private fun palette(id: Int, dunkel: Boolean): Int? = if (!materialYouMoeglich()) null else when (id) {
        R.color.grund -> if (dunkel) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50
        R.color.karte -> if (dunkel) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_10
        R.color.linie -> if (dunkel) android.R.color.system_neutral2_700 else android.R.color.system_neutral2_100
        R.color.schrift -> if (dunkel) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900
        R.color.schrift_zart -> if (dunkel) android.R.color.system_neutral2_300 else android.R.color.system_neutral2_700
        R.color.akzent -> if (dunkel) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
        R.color.akzent_gedrueckt -> if (dunkel) android.R.color.system_accent1_100 else android.R.color.system_accent1_700
        R.color.akzent_schrift -> if (dunkel) android.R.color.system_accent1_900 else android.R.color.system_accent1_0
        R.color.akzent_ernaehrung -> if (dunkel) android.R.color.system_accent2_200 else android.R.color.system_accent2_600
        R.color.akzent_ernaehrung_gedrueckt -> if (dunkel) android.R.color.system_accent2_100 else android.R.color.system_accent2_700
        R.color.akzent_training -> if (dunkel) android.R.color.system_accent3_200 else android.R.color.system_accent3_600
        R.color.akzent_training_gedrueckt -> if (dunkel) android.R.color.system_accent3_100 else android.R.color.system_accent3_700
        else -> null
    }
}

/**
 * Der Grund aller eigenen Schirme: Modus und Material You gelten, und eine
 * Aenderung in den Einstellungen erreicht auch die Schirme, die darunter
 * noch offen sind.
 */
open class KieselActivity : ComponentActivity() {

    private var signatur: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Thema.umhuellen(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signatur = Thema.signatur(this)
        faerbeFenster()
    }

    override fun onResume() {
        super.onResume()
        // In den Einstellungen umgestellt, waehrend dieser Schirm darunter
        // lag: neu bauen, sonst stuende er im alten Gewand da.
        if (signatur != null && signatur != Thema.signatur(this)) recreate()
    }

    /**
     * Fenstergrund und Leisten in der Grundfarbe - die kann mit Material You
     * eine andere sein als die im Thema, das nur die eigenen Farben kennt.
     */
    @Suppress("DEPRECATION")
    fun faerbeFenster() {
        val grund = farbe(R.color.grund)
        window.decorView.setBackgroundColor(grund)
        window.statusBarColor = grund
        window.navigationBarColor = grund
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !Thema.dunkel(this@KieselActivity)
            isAppearanceLightNavigationBars = !Thema.dunkel(this@KieselActivity)
        }
    }
}
