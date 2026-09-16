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
     * Der Riegel haengt am Zettel, an der Regel und am Wert des Feldes, das
     * unter `nicht_zweimal_fuer` steht — meist ein Zeitstempel. Zweimal
     * derselbe heisst: dieselbe Sache, nicht eine neue.
     *
     * Seit die Benachrichtigungen dazugekommen sind, ist der Riegel ein TEXT
     * und keine Zahl mehr. Bei einer Navigationsanweisung ist das Merkmal der
     * Strassenname — der wiederholt sich im Sekundentakt, und ohne Riegel
     * ginge jede Sekunde eine Nachricht an die Uhr.
     */
    fun schonGetan(modul: String, regelNr: Int, merkmal: String): Boolean {
        return prefs.getString(riegel(modul, regelNr), null) == merkmal
    }

    fun merkeGetan(modul: String, regelNr: Int, merkmal: String) {
        prefs.edit().putString(riegel(modul, regelNr), merkmal).apply()
    }

    private fun riegel(modul: String, regelNr: Int) = "riegel_${modul}_$regelNr"

    // --- Takt ---

    /**
     * Wann diese Regel zuletzt gesendet hat, in Sekunden.
     *
     * Getrennt vom Riegel oben: der fragt "dasselbe schon einmal?", dieser
     * fragt "schon wieder so bald?". Eine Navigationsanweisung braucht beides —
     * ihre Entfernung ändert sich ständig, soll aber nicht jede Sekunde über
     * Bluetooth gehen.
     */
    fun zuletztGesendet(modul: String, regelNr: Int): Long =
        prefs.getLong(takt(modul, regelNr), 0L)

    fun merkeGesendet(modul: String, regelNr: Int) {
        prefs.edit().putLong(takt(modul, regelNr), System.currentTimeMillis() / 1000).apply()
    }

    private fun takt(modul: String, regelNr: Int) = "takt_${modul}_$regelNr"

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
