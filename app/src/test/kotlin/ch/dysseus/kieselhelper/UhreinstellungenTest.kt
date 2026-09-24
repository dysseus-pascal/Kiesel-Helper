package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UhreinstellungenTest {

    private val magnesium = Uhreinstellungen.Praeparat("Magnesium", 8, 30, 1, 0, 0, 20000)
    private val kur = Uhreinstellungen.Praeparat("Ashwagandha", 21, 5, 2, 8, 4, 19950)

    @Test
    fun planHinUndZurueck() {
        val plan = listOf(magnesium, null, kur, null, null, null)
        val bytes = Uhreinstellungen.planAlsBytes(plan)
        assertEquals(6 * 26, bytes.size)
        assertEquals(plan, Uhreinstellungen.planAus(bytes))
    }

    @Test
    fun ohneEinnahmewochenKeinePause() {
        val p = magnesium.copy(wochenAn = 0, wochenAus = 3)
        val zurueck = Uhreinstellungen.planAus(Uhreinstellungen.planAlsBytes(listOf(p)))!!
        assertEquals(0, zurueck[0]!!.wochenAus)
    }

    @Test
    fun leererNameIstLeererPlatz() {
        val bytes = Uhreinstellungen.planAlsBytes(listOf(magnesium.copy(name = "  ")))
        assertNull(Uhreinstellungen.planAus(bytes)!![0])
    }

    @Test
    fun nameHoechstens15ByteUndNieEinHalberUmlaut() {
        assertEquals("Vitamin D3 + K2", String(Uhreinstellungen.nameBytes("Vitamin D3 + K2 Tropfen"), Charsets.UTF_8))
        // 14 Byte, dann ein Umlaut (2 Byte) - der passt nicht mehr ganz hinein.
        assertEquals("abcdefghijklmn", String(Uhreinstellungen.nameBytes("abcdefghijklmnü"), Charsets.UTF_8))
    }

    @Test
    fun ankerInVierByte() {
        val p = magnesium.copy(anker = 20500)
        assertEquals(20500, Uhreinstellungen.planAus(Uhreinstellungen.planAlsBytes(listOf(p)))!![0]!!.anker)
    }
}
