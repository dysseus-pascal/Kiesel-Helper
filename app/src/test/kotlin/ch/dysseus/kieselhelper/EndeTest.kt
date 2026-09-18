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
        // Meter, Kilometer, ohne Entfernung - und das Ende.
        assertEquals(4, m.regeln.size)
        for (i in 0..2) assertEquals(Ausloeser.ERSCHEINT, m.regeln[i].ausloeser)
        assertEquals(Ausloeser.VERSCHWINDET, m.regeln[3].ausloeser)
    }

    @Test
    fun dasEndeSchicktFesteWerte() {
        val s = Modul.lies(maps).modul!!.regeln[3].senke as Senke.AnDieUhr
        // MINUS EINS, NICHT NULL. Ohne eine Angabe zur Strecke zeigte die Uhr
        // die letzte Entfernung weiter - ein fehlendes Feld laesst dort das
        // alte stehen. Mit einer Null stuende gross "0 m" da, als waere die
        // Abzweigung genau hier; erst minus eins heisst "gar keine Zahl".
        val ent = s.felder.first { it.name == "ENTFERNUNG" }
        assertEquals(Wert.Zahl(-1), ent.fest)
        assertNull(ent.aus)
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
