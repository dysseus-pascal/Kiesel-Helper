package ch.dysseus.kieselhelper

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Der gesicherte Stand als Text - und wieder zurück.
 *
 * WARUM ES DIESE SICHERUNG ÜBERHAUPT GIBT: die Gesundheitsakte hält rund
 * dreissig Tage. Alles, was diese App an Wochenprofilen und Zusammenhängen
 * rechnet, steht danach nur noch in ihrer eigenen Tabelle — und die liegt in
 * den App-Daten eines einzigen Telefons. Ein Wechsel, ein Rücksetzen, ein
 * kaputtes Gerät, und ein Jahr Aufzeichnung ist weg.
 *
 * ALS EINE DATEI UND ALS KLARTEXT. Ein JSON, das man öffnen und lesen kann,
 * ist auch dann noch etwas wert, wenn es diese App nicht mehr gibt. Ein
 * SQLite-Abzug wäre kleiner und in fünf Jahren ein Rätsel.
 *
 * HIER STEHT NUR DAS UMWANDELN, kein Netz und kein Android: so lässt sich der
 * Teil prüfen, an dem ein Fehler wehtut. Wer eine Sicherung schreibt, die
 * sich nicht zurücklesen lässt, merkt es genau einmal — dann, wenn er sie
 * braucht.
 */
object Sicherung {

    /**
     * Die Fassung des Formats.
     *
     * SIE STEHT IN JEDER DATEI. Eine Sicherung überlebt die App, die sie
     * geschrieben hat; ohne diese Zahl müsste eine spätere Fassung raten,
     * was sie vor sich hat.
     */
    const val FASSUNG = 1

    data class Stand(
        val fassung: Int,
        val erzeugt: String,
        val tage: List<Pair<LocalDate, Map<String, Double>>>,
        val einstellungen: Map<String, String>,
        val spuren: List<Long>,
    )

    /**
     * Den Stand als JSON schreiben.
     *
     * DIE SPUREN STEHEN NUR ALS LISTE DARIN, nicht als Inhalt: eine Stunde
     * Laufen sind tausend Punkte, und zwanzig Läufe in einer Datei wären ein
     * Klotz, den niemand mehr über eine Leitung bekommt. Die Punkte liegen
     * daneben, eine Datei je Training — genau so, wie sie auf dem Telefon
     * liegen.
     */
    fun alsJson(
        tage: List<Pair<LocalDate, Map<String, Double>>>,
        einstellungen: Map<String, String>,
        spuren: List<Long>,
        erzeugt: String,
    ): String {
        val wurzel = JSONObject()
        wurzel.put("fassung", FASSUNG)
        wurzel.put("erzeugt", erzeugt)
        wurzel.put("app", "Kiesel-Helper")

        val liste = JSONArray()
        tage.forEach { (tag, werte) ->
            val z = JSONObject()
            z.put("datum", tag.toString())
            werte.toSortedMap().forEach { (spalte, wert) -> z.put(spalte, wert) }
            liste.put(z)
        }
        wurzel.put("tage", liste)

        val e = JSONObject()
        einstellungen.toSortedMap().forEach { (schluessel, wert) -> e.put(schluessel, wert) }
        wurzel.put("einstellungen", e)

        val s = JSONArray()
        spuren.sorted().forEach { s.put(it) }
        wurzel.put("spuren", s)

        return wurzel.toString(2)
    }

    /**
     * Den Stand wieder auseinandernehmen.
     *
     * STRENG BEIM LESEN: was keinen gültigen Tag trägt oder keine Zahl ist,
     * fällt weg. Eine halb übertragene Datei soll die Tabelle nicht mit
     * Unsinn füllen — lieber fehlen drei Tage als dass ein Jahr schief steht.
     *
     * Gibt null zurück, wenn es gar keine Sicherung ist.
     */
    fun ausJson(text: String?): Stand? {
        if (text.isNullOrBlank()) return null
        return try {
            val wurzel = JSONObject(text)
            val fassung = wurzel.optInt("fassung", 0)
            if (fassung <= 0) return null

            val tage = mutableListOf<Pair<LocalDate, Map<String, Double>>>()
            val liste = wurzel.optJSONArray("tage") ?: JSONArray()
            for (i in 0 until liste.length()) {
                val z = liste.optJSONObject(i) ?: continue
                val datum = try {
                    LocalDate.parse(z.optString("datum"))
                } catch (e: Exception) {
                    continue
                }
                val werte = mutableMapOf<String, Double>()
                // NUR BEKANNTE SPALTEN. Eine Sicherung aus einer spaeteren
                // Fassung kann Felder mitbringen, die es hier nicht gibt; sie
                // in die Tabelle zu schreiben ginge schief.
                Speicher.SPALTEN.forEach { spalte ->
                    if (z.has(spalte) && !z.isNull(spalte)) {
                        val wert = z.optDouble(spalte, Double.NaN)
                        if (!wert.isNaN()) werte[spalte] = wert
                    }
                }
                if (werte.isNotEmpty()) tage += datum to werte.toMap()
            }

            val einstellungen = mutableMapOf<String, String>()
            wurzel.optJSONObject("einstellungen")?.let { e ->
                e.keys().forEach { schluessel -> einstellungen[schluessel] = e.optString(schluessel) }
            }

            val spuren = mutableListOf<Long>()
            wurzel.optJSONArray("spuren")?.let { s ->
                for (i in 0 until s.length()) {
                    val wert = s.optLong(i, 0L)
                    if (wert > 0) spuren += wert
                }
            }

            Stand(
                fassung = fassung,
                erzeugt = wurzel.optString("erzeugt"),
                tage = tage,
                einstellungen = einstellungen,
                spuren = spuren,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Was beim Zurücklesen wirklich geschrieben wird.
     *
     * ERGÄNZEN, NICHT ÜBERSCHREIBEN. Eine Sicherung ist älter als das, was
     * gerade auf dem Telefon steht — sonst bräuchte man sie nicht. Sie über
     * den heutigen Stand zu legen hiesse, die letzten Tage gegen alte Zahlen
     * zu tauschen. Geschrieben wird deshalb nur, wo lokal NICHTS steht.
     *
     * Das ist zugleich die Antwort auf "was, wenn ich zwei Telefone habe":
     * beide ergänzen einander, keines löscht das andere.
     */
    fun nurLuecken(
        aus: List<Pair<LocalDate, Map<String, Double>>>,
        vorhanden: Map<LocalDate, Map<String, Double>>,
    ): List<Pair<LocalDate, Map<String, Double>>> {
        val ergebnis = mutableListOf<Pair<LocalDate, Map<String, Double>>>()
        aus.forEach { (tag, werte) ->
            val hier = vorhanden[tag].orEmpty()
            val fehlend = werte.filterKeys { it !in hier.keys }
            if (fehlend.isNotEmpty()) ergebnis += tag to fehlend
        }
        return ergebnis
    }

    /** Der Name, unter dem die Sicherung auf dem Server liegt. */
    const val DATEINAME = "kiesel-helper.json"

    /** Der Ordner für die Spuren, unterhalb des gewählten Ordners. */
    const val SPURORDNER = "spuren"

    fun spurname(beginn: Long): String = "spur-$beginn.jsonl"
}
