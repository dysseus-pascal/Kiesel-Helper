package ch.dysseus.kieselhelper

import ch.dysseus.kieselhelper.Schlafanalyse.Phase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Erfundene Naechte, deren Antwort man kennt.
 *
 * DIE ZAHLEN SIND GROB GEWAEHLT: wach heisst Bewegung 60 (vmc 225, weit ueber
 * der Schwelle), Schlaf heisst 4 (vmc 1). Geprueft wird, ob die Rechnung
 * das Offensichtliche findet - nicht, ob sie auf die Minute mit einem
 * Schlaflabor uebereinstimmt; das kann sie nicht.
 */
class SchlafanalyseTest {

    private val WACH = 60
    private val RUHIG = 4

    /** Eine Nacht aus Bloecken: (Minuten, Bewegung, Puls). */
    private fun nacht(vararg bloecke: Triple<Int, Int, Int>): Pair<IntArray, IntArray> {
        val b = mutableListOf<Int>()
        val p = mutableListOf<Int>()
        bloecke.forEach { (n, bew, puls) -> repeat(n) { b += bew; p += puls } }
        return b.toIntArray() to p.toIntArray()
    }

    private fun phaseBei(n: Schlafanalyse.Nacht, minute: Int): Phase? =
        n.phasen.firstOrNull { minute >= it.von && minute < it.bis }?.phase

    /**
     * Eine Nacht mit Schlafzyklen im Puls: tief (50), leicht (56), REM (62),
     * leicht zitternd, damit die Schwankung ein Mass hat.
     */
    private fun zyklen(minuten: Int): List<Int> {
        val aus = mutableListOf<Int>()
        while (aus.size < minuten) {
            repeat(25) { aus += 50 + it % 2 }
            repeat(20) { aus += 56 + it % 2 }
            repeat(35) { aus += 62 + it % 2 }
        }
        return aus.take(minuten)
    }

    @Test
    fun ruhigeNachtMitWachphase() {
        val schlaf1 = zyklen(170)
        val schlaf2 = zyklen(230)
        val b = IntArray(30) { WACH } + IntArray(170) { RUHIG } + IntArray(20) { WACH } +
            IntArray(230) { RUHIG } + IntArray(30) { WACH }
        val p = IntArray(30) { 85 } + schlaf1.toIntArray() + IntArray(20) { 88 } +
            schlaf2.toIntArray() + IntArray(30) { 85 }
        // Alle halbe Stunde eine HRV, im Schlaf um 40 ms.
        val hrv = (30 until 450 step 30).map { Schlafanalyse.HrvFenster(it, 40 + (it / 30) % 3, 55) }
        val n = Schlafanalyse.werte(b, p, hrv)
        assertNotNull(n)
        n!!
        assertTrue("eingeschlafen ${n.einschlafen}", n.einschlafen in 30..40)
        assertTrue("aufgewacht ${n.aufwachen}", n.aufwachen in 445..451)
        // Die Wachphase mitten in der Nacht steht als wach da.
        assertEquals(Phase.WACH, phaseBei(n, 210))
        assertTrue("wach ${n.minuten(Phase.WACH)}", n.minuten(Phase.WACH) in 18..32)
        // Alle drei Phasen kommen vor.
        assertTrue(n.minuten(Phase.TIEF) > 30)
        assertTrue(n.minuten(Phase.REM) > 30)
        assertTrue(n.minuten(Phase.LEICHT) > 30)
        // In der ersten Stunde gibt es kein REM.
        assertTrue(n.phasen.none { it.phase == Phase.REM && it.von < n.einschlafen + 60 })
        // Die Mitte eines REM-Blocks nach der ersten Stunde: Minute 30+80+45+17.
        assertEquals(Phase.REM, phaseBei(n, 30 + 80 + 45 + 17))
        // Die Mitte eines Tiefschlafblocks: 30+80+12.
        assertEquals(Phase.TIEF, phaseBei(n, 30 + 80 + 12))
        // Kein Abschnitt unter fuenf Minuten, lueckenlos und ohne Ueberlappung.
        assertTrue(n.phasen.all { it.laenge >= 5 })
        n.phasen.zipWithNext().forEach { (x, y) -> assertEquals(x.bis, y.von) }
        assertEquals(n.einschlafen, n.phasen.first().von)
        assertEquals(n.aufwachen, n.phasen.last().bis)
        // Ruhepuls: die tiefste halbe Stunde - im Tiefschlaf, also um 50..53.
        assertTrue("ruhepuls ${n.ruhepuls}", n.ruhepuls!! in 50..54)
        assertTrue("hrv ${n.hrv}", n.hrv!! in 40..42)
    }

    @Test
    fun einschlaflatenz() {
        // Eine Stunde unruhig im Bett (vmc 100), dann sechs Stunden Schlaf.
        val (b, p) = nacht(Triple(60, 40, 70), Triple(360, RUHIG, 55), Triple(20, WACH, 80))
        val n = Schlafanalyse.werte(b, p)!!
        assertTrue("eingeschlafen ${n.einschlafen}", n.einschlafen in 60..66)
        assertTrue(n.schlafMinuten in 340..360)
    }

    @Test
    fun stillWachLiegenMitHohemPuls() {
        // Ohne Bewegung, aber mit einem Puls weit ueber dem der Nacht: wach.
        val (b, p) = nacht(Triple(200, RUHIG, 52), Triple(30, RUHIG, 85), Triple(200, RUHIG, 52))
        val n = Schlafanalyse.werte(b, p)!!
        assertEquals(Phase.WACH, phaseBei(n, 215))
    }

    @Test
    fun fehlendeDaten() {
        // Anderthalb Stunden ohne Uhr mitten in der Nacht, und am Anfang.
        val (b, p) = nacht(
            Triple(20, Schlafanalyse.UNGUELTIG, 0),
            Triple(180, RUHIG, 54),
            Triple(90, Schlafanalyse.UNGUELTIG, 0),
            Triple(180, RUHIG, 54),
        )
        val n = Schlafanalyse.werte(b, p)!!
        assertTrue(n.einschlafen >= 20)
        // Was nicht gemessen wurde, ist kein Schlaf.
        assertEquals(Phase.WACH, phaseBei(n, 245))
        assertTrue("schlaf ${n.schlafMinuten}", n.schlafMinuten in 340..362)
    }

    @Test
    fun ohnePulsNurSchlafUndWach() {
        val (b, p) = nacht(Triple(20, WACH, 0), Triple(200, RUHIG, 0), Triple(20, WACH, 0), Triple(200, RUHIG, 0))
        val n = Schlafanalyse.werte(b, p)!!
        val arten = n.phasen.map { it.phase }.toSet()
        assertEquals(setOf(Phase.SCHLAF, Phase.WACH), arten)
        assertNull(n.ruhepuls)
        assertNull(n.hrv)
    }

    @Test
    fun zuKurzIstKeineNacht() {
        val (b, p) = nacht(Triple(30, WACH, 80), Triple(50, RUHIG, 55), Triple(30, WACH, 80))
        assertNull(Schlafanalyse.werte(b, p))
        assertNull(Schlafanalyse.werte(IntArray(0), IntArray(0)))
        // Nur wach: auch keine.
        val (b2, p2) = nacht(Triple(300, WACH, 80))
        assertNull(Schlafanalyse.werte(b2, p2))
    }

    @Test
    fun niedrigeHrvVerhindertTiefschlaf() {
        val schlaf = zyklen(400)
        val b = IntArray(400) { RUHIG }
        val p = schlaf.toIntArray()
        // Um den dritten Tiefschlafblock (Minute 160..185) eine niedrige HRV,
        // sonst hohe.
        val hrv = (0 until 400 step 30).map { Schlafanalyse.HrvFenster(it, 60, 50) } +
            Schlafanalyse.HrvFenster(165, 15, 50)
        val n = Schlafanalyse.werte(b, p, hrv)!!
        assertTrue(phaseBei(n, 172) != Phase.TIEF)
        assertEquals(Phase.TIEF, phaseBei(n, 92))
    }

    @Test
    fun websterVerlaengertWachphasen() {
        val wach = BooleanArray(40) { it in 5 until 20 }
        Schlafanalyse.webster(wach)
        // Fuenfzehn Minuten wach: die naechsten vier auch.
        assertTrue((5 until 24).all { wach[it] })
        assertFalse(wach[24])
    }

    @Test
    fun websterKurzerSchlafZwischenWachphasen() {
        val wach = BooleanArray(40) { it !in 12 until 18 }
        Schlafanalyse.webster(wach)
        assertTrue(wach.all { it })
    }

    @Test
    fun gewichtetIstEinMittelwert() {
        val a = DoubleArray(10) { 100.0 }
        val w = Schlafanalyse.gewichtet(a, BooleanArray(10) { true })
        w.forEach { assertEquals(100.0, it, 1e-9) }
    }

    @Test
    fun minutenUndHrvAusBytes() {
        val (bew, puls) = Schlafanalyse.minutenAus(byteArrayOf(4, 55, 255.toByte(), 0))
        assertArrayEquals(intArrayOf(4, 255), bew)
        assertArrayEquals(intArrayOf(55, 0), puls)
        val f = Schlafanalyse.hrvAus(byteArrayOf(0x2C, 0x01, 42, 51))
        assertEquals(listOf(Schlafanalyse.HrvFenster(300, 42, 51)), f)
    }

    @Test
    fun stueckeIdempotentZusammensetzen() {
        val stueck0 = byteArrayOf(4, 50, 5, 51)
        val stueck1 = byteArrayOf(6, 52)
        var d = Nachtdaten.einfuegen(null, 3, 0, stueck0)
        assertFalse(Nachtdaten.vollstaendig(d))
        // Dasselbe Stueck noch einmal: nichts aendert sich.
        d = Nachtdaten.einfuegen(d, 3, 0, stueck0)
        assertFalse(Nachtdaten.vollstaendig(d))
        d = Nachtdaten.einfuegen(d, 3, 2, stueck1)
        assertTrue(Nachtdaten.vollstaendig(d))
        d = Nachtdaten.einfuegen(d, 3, 2, stueck1)
        val (bew, puls) = Nachtdaten.zerlege(d)
        assertArrayEquals(intArrayOf(4, 5, 6), bew)
        assertArrayEquals(intArrayOf(50, 51, 52), puls)
    }

    @Test
    fun fehlendeMinuteIstUngueltig() {
        val d = Nachtdaten.einfuegen(null, 2, 1, byteArrayOf(4, 50))
        val (bew, puls) = Nachtdaten.zerlege(d)
        assertEquals(Schlafanalyse.UNGUELTIG, bew[0])
        assertEquals(0, puls[0])
    }

    @Test
    fun glaettenSchlaegtKurzeZu() {
        val a = listOf(
            Schlafanalyse.Abschnitt(0, 20, Phase.LEICHT),
            Schlafanalyse.Abschnitt(20, 22, Phase.REM),
            Schlafanalyse.Abschnitt(22, 40, Phase.LEICHT),
            Schlafanalyse.Abschnitt(40, 60, Phase.TIEF),
        )
        val g = Schlafanalyse.glaette(a)
        assertEquals(
            listOf(Schlafanalyse.Abschnitt(0, 40, Phase.LEICHT), Schlafanalyse.Abschnitt(40, 60, Phase.TIEF)),
            g,
        )
    }
}
