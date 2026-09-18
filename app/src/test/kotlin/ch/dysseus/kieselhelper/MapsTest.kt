package ch.dysseus.kieselhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Der Zettel fuer Google Maps, gegen das gepruefte, was Maps wirklich schickt.
 *
 * Nachgemessen am Telefon (Android 16, dumpsys notification):
 *   android.template = android.app.Notification$ProgressStyle
 *   android.title    = "450 m · Turn left onto <Strasse>"
 *   android.text     = null
 *   android.subText  = "Arrive 14:22"
 *
 * DIE ENTFERNUNG STEHT IM TITEL, und das war lange nicht klar. Am Stand
 * gemessen zeigt Maps nur "Head toward <Strasse>" ohne Zahl; erst der Verlauf
 * einer echten Fahrt zeigte die andere Haelfte:
 *
 *   +  2s   70 m · Turn right onto ...
 *   + 43s  450 m · Turn left
 *   + 53s  210 m · Turn left
 *   + 63s         Turn left
 *   + 77s  1.9 km · Turn left onto ...
 *
 * Zwei Dinge stehen darin. Erstens zaehlt die Entfernung herunter und wechselt
 * bei einem Kilometer die Einheit. Zweitens - und das ist die Zeile, die man
 * uebersieht - LAESST MAPS IM LETZTEN TAKT VOR DER ABZWEIGUNG DIE ZAHL WEG.
 * Das ist kein Fehlen, das ist das Jetzt: gleich abbiegen.
 *
 * Solange der Zettel das nicht wusste, schickte er den Streckenfortschritt
 * (progress/progressMax) und die Uhr zeigte den Rest bis zum Ziel - die
 * einzige Zahl, die Maps scheinbar hergab. Sie war die falsche: 29 km bis
 * Bern helfen an keiner Kreuzung.
 */
class MapsTest {

    private fun zettel(name: String): String =
        File("../beispiele/$name").readText()

    private val modul = Modul.lies(zettel("google-maps-navigation.json")).modul!!

    private fun feld(regel: Int, name: String): Feldbelegung =
        (modul.regeln[regel].senke as Senke.AnDieUhr).felder.first { it.name == name }

    private fun titel(regel: Int): Regex = modul.regeln[regel].nurWenn["titel"]!!

    @Test
    fun mapsZettelWirdVerstanden() {
        val e = Modul.lies(zettel("google-maps-navigation.json"))
        assertEquals(emptyList<String>(), e.fehler)
        assertEquals(Quelle.Benachrichtigung("com.google.android.apps.maps"), e.modul!!.quelle)
        for (i in 0..2) {
            val s = e.modul!!.regeln[i].senke as Senke.AnDieUhr
            assertEquals(10L, s.hoechstensAlleS)
            assertTrue(s.starten)
            // Ohne diesen Riegel greift die Regel auch bei einer beliebigen
            // Maps-Meldung, die gar keine Navigation ist.
            assertEquals("ProgressStyle",
                e.modul!!.regeln[i].nurWenn["extra:android.template"]!!.pattern)
        }
    }

    @Test
    fun ausDemTitelWerdenAnweisungUndEntfernung() {
        val t = "450 m · Turn left onto Musterstrasse"
        assertEquals("Turn left onto Musterstrasse", Wert.Text(t).alsText(feld(0, "ANWEISUNG").muster))
        val ent = feld(0, "ENTFERNUNG")
        assertEquals(450L, Wert.Text(t).alsZahl(ent.muster, ent.faktor))
    }

    @Test
    fun kilometerWerdenZuMetern() {
        val t = "1.9 km · Turn left onto Musterstrasse"
        val ent = feld(1, "ENTFERNUNG")
        assertEquals(1900L, Wert.Text(t).alsZahl(ent.muster, ent.faktor))
        assertEquals("Turn left onto Musterstrasse", Wert.Text(t).alsText(feld(1, "ANWEISUNG").muster))
    }

    @Test
    fun dieDreiRegelnSchliessenEinanderAus() {
        // Jeder Titel darf genau eine der drei ausloesen. Zwei waeren zwei
        // Zahlen fuer dieselbe Abzweigung, keine waere Stillstand.
        val faelle = mapOf(
            "450 m · Turn left onto Musterstrasse" to 0,
            "1.9 km · Turn left onto Musterstrasse" to 1,
            "Turn left onto Musterstrasse" to 2,
            "Head toward Musterstrasse" to 2,
        )
        for ((t, erwartet) in faelle) {
            val treffer = (0..2).filter { titel(it).containsMatchIn(t) }
            assertEquals("$t -> $treffer", listOf(erwartet), treffer)
        }
    }

    @Test
    fun derTrennpunktDarfEinAndererSein() {
        // Gemessen ist der Mittelpunkt "·". Welches Zeichen eine andere
        // Spracheinstellung nimmt, ist NICHT gemessen - deshalb steht im
        // Muster "irgendein Satzzeichen" und nicht dieser eine Punkt. Ein
        // Zettel, der auf das falsche Zeichen wartet, tut stumm gar nichts.
        for (t in listOf("450 m · Turn left", "450 m • Turn left", "450 m - Turn left")) {
            assertTrue(t, titel(0).containsMatchIn(t))
            assertEquals("Turn left", Wert.Text(t).alsText(feld(0, "ANWEISUNG").muster))
        }
    }

    @Test
    fun minutenSindKeineMeter() {
        // "28 min · ..." darf die Meter-Regel nicht ausloesen. Nach der Zahl
        // muss ein Satzzeichen folgen, kein weiterer Buchstabe.
        assertFalse(titel(0).containsMatchIn("28 min · Turn left"))
        assertFalse(titel(1).containsMatchIn("28 min · Turn left"))
    }

    @Test
    fun ohneEntfernungBleibtKeineAlteZahlStehen() {
        // Der Takt vor der Abzweigung. Wuerde hier nichts geschickt, stuenden
        // die 110 Meter von vorhin weiter da, waehrend man schon abbiegt.
        val ent = feld(2, "ENTFERNUNG")
        assertEquals(Wert.Zahl(-1), ent.fest)
        assertNull(ent.aus)
        // Und die Anweisung geht ungeschnitten durch - da ist nichts
        // abzuschneiden.
        assertNull(feld(2, "ANWEISUNG").muster)
    }

    @Test
    fun keinFortschrittMehr() {
        // Der Streckenfortschritt war der Notbehelf, solange die Entfernung
        // unbekannt schien. Beide zugleich waeren zwei grosse Zahlen mit
        // verschiedener Bedeutung - die Uhr schliesst sie aus, der Zettel
        // soll es gar nicht erst versuchen.
        assertFalse(modul.schluessel.containsKey("FORTSCHRITT"))
        for (r in modul.regeln) {
            val s = r.senke as Senke.AnDieUhr
            assertTrue(s.felder.none { it.name.startsWith("FORTSCHRITT") })
        }
    }

    @Test
    fun negativerTaktWirdAbgelehnt() {
        val e = Modul.lies(
            zettel("google-maps-navigation.json")
                .replace("\"hoechstens_alle_s\": 10", "\"hoechstens_alle_s\": -1")
        )
        assertNull(e.modul)
        assertTrue(e.fehler.any { it.contains("hoechstens_alle_s") })
    }

    @Test
    fun osmandZettelWirdVerstanden() {
        // Nur, dass er einlesbar ist. Was er tut, steht in OsmandTest - und
        // dort gegen das, was am Telefon gemessen wurde.
        val e = Modul.lies(zettel("osmand-navigation.json"))
        assertEquals(emptyList<String>(), e.fehler)
        assertEquals(Quelle.Benachrichtigung("net.osmand.plus"), e.modul!!.quelle)
    }

    @Test
    fun uuidZeigtAufKieselweg() {
        // Wenn jemand die UUID der Uhr-App aendert, muss es hier auffallen und
        // nicht erst, wenn die Uhr stumm bleibt.
        for (n in listOf("google-maps-navigation.json", "osmand-navigation.json")) {
            for (r in Modul.lies(zettel(n)).modul!!.regeln) {
                assertEquals("888e2bc3-f95a-40ce-ac86-01eeeded7d36",
                    (r.senke as Senke.AnDieUhr).uuid.toString())
            }
        }
    }
}
