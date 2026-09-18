package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Zettel fuer OsmAnd, gegen das gepruefte, was OsmAnd wirklich schickt.
 *
 * Nachgemessen am Telefon (dumpsys notification, net.osmand.plus, laufende
 * Autonavigation):
 *
 *   android.title    = "80 m • Turn right and go"
 *   android.bigText  = "Turn right and go Akazienweg 300 m\n31 km • 28 min • 09:15"
 *   android.text     = null
 *   android.subText  = null
 *   android.progress = 0   android.progressMax = 0
 *   android.template = android.app.Notification$BigTextStyle
 *
 * DAS IST DAS GENAUE GEGENTEIL VON GOOGLE MAPS. Maps kennt keine Entfernung
 * zur Abzweigung und legt den Streckenfortschritt in progress/progressMax;
 * OsmAnd kennt die Entfernung, schreibt sie aber in denselben Titel wie die
 * Anweisung und laesst progress auf null stehen. Zwei Karten-Apps, zwei
 * unvereinbare Formen - und kein Zettel, der geraten waere, haette eine der
 * beiden getroffen.
 *
 * Der erste Entwurf dieses Zettels hatte geraten: Anweisung in `text`,
 * Entfernung als blosse Ziffer im Titel. Beides falsch, und weil `text` leer
 * ist, waere die Regel nie gezuendet - ein Zettel, der nichts tut, sieht aus
 * wie einer, der noch nicht dran war.
 */
class OsmandTest {

    private val zettel = File("../beispiele/osmand-navigation.json").readText()
    private val modul = Modul.lies(zettel).modul!!

    /** Wortwoertlich das Gemessene. */
    private val TITEL = "80 m • Turn right and go"
    private val GROSSTEXT = "Turn right and go Akazienweg 300 m\n31 km • 28 min • 09:15"

    private fun feld(regel: Int, name: String): Feldbelegung =
        (modul.regeln[regel].senke as Senke.AnDieUhr).felder.first { it.name == name }

    @Test
    fun zettelWirdVerstanden() {
        val e = Modul.lies(zettel)
        assertEquals(emptyList<String>(), e.fehler)
        assertEquals(Quelle.Benachrichtigung("net.osmand.plus"), e.modul!!.quelle)
        // Meter, Kilometer, Ende.
        assertEquals(3, e.modul!!.regeln.size)
    }

    @Test
    fun ausDemEinenTitelWerdenZweiAngaben() {
        // Der Kern der Sache: ein Feld, zwei Werte.
        assertEquals("Turn right and go", Wert.Text(TITEL).alsText(feld(0, "ANWEISUNG").muster))
        val ent = feld(0, "ENTFERNUNG")
        assertEquals(80L, Wert.Text(TITEL).alsZahl(ent.muster, ent.faktor))
    }

    @Test
    fun derZusatzIstDieZweiteZeileDesGrosstexts() {
        // Nicht die Strasse - die steckt in Zeile eins zwischen Anweisung und
        // Abschnittslaenge und waere nur mit Sprachwissen herauszuloesen.
        // Restweg, Restzeit und Ankunft sind am Steuer ohnehin das Nuetzlichere.
        assertEquals(
            "31 km • 28 min • 09:15",
            Wert.Text(GROSSTEXT).alsText(feld(0, "ZUSATZ").muster),
        )
    }

    @Test
    fun kilometerWerdenZuMetern() {
        val ent = feld(1, "ENTFERNUNG")
        assertEquals(1200L, Wert.Text("1.2 km • Turn left").alsZahl(ent.muster, ent.faktor))
        // Mit Komma genauso - welches Trennzeichen kommt, haengt an der
        // Spracheinstellung von OsmAnd, nicht an uns.
        assertEquals(1200L, Wert.Text("1,2 km • Turn left").alsZahl(ent.muster, ent.faktor))
        assertEquals(2000L, Wert.Text("2 km • Continue").alsZahl(ent.muster, ent.faktor))
    }

    @Test
    fun meterUndKilometerSchliessenEinanderAus() {
        // Beide Regeln greifen auf denselben Titel zu; griffen beide, stuenden
        // 1200 Meter als 1,2 Meter da - oder umgekehrt.
        val meter = modul.regeln[0].nurWenn["titel"]!!
        val kilo = modul.regeln[1].nurWenn["titel"]!!
        assertTrue(meter.containsMatchIn(TITEL))
        assertFalse(kilo.containsMatchIn(TITEL))
        assertTrue(kilo.containsMatchIn("1.2 km • Turn left"))
        assertFalse(meter.containsMatchIn("1.2 km • Turn left"))
    }

    @Test
    fun ohneEntfernungImTitelGreiftKeineRegel() {
        // Was OsmAnd sonst noch in den Titel schreiben kann, ist nicht
        // gemessen. Lieber nichts schicken als etwas Falsches - die Uhr sagt
        // nach zehn Minuten von selbst "Keine Navigation".
        val meter = modul.regeln[0].nurWenn["titel"]!!
        val kilo = modul.regeln[1].nurWenn["titel"]!!
        for (t in listOf("Arriving at destination", "Recalculating", "OsmAnd")) {
            assertFalse(t, meter.containsMatchIn(t))
            assertFalse(t, kilo.containsMatchIn(t))
        }
    }

    @Test
    fun dasEndeLoeschtDieZahlUndNichtSieAufNull() {
        // MINUS EINS, NICHT NULL. Die Uhr liest jede Entfernung >= 0 als
        // "es gibt eine Zahl" und stellt sie gross hin - bei null stuende
        // dort nach dem Ende "0 m", als waere die Abzweigung genau hier.
        val ende = modul.regeln[2]
        assertEquals(Ausloeser.VERSCHWINDET, ende.ausloeser)
        val s = ende.senke as Senke.AnDieUhr
        assertEquals(Wert.Zahl(-1L), s.felder.first { it.name == "ENTFERNUNG" }.fest)
        assertNotEquals(Wert.Zahl(0L), s.felder.first { it.name == "ENTFERNUNG" }.fest)
        assertEquals(Wert.Text("Navigation beendet"),
            s.felder.first { it.name == "ANWEISUNG" }.fest)
        // Und der Zusatz wird geleert, nicht stehen gelassen: "Navigation
        // beendet" mit der Ankunftszeit von vorhin darunter ist schlimmer als
        // nichts.
        assertEquals(Wert.Text(""), s.felder.first { it.name == "ZUSATZ" }.fest)
        // Ein Ende ist kein Anlass, die Uhr-App zu oeffnen.
        assertFalse(s.starten)
    }

    @Test
    fun uuidZeigtAufKieselweg() {
        for (r in modul.regeln) {
            assertEquals("888e2bc3-f95a-40ce-ac86-01eeeded7d36",
                (r.senke as Senke.AnDieUhr).uuid.toString())
        }
    }

    /**
     * Die Luecke, die dieser Zettel aufgedeckt hat.
     *
     * `muster` war fuer Textfelder schon erlaubt, wurde eingelesen, im
     * Feldbelegung mitgetragen - und beim Anwenden weggeworfen. Ein Zettel
     * sah damit richtig aus und wirkte anders. Bei Maps fiel es nie auf,
     * weil dort kein Feld zwei Angaben traegt.
     */
    @Test
    fun einTextfeldSchneidetAusUndFaelltSonstAus() {
        val m = Regex("•\\s*(.+)$")
        assertEquals("Turn right and go", Wert.Text(TITEL).alsText(m))
        // Ohne Muster bleibt alles, wie es war.
        assertEquals(TITEL, Wert.Text(TITEL).alsText(null))
        // Passt das Muster nicht, ist das Feld nicht da - und die Regel
        // faellt aus, statt Leeres an die Uhr zu schicken.
        assertNull(Wert.Text("Recalculating").alsText(m))
    }
}
