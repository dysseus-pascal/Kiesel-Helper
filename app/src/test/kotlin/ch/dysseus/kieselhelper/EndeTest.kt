package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Das Ende einer Navigation.
 *
 * Die zweite Haelfte, die bis eben fehlte: verschwindet die Benachrichtigung,
 * ist die Navigation vorbei - und die Uhr muss es erfahren. Sonst zeigt sie
 * die letzte Anweisung weiter und merkt es erst nach zwei Minuten am
 * Zeitstempel.
 */
class EndeTest {

    private val maps = File("../beispiele/google-maps-navigation.json").readText()

    @Test
    fun mapsZettelHatEineRegelFuersEnde() {
        val m = Modul.lies(maps).modul!!
        assertEquals(2, m.regeln.size)
        assertEquals(Ausloeser.ERSCHEINT, m.regeln[0].ausloeser)
        assertEquals(Ausloeser.VERSCHWINDET, m.regeln[1].ausloeser)
    }

    @Test
    fun dasEndeSchicktFesteWerte() {
        val s = Modul.lies(maps).modul!!.regeln[1].senke as Senke.AnDieUhr
        // Ohne die Null fuer die Strecke zeigte die Uhr die letzte Entfernung
        // weiter - ein fehlendes Feld laesst dort das alte stehen.
        val max = s.felder.first { it.name == "FORTSCHRITT_MAX" }
        assertEquals(Wert.Zahl(0), max.fest)
        assertNull(max.aus)
        assertEquals(Wert.Text("Navigation beendet"),
            s.felder.first { it.name == "ANWEISUNG" }.fest)
        // Und es startet die Uhr-App NICHT neu - ein Ende ist kein Anlass,
        // etwas zu oeffnen.
        assertEquals(false, s.starten)
    }

    @Test
    fun einFeldKannNichtBeidesSein() {
        val e = Modul.lies(
            maps.replace("\"wert\": \"Navigation beendet\"",
                         "\"wert\": \"x\", \"aus\": \"titel\"")
        )
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("nicht beides") })
    }

    @Test
    fun eineAppMessageKannNichtVerschwinden() {
        // Fassung-1-Zettel meinen eine Uhr-App. Dort waere "verschwindet" eine
        // Regel, die nie greift - und das faellt niemandem auf.
        val drinktervall = File("../../Kiesel-Helper-Drinktervall/kiesel.json")
        if (!drinktervall.exists()) return
        val e = Modul.lies(
            drinktervall.readText().replace("\"wenn\":", "\"ausloeser\": \"verschwindet\", \"wenn\":")
        )
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("verschwindet") })
    }

    @Test
    fun unbekannterAusloeserWirdAbgelehnt() {
        val e = Modul.lies(maps.replace("\"verschwindet\"", "\"irgendwann\""))
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("irgendwann") })
    }
}
