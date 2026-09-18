package ch.dysseus.kieselhelper

/**
 * Zahlen lesbar machen - an einer Stelle fuer App und Widget.
 *
 * SIE MUESSEN GLEICH AUSSEHEN. Schirm und Widget stehen nebeneinander auf
 * demselben Telefon; steht dort "7 812" und da "7812,0", sieht es aus, als
 * redeten zwei Apps ueber zwei Dinge.
 *
 * `null` heisst durchweg: nichts in der Akte. Es kommt als `null` zurueck und
 * wird erst dort zu einem Strich, wo gezeichnet wird - eine Null waere eine
 * Behauptung.
 */
object Zahlen {

    /**
     * Ganze Zahl mit einem schmalen Abstand als Tausendertrennung.
     *
     * Ein Punkt waere hier gefaehrlich: "7.812" liest sich im Deutschen als
     * Tausender, im Englischen als Komma. Ein Leerzeichen liest jeder gleich.
     */
    fun ganz(d: Double?): String? =
        d?.let { String.format("%,.0f", it).replace(',', ' ') }

    fun eine(d: Double?): String? = d?.let { String.format("%.1f", it) }

    /** Minuten als "6 h 40" - eine Schlafdauer in Minuten liest niemand. */
    fun dauer(min: Double?): String? {
        val m = min?.toInt() ?: return null
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60}"
    }
}
