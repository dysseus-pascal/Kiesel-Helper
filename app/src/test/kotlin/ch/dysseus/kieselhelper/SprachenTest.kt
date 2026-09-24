package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die fuenf Sprachdateien gegeneinander.
 *
 * EIN FEHLENDER SCHLUESSEL FAELLT SONST NIEMANDEM AUF: Android nimmt still den
 * englischen Rueckfall, und auf einem franzoesischen Telefon steht dann
 * mitten im Satz Englisch. Ein falscher Platzhalter ist schlimmer - "%1\$d"
 * statt "%1\$s" wirft erst zur Laufzeit, auf dem Telefon, an genau der Stelle.
 */
class SprachenTest {

    private val sprachen = listOf("values-de", "values-fr", "values-it", "values-es")
    private val platzhalter = Regex("""%(\d+)\$[-#+ 0,(]*\d*(?:\.\d+)?[sdfxX]""")

    private fun platzhalterIn(t: String) = platzhalter.findAll(t).map { it.value }.toSortedSet()

    @Test
    fun jedeSpracheHatJedenText() {
        val en = Ressourcen.roh(Ressourcen.datei("values")).keys
        assertTrue(en.size > 40)
        for (s in sprachen) {
            val da = Ressourcen.roh(Ressourcen.datei(s)).keys
            assertEquals("$s: fehlt", emptySet<String>(), en - da)
            assertEquals("$s: zu viel", emptySet<String>(), da - en)
        }
    }

    @Test
    fun platzhalterStimmenUeberein() {
        val en = Ressourcen.texte(Ressourcen.datei("values"))
        val enP = Ressourcen.mehrzahlen(Ressourcen.datei("values"))
        for (s in sprachen) {
            val t = Ressourcen.texte(Ressourcen.datei(s))
            for ((k, v) in en) assertEquals("$s/$k", platzhalterIn(v), platzhalterIn(t.getValue(k)))
            val p = Ressourcen.mehrzahlen(Ressourcen.datei(s))
            for ((k, v) in enP) {
                assertTrue("$s/$k: other fehlt", p.getValue(k).containsKey("other"))
                assertEquals("$s/$k", platzhalterIn(v.getValue("other")), platzhalterIn(p.getValue(k).getValue("other")))
            }
        }
    }

    @Test
    fun apostropheSindMaskiert() {
        // aapt2 bricht an einem nackten ' ab - im Franzoesischen und
        // Italienischen steht er in jedem dritten Satz.
        for (s in sprachen + "values") {
            for ((k, v) in Ressourcen.roh(Ressourcen.datei(s))) {
                assertTrue("$s/$k: $v", !Regex("""(?<!\\)'""").containsMatchIn(v))
            }
        }
    }

    @Test
    fun englischIstDerRueckfall() {
        // Das Widget auf einem Telefon in einer sechsten Sprache.
        val l = Widgetlage.ermittle(
            WidgetlageTest.blick(jetzt = java.time.LocalDateTime.of(2026, 9, 24, 21, 0), wasserMl = 2100.0),
            TestTexte("values", java.util.Locale.ENGLISH),
        )
        assertEquals("Steps", l.name)
        assertTrue(l.satz, l.satz.contains("1 glass missing"))
    }
}
