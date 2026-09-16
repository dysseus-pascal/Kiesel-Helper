package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruefungen fuer den Zettelleser.
 *
 * Er laeuft ohne Telefon: reine Logik, kein Android. Das ist der Grund, warum
 * er ueberhaupt pruefbar ist - und der Grund, warum er es sein MUSS. Ein Zettel
 * kommt aus dem Netz, und was er anordnet, landet in einer Gesundheitsakte.
 * Der Leser ist die einzige Stelle, die zwischen beidem steht.
 *
 * Gepruefte Behauptungen:
 *  - Zettel der Fassung 1 laufen unveraendert weiter.
 *  - Fassung 2 kann Benachrichtigungen lesen und an die Uhr schicken.
 *  - Jeder Fehler bleibt ein Fehler: kein halbes Modul, nie.
 */
class ZettelTest {

    // Die ECHTEN Zettel aus den beiden Beschreibungs-Repos, wortgleich.
    private val drinktervall = """
        {
          "format": 1,
          "name": "Drinktervall",
          "uuid": "5b0f7a3e-2c8d-4b61-9e4f-7d2a1c9b8e50",
          "quelle": "https://github.com/dysseus-pascal/Drinktervall",
          "beschreibung": "Trägt jedes getrunkene Glas ein.",
          "schluessel": { "GLASS_ML": 10008, "DRANK_AT": 10009 },
          "regeln": [
            {
              "wenn": ["DRANK_AT", "GLASS_ML"],
              "nicht_zweimal_fuer": "DRANK_AT",
              "eintrag": {
                "art": "hydration",
                "menge": { "aus": "GLASS_ML", "einheit": "ml" },
                "beginn": { "aus": "DRANK_AT", "einheit": "s" },
                "dauer_s": 60
              },
              "meldung": "{GLASS_ML} ml eingetragen"
            }
          ]
        }
    """.trimIndent()

    private val navigation = """
        {
          "format": 2,
          "name": "OsmAnd-Navigation",
          "quelle": { "art": "benachrichtigung", "paket": "net.osmand.plus" },
          "schluessel": { "ANWEISUNG": 10000, "ENTFERNUNG": 10001 },
          "regeln": [
            {
              "wenn": ["titel", "text"],
              "nur_wenn": { "titel": "[0-9]" },
              "nicht_zweimal_fuer": "text",
              "senden": {
                "an": "7e1b28b2-cd13-4b50-ac4e-187b92707707",
                "starten": true,
                "felder": {
                  "ANWEISUNG":  { "aus": "text", "art": "text" },
                  "ENTFERNUNG": { "aus": "titel", "art": "zahl", "muster": "([0-9.,]+)" }
                }
              },
              "meldung": "{text}"
            }
          ]
        }
    """.trimIndent()

    // --- Fassung 1 laeuft weiter ---

    @Test
    fun fassungEinsBleibtGueltig() {
        val e = Modul.lies(drinktervall)
        assertEquals(emptyList<String>(), e.fehler)
        val m = assertNotNull(e.modul).let { e.modul!! }
        assertEquals("Drinktervall", m.name)
        // Ohne Feld `quelle` im neuen Sinn wird die Uhr erraten.
        assertEquals(Quelle.Uhr(java.util.UUID.fromString("5b0f7a3e-2c8d-4b61-9e4f-7d2a1c9b8e50")), m.quelle)
        // Und `quelle` als Adresse bleibt lesbar, statt als Objekt zu verunglücken.
        assertEquals("https://github.com/dysseus-pascal/Drinktervall", m.herkunft)
        assertEquals(1, m.regeln.size)
        assertTrue(m.regeln[0].senke is Senke.Akteneintrag)
    }

    // --- Fassung 2: Benachrichtigung an die Uhr ---

    @Test
    fun navigationZettelWirdVerstanden() {
        val e = Modul.lies(navigation)
        assertEquals(emptyList<String>(), e.fehler)
        val m = e.modul!!
        assertEquals(Quelle.Benachrichtigung("net.osmand.plus"), m.quelle)
        val senke = m.regeln[0].senke as Senke.AnDieUhr
        assertTrue(senke.starten)
        assertEquals(2, senke.felder.size)
        val entfernung = senke.felder.first { it.name == "ENTFERNUNG" }
        assertEquals(10001, entfernung.nummer)
        assertEquals(false, entfernung.alsText)
        assertNotNull(entfernung.muster)
    }

    // --- Fehler bleiben Fehler ---

    @Test
    fun unbekannteSatzartNenntDieMoeglichen() {
        val e = Modul.lies(drinktervall.replace("\"hydration\"", "\"blutdruck\""))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("blutdruck") && it.contains("hydration") })
    }

    @Test
    fun falscheEinheitFaelltAuf() {
        // "g" statt "ml" wuerde stillschweigend falsche Zahlen eintragen.
        val e = Modul.lies(drinktervall.replace("\"einheit\": \"ml\"", "\"einheit\": \"g\""))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("ml") })
    }

    @Test
    fun feldDasDieQuelleNichtHatFaelltAuf() {
        val e = Modul.lies(drinktervall.replace("\"GLASS_ML\", ", "\"TIPPFEHLER\", "))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("TIPPFEHLER") })
    }

    @Test
    fun benachrichtigungKenntNurIhreEigenenFelder() {
        val e = Modul.lies(navigation.replace("\"aus\": \"text\"", "\"aus\": \"GLASS_ML\""))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("GLASS_ML") })
    }

    @Test
    fun feldOhneNummerKannNichtGesendetWerden() {
        val e = Modul.lies(navigation.replace("\"ANWEISUNG\": 10000, ", ""))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("ANWEISUNG") })
    }

    @Test
    fun zweiSenkenInEinerRegelSindEinFehler() {
        val e = Modul.lies(
            navigation.replace("\"meldung\": \"{text}\"", "\"melden\": { \"text\": \"x\" }")
        )
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("genau eines") })
    }

    @Test
    fun unbekannteQuelleWirdAbgelehnt() {
        val e = Modul.lies(navigation.replace("\"benachrichtigung\"", "\"bluetooth\""))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("bluetooth") })
    }

    @Test
    fun unbekanntesFormatWirdAbgelehnt() {
        val e = Modul.lies(navigation.replace("\"format\": 2", "\"format\": 3"))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("Format 3") })
    }

    @Test
    fun kaputterRegulaererAusdruckWirdAbgelehnt() {
        val e = Modul.lies(navigation.replace("([0-9.,]+)", "([0-9"))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("regulärer Ausdruck") })
    }

    // --- Werte ---

    @Test
    fun zahlAusFliesstext() {
        val muster = Regex("([0-9.,]+)")
        assertEquals(250L, Wert.Text("in 250 m").alsZahl(muster, 1.0))
        // Komma als Dezimaltrenner, und km in Meter.
        assertEquals(1200L, Wert.Text("1,2 km").alsZahl(muster, 1000.0))
        // Kein Treffer heisst null - und damit: Regel greift nicht.
        assertNull(Wert.Text("jetzt geradeaus").alsZahl(muster, 1.0))
    }

    @Test
    fun zahlBleibtZahl() {
        assertEquals(42L, Wert.Zahl(42).alsZahl(null, 1.0))
        assertEquals("42", Wert.Zahl(42).alsText())
    }

    // --- Draht zur Uhr ---

    @Test
    fun woerterbuchHatDasFormatDerGegenseite() {
        val json = UhrSender.woerterbuch(
            mapOf(10000 to Wert.Text("Hauptstrasse"), 10001 to Wert.Zahl(250))
        )
        val a = org.json.JSONArray(json)
        val nach = (0 until a.length()).associate { i ->
            val o = a.getJSONObject(i)
            o.getInt("key") to o
        }
        // Text: type "string", Breite 0 (Width.NONE).
        assertEquals("string", nach[10000]!!.getString("type"))
        assertEquals(0, nach[10000]!!.getInt("length"))
        assertEquals("Hauptstrasse", nach[10000]!!.getString("value"))
        // Zahl: type "int", Breite 4 (Width.WORD) - NICHT die Laenge des Werts.
        assertEquals("int", nach[10001]!!.getString("type"))
        assertEquals(4, nach[10001]!!.getInt("length"))
        assertEquals(250, nach[10001]!!.getInt("value"))
    }
}
