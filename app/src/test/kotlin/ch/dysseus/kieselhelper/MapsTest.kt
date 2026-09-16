package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Zettel fuer Google Maps, gegen das gepruefte, was Maps wirklich schickt.
 *
 * Nachgemessen am Telefon (Android 16, dumpsys notification):
 *   android.template = android.app.Notification$ProgressStyle
 *   android.title    = "Head toward Lindenweg"    (Anweisung UND Strasse)
 *   android.text     = null
 *   android.subText  = "Arrive 14:22"
 *   android.progress = 1  /  android.progressMax = 6276
 *
 * Es gibt KEINE Entfernung zur naechsten Abzweigung. Wer eine erwartet,
 * schreibt einen Zettel, der nie greift - und merkt es nie, weil ein Zettel,
 * der nicht greift, sich genauso verhaelt wie einer, der noch nicht dran war.
 */
class MapsTest {

    private fun zettel(name: String): String =
        File("../beispiele/$name").readText()

    @Test
    fun mapsZettelWirdVerstanden() {
        val e = Modul.lies(zettel("google-maps-navigation.json"))
        assertEquals(emptyList<String>(), e.fehler)
        val m = e.modul!!
        assertEquals(Quelle.Benachrichtigung("com.google.android.apps.maps"), m.quelle)
        val s = m.regeln[0].senke as Senke.AnDieUhr
        assertEquals(10L, s.hoechstensAlleS)
        assertTrue(s.starten)
        // Die beiden Fortschrittszahlen sind der Kern: ohne sie haette die Uhr
        // ueberhaupt keine Entfernung zu zeigen.
        assertTrue(s.felder.any { it.aus == "extra:android.progress" })
        assertTrue(s.felder.any { it.aus == "extra:android.progressMax" })
    }

    @Test
    fun osmandZettelWirdVerstanden() {
        val e = Modul.lies(zettel("osmand-navigation.json"))
        assertEquals(emptyList<String>(), e.fehler)
        assertEquals(5L, ((e.modul!!.regeln[0].senke) as Senke.AnDieUhr).hoechstensAlleS)
    }

    @Test
    fun negativerTaktWirdAbgelehnt() {
        val e = Modul.lies(
            zettel("google-maps-navigation.json").replace("\"hoechstens_alle_s\": 10", "\"hoechstens_alle_s\": -1")
        )
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("hoechstens_alle_s") })
    }

    @Test
    fun uuidZeigtAufKieselweg() {
        // Wenn jemand die UUID der Uhr-App aendert, muss es hier auffallen und
        // nicht erst, wenn die Uhr stumm bleibt.
        for (n in listOf("google-maps-navigation.json", "osmand-navigation.json")) {
            val s = Modul.lies(zettel(n)).modul!!.regeln[0].senke as Senke.AnDieUhr
            assertEquals("888e2bc3-f95a-40ce-ac86-01eeeded7d36", s.uuid.toString())
        }
    }
}
