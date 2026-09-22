package ch.dysseus.kieselhelper

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Was der Mensch einstellt - und nicht die Akte weiss.
 *
 * BISHER STANDEN DIE ZIELE ALS KONSTANTEN IM CODE, mit der Begruendung, eine
 * Einstellung waere ein Bildschirm mehr fuer eine Zahl, die man einmal im
 * Leben setzt. Das stimmt fuer Schritte und Bewegung, wo zehntausend und
 * dreissig Minuten Hausnummern sind, an denen sich ohnehin niemand misst.
 *
 * FUER DEN SCHLAF STIMMT ES NICHT. Acht Stunden sind ein Mittelwert ueber
 * Menschen, keine Vorgabe fuer einen; wer mit sieben auskommt, bekommt jede
 * Nacht einen Balken vorgehalten, der nichts bedeutet. Und wer neun braucht,
 * sieht eine erfuellte Vorgabe, wo eine kurze Nacht war.
 *
 * Deshalb genau EIN Wert hier, nicht sieben. Was man selbst gesetzt hat, kann
 * man gegen sich gelten lassen.
 */
object Einstellungen {

    private const val DATEI = "kiesel-einstellungen"
    private const val SCHLAF = "schlaf_ziel_min"
    private const val GRENZE = "tagesgrenze_stunde"
    private const val FUEHRT = "kartenlink_fuehrt"

    /** Acht Stunden, bis jemand etwas anderes sagt. */
    const val SCHLAF_VORGABE = 8 * 60

    /**
     * In Viertelstunden.
     *
     * Feiner waere eine Genauigkeit, die niemand hat: wer sein Schlafbeduerfnis
     * auf fuenf Minuten genau kennt, misst es nicht mit einer Uhr am
     * Handgelenk.
     */
    const val SCHLAF_SCHRITT = 15
    const val SCHLAF_KLEINSTES = 4 * 60
    const val SCHLAF_GROESSTES = 12 * 60

    fun schlafziel(context: Context): Int =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getInt(SCHLAF, SCHLAF_VORGABE)

    /** Setzt das Ziel und haelt es in den Grenzen des Sinnvollen. */
    fun setzeSchlafziel(context: Context, minuten: Int) {
        val gehalten = minuten.coerceIn(SCHLAF_KLEINSTES, SCHLAF_GROESSTES)
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .edit().putInt(SCHLAF, gehalten).apply()
    }

    // --- Wann ein Tag anfaengt ---

    /**
     * Mitternacht, bis jemand etwas anderes sagt.
     *
     * WER UM ZWEI UHR NOCH WACH IST, hat seine Schritte am Vortag gemacht -
     * der Kalender sieht das anders. Mit einer eigenen Grenze zaehlt ein Tag
     * von sechs bis sechs, und die Nacht gehoert dem Tag, an dem sie begann.
     */
    const val GRENZE_VORGABE = 0
    const val GRENZE_FRUEHESTE = 0
    const val GRENZE_SPAETESTE = 12

    fun tagesgrenze(context: Context): Int =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getInt(GRENZE, GRENZE_VORGABE)

    fun setzeTagesgrenze(context: Context, stunde: Int) {
        val gehalten = stunde.coerceIn(GRENZE_FRUEHESTE, GRENZE_SPAETESTE)
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .edit().putInt(GRENZE, gehalten).apply()
    }

    /**
     * Welcher Tag gerade laeuft.
     *
     * DIE EINE STELLE, an der "heute" entschieden wird. Ueberall sonst wird
     * sie gefragt - haette jede Rechnung ihr eigenes LocalDate.now(), stuende
     * um halb sechs morgens in einem Bild der eine und im naechsten der andere
     * Tag.
     */
    fun heute(context: Context): LocalDate {
        val jetzt = LocalDateTime.now()
        val grenze = tagesgrenze(context)
        return if (jetzt.hour >= grenze) jetzt.toLocalDate()
               else jetzt.toLocalDate().minusDays(1)
    }

    // --- Was ein Kartenlink ausloest ---

    /**
     * Ort zeigen oder gleich losfahren.
     *
     * ZEIGEN IST DIE VORGABE. Eine Fuehrung, die von selbst anspringt, nimmt
     * eine Entscheidung vorweg: welche Route, welches Profil, und ueberhaupt -
     * ob jetzt gefahren wird. Wer auf einen Link tippt, will meistens erst
     * sehen, wo das ist. Der Weg dahin ist danach ein Tipp entfernt.
     */
    const val FUEHRT_VORGABE = false

    fun kartenlinkFuehrt(context: Context): Boolean =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getBoolean(FUEHRT, FUEHRT_VORGABE)

    fun setzeKartenlinkFuehrt(context: Context, fuehrt: Boolean) {
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .edit().putBoolean(FUEHRT, fuehrt).apply()
    }
    // --- Die Sicherung ---

    private const val DAV_URL = "sicherung_url"
    private const val DAV_NUTZER = "sicherung_nutzer"
    private const val DAV_TAEGLICH = "sicherung_taeglich"
    private const val DAV_ZULETZT = "sicherung_zuletzt"
    private const val DAV_SPUREN = "sicherung_spuren"

    fun sicherungUrl(context: Context): String =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getString(DAV_URL, "").orEmpty()

    fun sicherungNutzer(context: Context): String =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getString(DAV_NUTZER, "").orEmpty()

    fun setzeSicherungZugang(context: Context, url: String, nutzer: String) {
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).edit()
            .putString(DAV_URL, url.trim())
            .putString(DAV_NUTZER, nutzer.trim())
            .apply()
    }

    fun sicherungTaeglich(context: Context): Boolean =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getBoolean(DAV_TAEGLICH, false)

    fun setzeSicherungTaeglich(context: Context, an: Boolean) {
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .edit().putBoolean(DAV_TAEGLICH, an).apply()
    }

    /** Wann zuletzt gesichert wurde, in Millisekunden. 0 = noch nie. */
    fun sicherungZuletzt(context: Context): Long =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getLong(DAV_ZULETZT, 0L)

    fun setzeSicherungZuletzt(context: Context, wann: Long) {
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .edit().putLong(DAV_ZULETZT, wann).apply()
    }

    /**
     * Welche Spuren schon oben liegen.
     *
     * DAMIT NICHT JEDEN TAG DASSELBE MEGABYTE HINAUFGEHT. Eine Spur aendert
     * sich nach dem Training nicht mehr; einmal hochgeladen ist sie fertig.
     */
    fun gesicherteSpuren(context: Context): Set<String> =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getStringSet(DAV_SPUREN, emptySet()).orEmpty()

    fun merkeGesicherteSpur(context: Context, beginn: Long) {
        val laden = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
        // Die Menge muss KOPIERT werden: getStringSet gibt die gespeicherte
        // Menge selbst zurueck, und wer sie aendert, aendert sie hinter dem
        // Ruecken der Ablage - beim naechsten Start stuende der alte Stand da.
        val neu = laden.getStringSet(DAV_SPUREN, emptySet()).orEmpty().toMutableSet()
        neu += beginn.toString()
        laden.edit().putStringSet(DAV_SPUREN, neu).apply()
    }

    /**
     * Die Einstellungen als Text - fuer die Sicherung.
     *
     * OHNE ZUGANGSDATEN. Eine Sicherung, die das Passwort ihres eigenen
     * Ablageorts enthaelt, waere ein Schluessel, der im Schloss steckt.
     */
    fun alsText(context: Context): Map<String, String> = mapOf(
        SCHLAF to schlafziel(context).toString(),
        GRENZE to tagesgrenze(context).toString(),
        FUEHRT to kartenlinkFuehrt(context).toString(),
    )
}
