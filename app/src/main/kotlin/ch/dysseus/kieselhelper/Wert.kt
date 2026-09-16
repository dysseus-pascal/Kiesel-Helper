package ch.dysseus.kieselhelper

/**
 * Ein Wert, wie ihn eine Quelle liefert.
 *
 * Bis Fassung 1 war jeder Wert eine ganze Zahl - mehr schickt eine Uhr-App
 * nicht. Mit den Benachrichtigungen als Quelle kommt Text dazu: Strassennamen,
 * Anweisungen, Absender. Deshalb zwei Sorten statt einer.
 *
 * Die Umwandlung zwischen beiden steht hier und nirgends sonst, damit es genau
 * eine Stelle gibt, an der aus "250 m" die Zahl 250 wird.
 */
sealed class Wert {

    data class Zahl(val zahl: Long) : Wert()

    data class Text(val text: String) : Wert()

    fun alsText(): String = when (this) {
        is Zahl -> zahl.toString()
        is Text -> text
    }

    /**
     * Den Wert als Zahl lesen.
     *
     * `muster` ist ein regulaerer Ausdruck mit einer Fanggruppe; er wird
     * gebraucht, weil Benachrichtigungen Zahlen im Fliesstext tragen
     * ("250 m", "in 1,2 km"). Ohne Muster wird der ganze Text gelesen.
     * `faktor` rechnet Einheiten um (km -> m: 1000).
     *
     * Rueckgabe null heisst: da steht keine Zahl. Das ist ein Grund, die Regel
     * NICHT anzuwenden - nicht ein Grund, null einzutragen.
     */
    fun alsZahl(muster: Regex?, faktor: Double): Long? {
        val roh: String = when (this) {
            is Zahl -> if (muster == null) return skaliere(zahl.toDouble(), faktor) else zahl.toString()
            is Text -> text
        }
        val gefangen = if (muster == null) roh else {
            val treffer = muster.find(roh) ?: return null
            // Die erste Fanggruppe, sonst der ganze Treffer.
            treffer.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: treffer.value
        }
        // Komma als Dezimaltrennzeichen kommt in deutschen Benachrichtigungen
        // vor ("1,2 km"); Leerzeichen als Tausendertrennung auch.
        val sauber = gefangen.trim().replace(",", ".").replace(" ", "").replace(" ", "")
        val d = sauber.toDoubleOrNull() ?: return null
        return skaliere(d, faktor)
    }

    private fun skaliere(d: Double, faktor: Double): Long = Math.round(d * faktor)

    companion object {
        /** Aus einem beliebigen Objekt aus einem Notification-Bundle einen Wert machen. */
        fun aus(roh: Any?): Wert? = when (roh) {
            null -> null
            is CharSequence -> roh.toString().trim().ifEmpty { null }?.let { Text(it) }
            is Int -> Zahl(roh.toLong())
            is Long -> Zahl(roh)
            is Short -> Zahl(roh.toLong())
            is Byte -> Zahl(roh.toLong())
            is Boolean -> Zahl(if (roh) 1L else 0L)
            is Float -> Zahl(Math.round(roh.toDouble()))
            is Double -> Zahl(Math.round(roh))
            else -> null
        }
    }
}
