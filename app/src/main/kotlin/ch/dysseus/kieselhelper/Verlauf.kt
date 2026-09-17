package ch.dysseus.kieselhelper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    // --- Statusanzeige und Verlauf ---

    /**
     * Was geschehen ist - und zwar nicht nur das Letzte.
     *
     * WARUM DAS SEIN MUSS: was diese App tut, tut sie, wenn niemand hinsieht.
     * Bis hierher merkte sie sich genau eine Zeile, und alles davor war nur im
     * Logbuch des Systems zu finden. Das reicht nicht: der Logcat-Puffer haelt
     * Stunden, nicht Tage, und eine Deinstallation nimmt den Prozess mit.
     * Genau daran ist die Auswertung einer Autofahrt gescheitert - die Fahrt
     * war vorbei, die Spur weg.
     *
     * Der Verlauf steht deshalb IN der App. Er ueberlebt Neustarts, braucht
     * kein Kabel und keinen Rechner, und man kann ihn lesen, wo man gerade
     * steht.
     */
    fun merkeMeldung(text: String) {
        val jetzt = System.currentTimeMillis() / 1000
        val liste = JSONArray(prefs.getString(VERLAUF, "[]") ?: "[]")
        liste.put(JSONObject().put("t", jetzt).put("m", text))
        // Vorne abschneiden: die aeltesten fliegen raus, nicht die neuesten.
        while (liste.length() > VERLAUF_MAX) liste.remove(0)
        prefs.edit()
            .putString("meldung", text)
            .putLong("meldung_am", jetzt)
            .putString(VERLAUF, liste.toString())
            .apply()
    }

    fun letzteMeldung(): String = prefs.getString("meldung", "") ?: ""

    fun letzteMeldungAm(): Long = prefs.getLong("meldung_am", 0L)

    data class Zeile(val am: Long, val text: String)

    /** Der Verlauf, das Neueste zuerst. */
    fun verlauf(): List<Zeile> {
        val aus = mutableListOf<Zeile>()
        try {
            val a = JSONArray(prefs.getString(VERLAUF, "[]") ?: "[]")
            for (i in a.length() - 1 downTo 0) {
                val o = a.optJSONObject(i) ?: continue
                aus.add(Zeile(o.optLong("t", 0L), o.optString("m", "")))
            }
        } catch (e: Exception) {
            // Ein verdorbener Verlauf darf die Anzeige nicht aufhalten.
            return emptyList()
        }
        return aus
    }

    fun leereVerlauf() {
        prefs.edit().remove(VERLAUF).apply()
    }

    private companion object {
        const val VERLAUF = "verlauf"

        /**
         * Wie viele Zeilen aufgehoben werden.
         *
         * Bei einer Navigation kommt hoechstens alle zehn Sekunden eine - das
         * sind rund siebzehn Minuten Fahrt. Genug, um ein Muster zu sehen, und
         * wenig genug, dass die Ablage eine Kleinigkeit bleibt.
         */
        const val VERLAUF_MAX = 100
    }
}
