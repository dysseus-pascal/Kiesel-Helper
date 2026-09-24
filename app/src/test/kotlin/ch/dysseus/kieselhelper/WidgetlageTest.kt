package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WidgetlageTest {

    private fun blick(
        jetzt: LocalDateTime = LocalDateTime.of(2026, 9, 24, 12, 0),
        schlafEnde: LocalDateTime? = null,
        trainingEnde: LocalDateTime? = null,
        schritte: Double? = 4000.0,
        wasserMl: Double? = 1500.0,
        offene: List<String> = emptyList(),
        wunsch: Widgetlage.Art? = null,
    ) = Widgetlage.Blick(
        jetzt = jetzt, schlafEnde = schlafEnde, schlafMin = 430.0, schlafZielMin = 480.0,
        schlafWocheMin = 400.0, erholsamAnteil = 0.4, ruhepuls = 52.0, hrv = 60.0,
        trainingEnde = trainingEnde, trainingName = "Laufen", trainingMin = 40, trainingPuls = 140.0,
        trainingKm = 7.2, trainingBeginn = 1L,
        schritte = schritte, schritteZiel = 8000.0, aktivMin = 30.0, aktivZiel = 60.0,
        wasserMl = wasserMl, wasserZiel = 2400.0, glasMl = 300.0,
        offenePraeparate = offene, koffeinMg = null, wunsch = wunsch,
    )

    @Test
    fun morgensDieNacht() {
        val l = Widgetlage.ermittle(blick(
            jetzt = LocalDateTime.of(2026, 9, 24, 7, 30),
            schlafEnde = LocalDateTime.of(2026, 9, 24, 6, 45),
            schritte = 120.0, wasserMl = 0.0,
        ))
        assertEquals(Widgetlage.Art.MORGEN, l.art)
        assertEquals("7 h 10", l.gross)
        assertTrue(l.satz.contains("länger als sonst"))
        assertEquals(listOf("schritte", "aktiv", "wasser"), l.kacheln.map { it.schluessel })
    }

    @Test
    fun trainingSchlaegtMorgen() {
        val l = Widgetlage.ermittle(blick(
            jetzt = LocalDateTime.of(2026, 9, 24, 8, 0),
            schlafEnde = LocalDateTime.of(2026, 9, 24, 6, 45),
            trainingEnde = LocalDateTime.of(2026, 9, 24, 7, 50),
        ))
        assertEquals(Widgetlage.Art.TRAINING, l.art)
        assertEquals("Laufen", l.name)
        assertEquals(1L, l.trainingBeginn)
        assertTrue(l.satz.contains("7.2 km"))
    }

    @Test
    fun dreiStundenNachDemAufwachenIstEsTag() {
        val l = Widgetlage.ermittle(blick(
            jetzt = LocalDateTime.of(2026, 9, 24, 10, 0),
            schlafEnde = LocalDateTime.of(2026, 9, 24, 6, 45),
            wasserMl = 900.0,
        ))
        assertEquals(Widgetlage.Art.TAG, l.art)
        assertEquals("Schritte", l.name)
    }

    @Test
    fun offenePraeparateErinnern() {
        val l = Widgetlage.ermittle(blick(offene = listOf("Magnesium", "Zink", "D3")))
        assertEquals(Widgetlage.Art.ERINNERUNG, l.art)
        assertEquals("Magnesium, Zink … noch offen", l.satz)
    }

    @Test
    fun wasserImRueckstandErinnert() {
        // Um 15 Uhr sollten 7/14 des Ziels drin sein: 1200 ml. 300 sind vier Glaeser dahinter.
        val l = Widgetlage.ermittle(blick(jetzt = LocalDateTime.of(2026, 9, 24, 15, 0), wasserMl = 300.0))
        assertEquals(Widgetlage.Art.ERINNERUNG, l.art)
        assertEquals("Wasser", l.name)
        assertEquals("3 Gläser hinterher", l.satz)
    }

    @Test
    fun abendsDieBilanz() {
        val l = Widgetlage.ermittle(blick(jetzt = LocalDateTime.of(2026, 9, 24, 21, 0), wasserMl = 2100.0))
        assertEquals(Widgetlage.Art.ABEND, l.art)
        assertTrue(l.satz, l.satz.contains("noch 4 000 Schritte"))
        assertTrue(l.satz.contains("1 Glas fehlen"))
    }

    @Test
    fun derWunschUebersteuert() {
        val l = Widgetlage.ermittle(blick(wunsch = Widgetlage.Art.MORGEN))
        assertEquals(Widgetlage.Art.MORGEN, l.art)
    }
}
