package ch.dysseus.kieselhelper

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Zahlen lesbar machen - an einer Stelle fuer App und Widget.
 *
 * SIE MUESSEN GLEICH AUSSEHEN. Schirm und Widget stehen nebeneinander auf
 * demselben Telefon; steht dort "7 812" und da "7812,0", sieht es aus, als
 * redeten zwei Apps ueber zwei Dinge.
 *
 * DIE SCHREIBWEISE FOLGT DER SPRACHE DES TELEFONS. Frueher stand hier fest
 * ein Leerzeichen als Tausendertrenner und ein 24-Stunden-Format - richtig
 * fuer die Schweiz, fremd ueberall sonst. Jetzt kommt beides aus der Locale:
 * "7.812" auf Deutsch, "7’812" in der Schweiz, "7,812" auf Englisch. Weil App
 * und Widget dieselbe Locale haben, sehen sie weiterhin gleich aus.
 *
 * `null` heisst durchweg: nichts in der Akte. Es kommt als `null` zurueck und
 * wird erst dort zu einem Strich, wo gezeichnet wird - eine Null waere eine
 * Behauptung.
 */
object Zahlen {

    /** Ganze Zahl mit dem Tausendertrenner der Sprache. */
    fun ganz(d: Double?): String? = d?.let { String.format("%,.0f", it) }

    fun eine(d: Double?): String? = d?.let { String.format("%.1f", it) }

    /** Zwei Stellen - fuer Groessen, die zwischen -1 und 1 leben. */
    fun zwei(d: Double): String = String.format("%.2f", d)

    /** Minuten als "6 h 40" - eine Schlafdauer in Minuten liest niemand. */
    fun dauer(min: Double?): String? {
        val m = min?.toInt() ?: return null
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60}"
    }

    /** Minuten seit Mitternacht als Uhrzeit - "07:30" oder "7:30 AM", je nach Sprache. */
    fun uhrzeit(minuten: Double?): String? {
        val m = minuten?.toInt() ?: return null
        // floorMod statt %: LocalTime nimmt keine negative Stunde.
        val t = Math.floorMod(m, 1440)
        return zeit(t / 60, t % 60)
    }

    private fun zeit(stunde: Int, minute: Int): String =
        LocalTime.of(stunde, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    /**
     * Minuten seit achtzehn Uhr zurueck in eine Uhrzeit.
     *
     * Nachtzeiten werden ab 18 Uhr gerechnet, damit Mitternacht keine Kante
     * ist. Lesen will man sie trotzdem als Uhrzeit.
     */
    fun uhrzeitAb18(minuten: Double?): String? {
        val m = minuten?.toInt() ?: return null
        val tagesminute = ((18 * 60 + m) % 1440 + 1440) % 1440
        return zeit(tagesminute / 60, tagesminute % 60)
    }
}
