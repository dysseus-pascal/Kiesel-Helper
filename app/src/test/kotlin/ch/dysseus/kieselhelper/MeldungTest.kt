package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Die Vorlage "meldung" gilt fuer JEDE Senke.
 *
 * Sie galt fuer "eintrag" und "melden", nicht aber fuer "senden" - dort schrieb
 * der Sender immer seinen eigenen Text. Aufgefallen ist das erst nach einer
 * Autofahrt: der Verlauf war voll und sagte nichts als "4 Felder an die Uhr",
 * und die Rohwerte, wegen derer die Fahrt gemacht wurde, fehlten.
 *
 * Diese Pruefungen halten fest, dass der Maps-Zettel eine Vorlage MIT den
 * Rohwerten traegt. Ob sie am Ende benutzt wird, entscheidet Regelwerk - das
 * braucht Android und laesst sich hier nicht pruefen; die Vorlage selbst schon.
 */
class MeldungTest {

    private val maps = File("../beispiele/google-maps-navigation.json").readText()

    @Test
    fun mapsMeldungTraegtDieRohwerte() {
        // JEDE Regel, die etwas schickt, muss den Rohtext mitschreiben, aus
        // dem sie geschnitten hat - nicht nur die erste.
        //
        // Dieser Verlauf hat sich schon einmal bezahlt gemacht: er war es, der
        // spaeter bewiesen hat, dass Maps die Entfernung sehr wohl in den
        // Titel legt. Am Stand ist das nicht zu sehen, dort steht nie eine
        // Zahl. Ohne die Aufzeichnung waere es eine Vermutung geblieben.
        for (i in 0..2) {
            val r = Modul.lies(maps).modul!!.regeln[i]
            for (feld in listOf("{titel}", "{untertext}")) {
                assertTrue("Platzhalter $feld fehlt in Regel $i: " + r.meldung,
                    r.meldung.contains(feld))
            }
        }
    }

    @Test
    fun dieEndeRegelMeldetAuchEtwas() {
        val r = Modul.lies(maps).modul!!.regeln[3]
        assertEquals("Navigation beendet", r.meldung)
    }
}
