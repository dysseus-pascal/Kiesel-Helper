package ch.dysseus.kieselhelper

import android.content.Context
import java.time.LocalDate

/**
 * Was heute ansteht - namentlich.
 *
 * WARUM NICHT IN DEN TAGESSPEICHER. Dort steht je Tag eine Zahl; hier stehen
 * Namen und zwei Bitmasken, und beides gilt nur fuer HEUTE. Eine Liste
 * abgehakter Praeparate von vorletztem Dienstag hat niemand je gebraucht - die
 * Quote von damals schon, und die steht in [Speicher].
 *
 * DIE STELLE IST DIE AUSSAGE. SupCycle schickt sechs Plaetze, durch
 * Zeilenumbruch getrennt, auch die leeren: die Bitmasken zaehlen Plaetze, nicht
 * Eintraege. Wer die leeren wegliesse, verschoebe jeden Namen dahinter - und
 * das Magnesium hiesse dann Zink.
 */
object Supplemente {

    private const val DATEI = "kiesel-supplemente"

    /** Ein Praeparat, das heute ansteht. */
    data class Eintrag(val name: String, val genommen: Boolean)

    data class Stand(
        val tag: LocalDate,
        val namen: List<String>,
        val faellig: Long,
        val genommen: Long,
    ) {
        /**
         * Nur was heute faellig ist, in der Reihenfolge des Plans.
         *
         * Ein Praeparat in der Pausenwoche gehoert nicht auf die Liste. Es
         * fehlt nicht, es ist nicht dran - und ein "offen" daneben waere ein
         * Vorwurf.
         */
        fun heute(context: Context): List<Eintrag> =
            namen.indices
                .filter { ((faellig shr it) and 1L) == 1L }
                .map { i ->
                    Eintrag(
                        namen[i].ifBlank { context.getString(R.string.platz_n, i + 1) },
                        ((genommen shr i) and 1L) == 1L,
                    )
                }
    }

    fun merke(
        context: Context,
        tag: LocalDate,
        namen: List<String>,
        faellig: Long,
        genommen: Long,
    ) {
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).edit().apply {
            putString("tag", tag.toString())
            // Nur ueberschreiben, wenn welche kamen: eine aeltere Fassung von
            // SupCycle schickt keine, und dann sollen die zuletzt bekannten
            // stehen bleiben statt zu verschwinden.
            if (namen.isNotEmpty()) putString("namen", namen.joinToString("\n"))
            putLong("faellig", faellig)
            putLong("genommen", genommen)
            apply()
        }
    }

    /** Der Stand von HEUTE, oder nichts. Gestern ist hier nicht interessant. */
    fun lies(context: Context): Stand? {
        val laden = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
        val tag = laden.getString("tag", null) ?: return null
        val datum = try {
            LocalDate.parse(tag)
        } catch (e: Exception) {
            return null
        }
        // An der eingestellten Tagesgrenze gemessen: wer um fuenf Uhr
        // morgens aufs Widget schaut, sieht sonst eine leere Liste, obwohl
        // sein Tag noch laeuft.
        if (datum != Einstellungen.heute(context)) return null
        return Stand(
            datum,
            laden.getString("namen", "")?.split("\n").orEmpty(),
            laden.getLong("faellig", 0),
            laden.getLong("genommen", 0),
        )
    }
}
