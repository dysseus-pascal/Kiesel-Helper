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
}
