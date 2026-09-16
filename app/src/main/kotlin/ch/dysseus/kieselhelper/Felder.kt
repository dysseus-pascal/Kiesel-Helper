package ch.dysseus.kieselhelper

/**
 * Die Feldnamen, die eine Benachrichtigung als Quelle anbietet.
 *
 * An EINER Stelle, weil zwei Teile sie kennen muessen und sich nicht
 * widersprechen duerfen: [Modul] prueft beim Einlesen, ob ein Zettel nur
 * Felder nennt, die es gibt, und [BenachrichtigungsHorcher] fuellt sie.
 * Stuenden die Namen zweimal da, faende man den Tippfehler erst, wenn nachts
 * nichts ankommt.
 */
object Benachrichtigungsfeld {
    const val PAKET = "paket"
    const val TITEL = "titel"
    const val TEXT = "text"
    const val UNTERTEXT = "untertext"
    const val GROSSTEXT = "grosstext"
    const val ZUSATZ = "zusatz"
    const val TICKER = "ticker"
    const val WANN = "wann"
    const val DAUERHAFT = "dauerhaft"

    val bekannt: Set<String> = setOf(
        PAKET, TITEL, TEXT, UNTERTEXT, GROSSTEXT, ZUSATZ, TICKER, WANN, DAUERHAFT,
    )

    /**
     * Zusaetzlich ist jedes Extra der Benachrichtigung unter `extra:<name>`
     * erreichbar. Das ist die Hintertuer fuer Apps, die ihre eigentliche
     * Angabe nicht in Titel oder Text legen - und es ist die Stelle, an der
     * ein Zettel auf eine App zugeschnitten wird, ohne dass die App davon
     * etwas wissen muss.
     */
    const val EXTRA_VORSATZ = "extra:"

    fun gueltig(name: String): Boolean =
        name in bekannt || name.startsWith(EXTRA_VORSATZ) || name == JETZT
}

/**
 * Ein Feld, das JEDE Quelle mitliefert: der Augenblick des Empfangs, in
 * Sekunden.
 *
 * Gedacht fuer Quellen, die selbst keinen Zeitstempel tragen. Es ist der
 * schlechtere Zeitpunkt - der des Empfangs, nicht der der Messung -, aber
 * manchmal der einzige. Ein Zettel, der `zeitpunkt` aus einem echten Feld
 * nehmen kann, sollte das tun.
 */
const val JETZT = "jetzt"
