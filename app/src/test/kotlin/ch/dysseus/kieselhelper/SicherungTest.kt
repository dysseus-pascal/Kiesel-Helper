package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Die Sicherung schreiben und wieder lesen - ohne Telefon.
 *
 * WER EINE SICHERUNG SCHREIBT, DIE SICH NICHT ZURUECKLESEN LAESST, merkt es
 * genau einmal: dann, wenn er sie braucht. Genau deshalb steht das Umwandeln
 * in einer eigenen Datei ohne Netz und ohne Android - damit es hier geprueft
 * werden kann und nicht erst nach einem Geraetewechsel.
 */
class SicherungTest {

    private val tage = listOf(
        LocalDate.of(2026, 9, 1) to mapOf("schritte" to 8123.0, "schlaf" to 431.0),
        LocalDate.of(2026, 9, 2) to mapOf("schritte" to 6002.0, "ruhepuls" to 54.0),
    )

    @Test
    fun `hin und zurueck ergibt dasselbe`() {
        val text = Sicherung.alsJson(tage, mapOf("schlaf_ziel_min" to "480"), listOf(1758400000L), "jetzt")
        val zurueck = Sicherung.ausJson(text)
        assertNotNull(zurueck)
        assertEquals(Sicherung.FASSUNG, zurueck!!.fassung)
        assertEquals(2, zurueck.tage.size)
        assertEquals(LocalDate.of(2026, 9, 1), zurueck.tage[0].first)
        assertEquals(8123.0, zurueck.tage[0].second["schritte"]!!, 0.001)
        assertEquals(54.0, zurueck.tage[1].second["ruhepuls"]!!, 0.001)
        assertEquals("480", zurueck.einstellungen["schlaf_ziel_min"])
        assertEquals(listOf(1758400000L), zurueck.spuren)
    }

    @Test
    fun `die Fassung steht drin`() {
        val text = Sicherung.alsJson(tage, emptyMap(), emptyList(), "jetzt")
        assertTrue(text.contains("\"fassung\""))
        // Eine Sicherung ueberlebt die App, die sie geschrieben hat; ohne
        // diese Zahl muesste eine spaetere Fassung raten.
        assertTrue(text.contains("Kiesel-Helper"))
    }

    @Test
    fun `was keine Sicherung ist, ergibt nichts`() {
        assertNull(Sicherung.ausJson(null))
        assertNull(Sicherung.ausJson(""))
        assertNull(Sicherung.ausJson("kein JSON"))
        assertNull(Sicherung.ausJson("{}"))
        // Ohne Fassung ist es keine.
        assertNull(Sicherung.ausJson("""{"tage":[]}"""))
    }

    @Test
    fun `ein kaputter Tag faellt weg, der Rest bleibt`() {
        val text = """
            {"fassung":1,"tage":[
              {"datum":"2026-09-01","schritte":100.0},
              {"datum":"kein Datum","schritte":200.0},
              {"datum":"2026-09-03","schritte":300.0}
            ]}
        """.trimIndent()
        val zurueck = Sicherung.ausJson(text)!!
        assertEquals(2, zurueck.tage.size)
        assertEquals(LocalDate.of(2026, 9, 3), zurueck.tage[1].first)
    }

    @Test
    fun `unbekannte Spalten werden nicht uebernommen`() {
        // Eine Sicherung aus einer spaeteren Fassung kann Felder mitbringen,
        // die es hier nicht gibt. Sie in die Tabelle zu schreiben ginge schief.
        val text = """{"fassung":9,"tage":[{"datum":"2026-09-01","schritte":100.0,"zukunft":7.0}]}"""
        val zurueck = Sicherung.ausJson(text)!!
        assertEquals(setOf("schritte"), zurueck.tage[0].second.keys)
    }

    @Test
    fun `zurueckgeholt wird nur, was hier fehlt`() {
        val vorhanden = mapOf(
            LocalDate.of(2026, 9, 1) to mapOf("schritte" to 9999.0),
        )
        val luecken = Sicherung.nurLuecken(tage, vorhanden)
        // Tag 1 hat schon Schritte - nur der Schlaf fehlt.
        assertEquals(2, luecken.size)
        assertEquals(setOf("schlaf"), luecken[0].second.keys)
        // Tag 2 gibt es gar nicht: beides kommt.
        assertEquals(setOf("schritte", "ruhepuls"), luecken[1].second.keys)
    }

    @Test
    fun `ein vollstaendig bekannter Tag kommt nicht vor`() {
        val vorhanden = mapOf(
            LocalDate.of(2026, 9, 1) to mapOf("schritte" to 1.0, "schlaf" to 2.0),
            LocalDate.of(2026, 9, 2) to mapOf("schritte" to 1.0, "ruhepuls" to 2.0),
        )
        assertTrue(Sicherung.nurLuecken(tage, vorhanden).isEmpty())
    }

    @Test
    fun `die Spurdatei heisst wie auf dem Telefon`() {
        // Beide Seiten muessen denselben Namen bilden, sonst kommt eine
        // gesicherte Strecke nie wieder an.
        assertEquals("spur-1758400000.jsonl", Sicherung.spurname(1758400000L))
    }
}
