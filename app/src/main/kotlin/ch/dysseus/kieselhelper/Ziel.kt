package ch.dysseus.kieselhelper

/**
 * Wohin eine Sicherung geht - ein WebDAV-Ordner oder ein Ordner auf dem
 * Telefon. Beide koennen dasselbe: einen Ordner anlegen, eine Datei ablegen,
 * eine Datei holen.
 *
 * WARUM ES ZWEI GIBT: WebDAV ist der Weg ohne fremde App - aber jeder Server
 * hat seine Eigenheiten, und wer an mailbox.org oder Icedrive scheitert, hat
 * die Sicherung nicht. Ein Ordner auf dem Telefon geht immer; dorthin
 * schreibt diese App, und die Cloud-App des Anbieters traegt ihn hinauf.
 */
interface Ziel {
    /** Erreichbar und beschreibbar? Ein fehlender Ordner wird angelegt. */
    fun pruefe(): WebDav.Ergebnis
    /** Einen Unterordner anlegen; leer heisst: den Zielordner selbst. */
    fun ordner(name: String = ""): WebDav.Ergebnis
    fun lege(name: String, inhalt: ByteArray, typ: String = "application/json"): WebDav.Ergebnis
    /** Eine Datei als Text; null heisst: gibt es nicht oder ging nicht. */
    fun hole(name: String): String?
    /** Wie das Ziel in Meldungen heisst. */
    val name: String
}
