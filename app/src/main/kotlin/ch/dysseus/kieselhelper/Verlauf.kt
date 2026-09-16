package ch.dysseus.kieselhelper

import android.content.Context

/**
 * Merkt sich, was zuletzt ankam und was damit geschah.
 *
 * Kein Verlauf im eigentlichen Sinn — der gehoert in die Gesundheitsakte, und
 * dort steht er auch. Das hier ist eine Statusanzeige: ohne sie sieht man einer
 * App, die im Kern aus einem Empfaenger besteht, von aussen nie an, ob sie
 * ueberhaupt etwas tut.
 *
 * Eine zweite Aufgabe hat der Speicher: er weist DOPPELTE Nachrichten ab. Eine
 * erneut zugestellte Nachricht — etwa nach einer verlorenen Bestaetigung — darf
 * kein zweites Glas und keine zweite Messung in die Akte schreiben.
 */
class Verlauf(context: Context) {

    private val prefs = context.getSharedPreferences("kiesel-verlauf", Context.MODE_PRIVATE)

    /**
     * Schon eingetragen?
     *
     * Der Riegel haengt an UUID, Regel und dem Wert des Feldes, das die
     * Beschreibung unter `nicht_zweimal_fuer` nennt — meist ein Zeitstempel.
     * Zweimal derselbe Zeitstempel heisst: dieselbe Messung, nicht eine neue.
     */
    fun schonGetan(uuid: String, regelNr: Int, merkmal: Long): Boolean {
        return prefs.getLong(riegel(uuid, regelNr), Long.MIN_VALUE) == merkmal
    }

    fun merkeGetan(uuid: String, regelNr: Int, merkmal: Long) {
        prefs.edit().putLong(riegel(uuid, regelNr), merkmal).apply()
    }

    private fun riegel(uuid: String, regelNr: Int) = "riegel_${uuid}_$regelNr"

    // --- Statusanzeige ---

    fun merkeMeldung(text: String) {
        prefs.edit()
            .putString("meldung", text)
            .putLong("meldung_am", System.currentTimeMillis() / 1000)
            .apply()
    }

    fun letzteMeldung(): String = prefs.getString("meldung", "") ?: ""

    fun letzteMeldungAm(): Long = prefs.getLong("meldung_am", 0L)
}
