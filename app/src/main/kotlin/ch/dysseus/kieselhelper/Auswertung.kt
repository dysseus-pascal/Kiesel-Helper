package ch.dysseus.kieselhelper

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Aus Tagen ein Muster machen.
 *
 * REINE RECHNUNG, kein Android: nichts hier kennt eine Datenbank oder einen
 * Bildschirm. Das ist Absicht - es ist die einzige Stelle der App, die eine
 * Aussage BEHAUPTET ("dein Mittwoch ist schwach"), und Behauptungen sollte man
 * ohne Telefon nachrechnen koennen. Die Pruefungen dazu stehen in
 * `AuswertungTest`.
 *
 * HEUTE ZAEHLT NICHT MIT. Ein angefangener Tag hat immer zu wenig Schritte;
 * wer ihn einrechnet, bekommt ein Wochenprofil, in dem der heutige Wochentag
 * immer der schwaechste ist - und das Muster waere ein Abbild der Uhrzeit, zu
 * der man hinschaut.
 */
object Auswertung {

    /** Ein Wochentag im Mittel. `mittel == null` heisst: kein einziger Tag. */
    data class Profilwert(val tag: DayOfWeek, val mittel: Double?, val anzahl: Int)

    /** Eine Kalenderwoche im Mittel, benannt nach ihrem Montag. */
    data class Wochenwert(val montag: LocalDate, val mittel: Double, val anzahl: Int)

    data class Bild(
        val profil: List<Profilwert>,
        val gesamt: Double?,
        val anzahl: Int,
        val staerkster: Profilwert?,
        val schwaechster: Profilwert?,
        val wochen: List<Wochenwert>,
        /** Letzte vier Wochen gegen die vier davor, in Prozent. */
        val veraenderung: Double?,
    )

    /**
     * Wie viele Tage ein Wochentag braucht, bevor er "typisch" heissen darf.
     *
     * Aus einem einzigen Mittwoch ein Muster zu lesen ist keine Auswertung,
     * sondern eine Erinnerung. Zwei sind auch noch duenn, aber ab da wird das
     * Warten laenger als der Nutzen: es dauert drei Wochen, bis das Bild
     * ueberhaupt etwas zeigen darf.
     */
    const val MINDESTENS = 2

    /** Wie viele Wochen der Verlauf zeigt. */
    const val WOCHEN = 8

    fun bild(reihe: List<Pair<LocalDate, Double>>, heute: LocalDate): Bild {
        val tage = reihe.filter { it.first < heute }

        val profil = DayOfWeek.values().map { wochentag ->
            val treffer = tage.filter { it.first.dayOfWeek == wochentag }
            Profilwert(
                wochentag,
                if (treffer.size >= MINDESTENS) treffer.map { it.second }.average() else null,
                treffer.size,
            )
        }
        val gezaehlt = profil.filter { it.mittel != null }

        val wochen = tage.groupBy { it.first.with(DayOfWeek.MONDAY) }
            .map { (montag, werte) ->
                Wochenwert(montag, werte.map { it.second }.average(), werte.size)
            }
            .sortedBy { it.montag }
            .takeLast(WOCHEN)

        return Bild(
            profil = profil,
            gesamt = if (tage.isEmpty()) null else tage.map { it.second }.average(),
            anzahl = tage.size,
            staerkster = gezaehlt.maxByOrNull { it.mittel!! },
            schwaechster = gezaehlt.minByOrNull { it.mittel!! },
            wochen = wochen,
            veraenderung = veraenderung(tage, heute),
        )
    }

    /**
     * Die Richtung: vier Wochen gegen vier Wochen.
     *
     * VIER UND NICHT EINE. Eine Woche gegen die vorige ist Rauschen - ein
     * Feiertag, eine Erkaeltung, ein Wochenende weg, und die Zahl springt um
     * dreissig Prozent. Vier Wochen gegen vier Wochen ueberlebt einen
     * einzelnen schlechten Tag.
     *
     * Beide Haelften brauchen genug Tage; sonst vergleicht man eine volle
     * Woche mit drei Tagen und nennt den Unterschied einen Trend.
     */
    private fun veraenderung(
        tage: List<Pair<LocalDate, Double>>,
        heute: LocalDate,
    ): Double? {
        val jung = tage.filter { it.first >= heute.minusDays(28) }
        val alt = tage.filter {
            it.first >= heute.minusDays(56) && it.first < heute.minusDays(28)
        }
        if (jung.size < 7 || alt.size < 7) return null
        val a = alt.map { it.second }.average()
        if (a == 0.0) return null
        return (jung.map { it.second }.average() - a) / a * 100
    }
}
