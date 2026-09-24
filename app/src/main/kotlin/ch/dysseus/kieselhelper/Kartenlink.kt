package ch.dysseus.kieselhelper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Was in einem Kartenlink steckt.
 *
 * REINE ZERLEGUNG, KEIN ANDROID - und deshalb ohne Telefon pruefbar. Der
 * erste Entwurf nahm android.net.Uri; das haette bedeutet, die Zerlegung nur
 * am Geraet pruefen zu koennen, denn in einem Test ist Uri eine Attrappe, die
 * null zurueckgibt. Ein paar Zeilen Zeichenkettenarbeit sind der Preis dafuer,
 * dass ein Dutzend Linkformen nachrechenbar bleibt - und das ist hier mehr
 * wert als anderswo: Google schreibt seine Links in einem halben Dutzend
 * Formen, die sich ueber die Jahre geaendert haben, und ein Fehler faellt erst
 * im Auto auf.
 */
object Kartenlink {

    /** Ein Ziel: Koordinaten, ein Suchtext, oder beides. */
    data class Ziel(
        val lat: Double?,
        val lon: Double?,
        val text: String?,
    ) {
        val brauchbar: Boolean get() = (lat != null && lon != null) || !text.isNullOrBlank()
    }

    /**
     * Die Formen, die tatsaechlich vorkommen:
     *
     *  - `.../maps/dir/?api=1&destination=47.1,8.2` oder `destination=Name`
     *  - `.../maps/search/?api=1&query=47.1,8.2`
     *  - `.../maps/place/Name/@47.1,8.2,17z/...`
     *  - `.../maps/@47.1,8.2,15z`
     *  - `?daddr=47.1,8.2` (die alte Form, immer noch unterwegs)
     *  - `geo:47.1,8.2?q=Name` und `geo:0,0?q=Name`
     *
     * DAS AT-ZEICHEN IST NICHT IMMER DAS ZIEL. In einem `place`-Link ist es
     * der Kartenausschnitt, der meist auf dem Ort liegt - aber nicht immer.
     * Steht ein Name daneben, geht der vor: OsmAnd sucht ihn selbst und
     * trifft damit den Eingang statt der Bildmitte.
     */
    fun zerlege(roh: String): Ziel? {
        if (roh.isBlank()) return null

        if (roh.startsWith("geo:", ignoreCase = true)) {
            val rest = roh.substring(4)
            val koordinaten = rest.substringBefore('?')
            val frage = abfrage(roh, "q")
            val paar = alsPaar(koordinaten)
            // "geo:0,0?q=..." ist die uebliche Form fuer eine reine Suche.
            val echt = paar?.takeUnless { it.first == 0.0 && it.second == 0.0 }
            // Steht im q= eine Koordinate, gilt die.
            val ausFrage = frage?.let { alsPaar(it) }
            return Ziel(
                echt?.first ?: ausFrage?.first,
                echt?.second ?: ausFrage?.second,
                frage?.takeIf { ausFrage == null },
            ).takeIf { it.brauchbar }
        }

        // NUR AUS EINEM KARTENLINK. Ein "q=" gibt es auch in einer
        // Google-Suche, und daraus ein Navigationsziel zu machen waere
        // eine Anmassung - die Pruefung hat genau das gefunden.
        val istKarte = roh.contains("/maps", ignoreCase = true) ||
            roh.contains("maps.google.", ignoreCase = true) ||
            roh.contains("maps.app.goo.gl", ignoreCase = true)
        if (!istKarte) return null

        listOf("destination", "query", "daddr", "q").forEach { name ->
            abfrage(roh, name)?.takeIf { it.isNotBlank() }?.let { wert ->
                alsPaar(wert)?.let { return Ziel(it.first, it.second, null) }
                return Ziel(null, null, wert)
            }
        }

        // Ein Name im Pfad schlaegt den Kartenausschnitt: die Koordinate
        // hinter dem At-Zeichen ist die Bildmitte, nicht der Eingang.
        val name = Regex("/maps/place/([^/@?]+)").find(roh)?.groupValues?.get(1)
            ?.let { entschluessle(it.replace('+', ' ')) }
            ?.takeIf { it.isNotBlank() }

        val stelle = Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)").find(roh)
        val lat = stelle?.groupValues?.get(1)?.toDoubleOrNull()
        val lon = stelle?.groupValues?.get(2)?.toDoubleOrNull()

        return Ziel(lat, lon, name).takeIf { it.brauchbar }
    }

    /** Ein Wert aus der Abfrage hinter dem Fragezeichen. */
    private fun abfrage(roh: String, name: String): String? {
        val teil = roh.substringAfter('?', "")
        if (teil.isEmpty()) return null
        return teil.split('&')
            .firstOrNull { it.startsWith("$name=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.let { entschluessle(it.replace('+', ' ')) }
    }

    /** "47.1,8.2" - alles andere ist ein Name. */
    private fun alsPaar(text: String): Pair<Double, Double>? {
        val teile = entschluessle(text).split(',')
        if (teile.size != 2) return null
        val lat = teile[0].trim().toDoubleOrNull() ?: return null
        val lon = teile[1].trim().toDoubleOrNull() ?: return null
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
        return lat to lon
    }

    private fun entschluessle(text: String): String = try {
        URLDecoder.decode(text, "UTF-8")
    } catch (e: Exception) {
        text
    }

    /** Kurzlinks, die erst aufgeloest werden muessen. */
    fun istKurzlink(roh: String): Boolean =
        roh.contains("maps.app.goo.gl") || roh.contains("goo.gl/maps") ||
            roh.contains("g.co/kgs")

    /**
     * Einem Kurzlink folgen, bis er sich zu erkennen gibt.
     *
     * NUR DEN KOPF LESEN, nicht die Seite: gesucht ist das Ziel der
     * Umleitung, und eine Google-Maps-Seite ist ein Megabyte JavaScript.
     * Hoechstens fuenf Sprünge - eine Kette, die laenger ist, ist keine
     * Umleitung mehr, sondern eine Schleife.
     *
     * Laeuft auf einem Hintergrundfaden; der Aufrufer sorgt dafuer.
     */
    fun folge(roh: String, spruenge: Int = 5): String {
        var aktuell = roh
        repeat(spruenge) {
            val verbindung = try {
                (URL(aktuell).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
            } catch (e: Exception) {
                return aktuell
            }
            try {
                val code = verbindung.responseCode
                if (code !in 300..399) return aktuell
                val weiter = verbindung.getHeaderField("Location") ?: return aktuell
                aktuell = if (weiter.startsWith("http")) weiter
                          else URL(URL(aktuell), weiter).toString()
            } catch (e: Exception) {
                return aktuell
            } finally {
                verbindung.disconnect()
            }
        }
        return aktuell
    }

    /**
     * Ein Ziel an OsmAnd geben - auf dem Weg, der am ehesten ankommt.
     *
     * DIE KOORDINATE GEHT VOR, und das war einmal andersherum. Die Ueberlegung
     * war: der Name trifft den Eingang, das @lat,lon in einem Google-Link ist
     * nur die Bildmitte. Stimmt - aber OsmAnds Suche ist offline und findet
     * nur, was in der geladenen Karte steht und dort auch so heisst. Im
     * Versuch fand sie eine gewoehnliche Adresse nicht, und ein Ziel, das
     * nicht ankommt, ist schlechter als eins, das zwanzig Meter danebenliegt.
     *
     * Bleibt nur ein Name, wird er ZUERST in eine Koordinate umgesetzt
     * ([Ortsuche]) und erst dann uebergeben.
     *
     * UEBERGEBEN WIRD PER INTENT, nicht ueber die AIDL-Schnittstelle: deren
     * `navigate()` lieferte `true` und OsmAnd tat nichts - der Aufruf kommt
     * an, solange der Dienst gebunden ist, aber ob die Karte dahinter ihn
     * ausfuehren kann, sagt die Rueckgabe nicht. Siehe
     * [OsmandNavigation.oeffneZiel].
     */
    suspend fun uebergib(context: Context, ziel: Ziel): Uebergabe =
        withContext(Dispatchers.IO) {
            val fuehren = Einstellungen.kartenlinkFuehrt(context)
            if (ziel.lat != null && ziel.lon != null) {
                val weg = OsmandNavigation.oeffneZiel(
                    context, ziel.lat, ziel.lon, ziel.text, fuehren
                )
                return@withContext Uebergabe(
                    weg != null,
                    context.getString(R.string.kl_weg_koordinate, weg ?: context.getString(R.string.kl_abgelehnt_kurz)),
                    ziel.lat, ziel.lon,
                )
            }
            val name = ziel.text
            if (name.isNullOrBlank()) return@withContext Uebergabe(false, context.getString(R.string.kl_kein_ziel_kurz))

            Ortsuche.finde(context, name)?.let { (lat, lon) ->
                val weg = OsmandNavigation.oeffneZiel(context, lat, lon, name, fuehren)
                return@withContext Uebergabe(
                    weg != null,
                    context.getString(R.string.kl_weg_adresse, weg ?: context.getString(R.string.kl_abgelehnt_kurz)),
                    lat, lon,
                )
            }
            // Letzter Ausweg: OsmAnd selbst suchen lassen.
            val weg = OsmandNavigation.oeffneZiel(context, null, null, name, fuehren)
            Uebergabe(weg != null, weg ?: context.getString(R.string.kl_abgelehnt_kurz))
        }

    /** Was bei der Uebergabe herauskam - fuer den Verlauf und den Pruefstand. */
    data class Uebergabe(
        val geschafft: Boolean,
        val weg: String,
        val lat: Double? = null,
        val lon: Double? = null,
    ) {
        val beschreibung: String
            get() = weg + (if (lat != null && lon != null)
                ": " + Zahlen.zwei(lat) + ", " + Zahlen.zwei(lon) else "")
    }
}
