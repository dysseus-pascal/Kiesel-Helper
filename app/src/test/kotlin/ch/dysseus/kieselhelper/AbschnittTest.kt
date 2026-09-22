package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Abschnittsliste der Uhr zerlegen - ohne Telefon.
 *
 * SIE KOMMT ALS TEXT UEBER EINE FUNKSTRECKE, und die bricht ab, statt zu
 * kuerzen: der Postausgang der Uhr hoert mitten in einer Zeile auf, wenn der
 * Platz nicht reicht. Ein halb angekommener Satz waere in der
 * Gesundheitsakte eine erfundene Zahl - hier faellt er vorher weg.
 */
class AbschnittTest {

    @Test
    fun `drei Saetze aus der Liste`() {
        val a = Aufgaben.abschnitteAus("0:12:40;130:10:38;260:8:35;")
        assertEquals(3, a.size)
        assertEquals(0L, a[0].ab)
        assertEquals(12, a[0].anzahl)
        assertEquals(40L, a[0].dauer)
        assertEquals(8, a[2].anzahl)
    }

    @Test
    fun `eine abgebrochene Zeile faellt weg`() {
        // So sieht es aus, wenn der Platz mitten im letzten Abschnitt endet.
        val a = Aufgaben.abschnitteAus("0:12:40;130:10")
        assertEquals(1, a.size)
        assertEquals(12, a[0].anzahl)
    }

    @Test
    fun `Unsinn ergibt nichts`() {
        assertTrue(Aufgaben.abschnitteAus(null).isEmpty())
        assertTrue(Aufgaben.abschnitteAus("").isEmpty())
        assertTrue(Aufgaben.abschnitteAus("hallo;welt").isEmpty())
        assertTrue(Aufgaben.abschnitteAus("a:b:c").isEmpty())
        assertTrue(Aufgaben.abschnitteAus("-5:3:10").isEmpty())
    }

    @Test
    fun `eine Bahn ist ein Abschnitt mit Anzahl eins`() {
        val a = Aufgaben.abschnitteAus("10:1:22;32:1:24;56:1:23;")
        assertEquals(3, a.size)
        assertTrue(a.all { it.anzahl == 1 })
    }

    @Test
    fun `die Pause ist die Luecke zwischen zwei Saetzen`() {
        // Satz 1: 0 bis 40. Satz 2: ab 130 - also 90 Sekunden dazwischen.
        // Satz 3: ab 260, Satz 2 endete bei 168 - also 92.
        val a = Aufgaben.abschnitteAus("0:12:40;130:10:38;260:8:35;")
        assertEquals(91L, Aufgaben.pausenSchnitt(a))
    }

    @Test
    fun `ein einzelner Satz hat keine Pause`() {
        assertEquals(0L, Aufgaben.pausenSchnitt(Aufgaben.abschnitteAus("0:12:40;")))
        assertEquals(0L, Aufgaben.pausenSchnitt(emptyList()))
    }

    @Test
    fun `eine halbe Stunde dazwischen ist keine Pause mehr`() {
        // Wer den Satz um halb sechs macht und den naechsten um sechs, hat
        // dazwischen nicht pausiert - er war weg. So ein Wert verzerrte den
        // Schnitt bis zur Unbrauchbarkeit.
        val a = Aufgaben.abschnitteAus("0:12:40;130:10:38;4000:8:35;")
        assertEquals(90L, Aufgaben.pausenSchnitt(a))
    }
}
