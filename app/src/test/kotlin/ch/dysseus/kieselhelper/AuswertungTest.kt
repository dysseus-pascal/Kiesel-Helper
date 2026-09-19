package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Die Auswertung ohne Telefon pruefen.
 *
 * Sie ist die einzige Stelle der App, die etwas BEHAUPTET - "dein Mittwoch ist
 * schwach". Eine falsch gezogene Grenze faellt am Geraet nicht auf: das Bild
 * sieht immer plausibel aus. Hier faellt sie auf.
 */
class AuswertungTest {

    private val heute = LocalDate.of(2026, 9, 19)     // ein Samstag

    private fun tag(minusTage: Long, wert: Double) = heute.minusDays(minusTage) to wert

    @Test
    fun `heute zaehlt nicht mit`() {
        val bild = Auswertung.bild(
            listOf(tag(0, 100.0), tag(7, 1000.0), tag(14, 1000.0)), heute
        )
        assertEquals(2, bild.anzahl)
        assertEquals(1000.0, bild.gesamt!!, 0.001)

        val samstag = bild.profil.first { it.tag == DayOfWeek.SATURDAY }
        assertEquals(1000.0, samstag.mittel!!, 0.001)
    }

    @Test
    fun `ein einzelner Tag ergibt noch kein Muster`() {
        val bild = Auswertung.bild(listOf(tag(3, 500.0)), heute)
        val mittwoch = bild.profil.first { it.tag == DayOfWeek.WEDNESDAY }
        assertNull(mittwoch.mittel)
        assertEquals(1, mittwoch.anzahl)
    }

    @Test
    fun `zwei gleiche Wochentage werden gemittelt`() {
        val bild = Auswertung.bild(listOf(tag(3, 400.0), tag(10, 600.0)), heute)
        val mittwoch = bild.profil.first { it.tag == DayOfWeek.WEDNESDAY }
        assertEquals(500.0, mittwoch.mittel!!, 0.001)
        assertEquals(2, mittwoch.anzahl)
    }

    @Test
    fun `staerkster und schwaechster Tag`() {
        val werte = mutableListOf<Pair<LocalDate, Double>>()
        (1L..28L).forEach { i ->
            val t = heute.minusDays(i)
            werte += t to if (t.dayOfWeek == DayOfWeek.SUNDAY) 2000.0 else 1000.0
        }
        val bild = Auswertung.bild(werte, heute)
        assertEquals(DayOfWeek.SUNDAY, bild.staerkster!!.tag)
        assertEquals(1000.0, bild.schwaechster!!.mittel!!, 0.001)
    }

    @Test
    fun `Veraenderung braucht beide Haelften`() {
        val kurz = (1L..10L).map { tag(it, 1000.0) }
        assertNull(Auswertung.bild(kurz, heute).veraenderung)

        val lang = (1L..56L).map { i -> tag(i, if (i <= 28) 1100.0 else 1000.0) }
        assertEquals(10.0, Auswertung.bild(lang, heute).veraenderung!!, 0.001)
    }

    @Test
    fun `der Verlauf zeigt hoechstens acht Wochen`() {
        val lang = (1L..200L).map { tag(it, 1000.0) }
        assertEquals(Auswertung.WOCHEN, Auswertung.bild(lang, heute).wochen.size)
    }
}
