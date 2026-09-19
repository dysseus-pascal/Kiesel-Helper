package ch.dysseus.kieselhelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import net.osmand.aidlapi.IOsmAndAidlCallback
import net.osmand.aidlapi.IOsmAndAidlInterface
import net.osmand.aidlapi.gpx.AGpxBitmap
import net.osmand.aidlapi.logcat.OnLogcatMessageParams
import net.osmand.aidlapi.navigation.ADirectionInfo
import net.osmand.aidlapi.navigation.ANavigationUpdateParams
import net.osmand.aidlapi.navigation.OnVoiceNavigationParams
import net.osmand.aidlapi.search.SearchResult
import java.util.UUID

/**
 * OsmAnd als Quelle - ueber seine eigene Schnittstelle, nicht ueber seine
 * Benachrichtigung.
 *
 * WARUM DAS DER BESSERE WEG IST. Aus der Benachrichtigung liess sich nur Text
 * lesen: "80 m • Turn right and go". Daraus die Richtung zu gewinnen hiess,
 * Woerter zu erraten - in zwei Sprachen, und Kreisverkehre standen in keiner
 * Liste. Hier kommt die Abbiegeart als ZAHL, dazu der Winkel in Grad, der
 * Strassenname, die Ankunftszeit und die Spuren. Nichts davon muss gedeutet
 * werden.
 *
 * WAS ES KOSTET: OsmAnds Schnittstelle steht unter GPLv3, und weil ihr Modul
 * mit dieser App verbunden wird, steht Kiesel-Helper als Ganzes unter GPLv3.
 * Das ist der bewusst bezahlte Preis - siehe LICENSE.
 *
 * Der Dienst ist bei OsmAnd `exported="true"`, ohne Berechtigung und ohne
 * Aufruferliste; jede App darf sich anbinden. Nachgesehen im Manifest von
 * OsmAnd, nicht angenommen.
 */
private typealias NavigateParams = net.osmand.aidlapi.navigation.NavigateParams
private typealias NavigateSearchParams = net.osmand.aidlapi.navigation.NavigateSearchParams

object OsmandNavigation {

    private const val TAG = PebbleEmpfaenger.TAG

    /** Die Aktion, unter der OsmAnd seinen Dienst anbietet. */
    private const val AKTION = "net.osmand.aidl.OsmandAidlServiceV2"

    /**
     * In dieser Reihenfolge gesucht. Es gibt OsmAnd mehrfach - die gekaufte
     * Fassung, die freie und die naechtliche -, und welche installiert ist,
     * weiss nur das Telefon.
     */
    private val PAKETE = listOf("net.osmand.plus", "net.osmand", "net.osmand.dev")

    /**
     * Die Schluessel im `turnInfo`-Buendel von `getAppInfo`. Ausgelesen aus
     * ExternalApiHelper von OsmAnd; das Buendel traegt die Angaben zur
     * laufenden Abbiegung unter dem Vorsatz "current_".
     */
    // "next_" ist die Abzweigung, die KOMMT - "current_" ist die, in der man
    // gerade steckt. Nur die naechste hat eine Entfernung, und nur sie gehoert
    // aufs Handgelenk.
    private const val SCHL_ENTFERNUNG = "next_turn_distance"
    private const val SCHL_ART = "next_turn_type"
    private const val SCHL_NAME = "next_turn_name"

    /** Wie oft bei OsmAnd nachgefragt wird, solange die Bindung steht. */
    private const val ABFRAGE_MS = 2000L

    /**
     * Wie oft hoechstens an die Uhr. OsmAnd meldet im Sekundentakt; jede
     * Meldung weiterzureichen hiesse, die Funkstrecke zur Uhr zu fluten.
     *
     * AUSGENOMMEN IST DER WECHSEL DER ABBIEGEART - der ist der eine Augenblick,
     * auf den es ankommt, und der darf nicht auf den Takt warten.
     */
    private const val TAKT_MS = 4000L

    private var api: IOsmAndAidlInterface? = null
    private var verbindung: ServiceConnection? = null
    private var ziel: UUID? = null
    private var app: Context? = null

    // Zuletzt Geschicktes, um Unveraendertes nicht zu wiederholen.
    private var letzteArt = -1
    private var letzteMeter = -1
    private var letzterTakt = 0L
    private var kurveGemeldet = false

    /** Laeuft gerade eine Navigation? Nur dann steht etwas auf der Uhr. */
    @Volatile
    var navigiert: Boolean = false
        private set

    /**
     * Wie es um die Verbindung steht - im Klartext, fuer den Schirm der App.
     *
     * DAS IST NICHT SCHMUCK. Der erste Anlauf scheiterte daran, dass OsmAnd im
     * Manifest nicht unter <queries> stand und damit unsichtbar war. Nichts
     * stuerzte ab, nichts warnte; die einzige Spur stand im Logcat, das man
     * ohne Kabel nicht liest. Also sagt die App es jetzt selbst.
     */
    @Volatile
    var lage: String = "noch nicht versucht"
        private set

    // --- Anbinden ---

    /**
     * An OsmAnd anbinden und die Abbiegedaten abonnieren.
     *
     * Rueckgabe false heisst: kein OsmAnd gefunden. Das ist kein Fehler,
     * sondern eine Auskunft - wer es nicht installiert hat, soll keine
     * Fehlermeldung bekommen.
     */
    fun binde(context: Context, an: UUID): Boolean {
        if (api != null) return true
        val ctx = context.applicationContext
        app = ctx
        ziel = an

        for (paket in PAKETE) {
            val absicht = Intent(AKTION).setPackage(paket)
            val verb = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    api = IOsmAndAidlInterface.Stub.asInterface(binder)
                    Log.i(TAG, "OsmAnd angebunden: $paket")
                    lage = "verbunden mit $paket"
                    Verlauf(ctx).merkeMeldung("OsmAnd verbunden ($paket)")
                    abonniere(ctx)
                    starteTakt(ctx)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    // OsmAnd wurde beendet. Nicht selbst neu binden - Android
                    // ruft onServiceConnected von sich aus wieder, sobald der
                    // Dienst zurueck ist. Ein eigener Wiederholversuch liefe
                    // daneben und haelt nur das Telefon wach.
                    Log.i(TAG, "OsmAnd weg")
                    lage = "OsmAnd beendet — wartet auf Rückkehr"
                    api = null
                    navigiert = false
                }
            }
            if (ctx.bindService(absicht, verb, Context.BIND_AUTO_CREATE)) {
                verbindung = verb
                lage = "$paket gefunden — verbinde"
                return true
            }
        }
        // Zwei Ursachen, und die zweite hat hier schon einmal einen Abend
        // gekostet: entweder ist OsmAnd nicht installiert, ODER es steht nicht
        // unter <queries> im Manifest und ist damit unsichtbar - auch wenn es
        // laeuft. Beides sieht von hier aus gleich aus, also beides nennen.
        Log.i(TAG, "Kein OsmAnd erreichbar")
        lage = "OsmAnd nicht erreichbar — installiert? (sonst Sichtbarkeit im Manifest)"
        Verlauf(ctx).merkeMeldung("OsmAnd nicht erreichbar")
        return false
    }

    /**
     * Nochmal anklopfen.
     *
     * NOETIG, WEIL DAS FREISCHALTEN IN EINER ANDEREN APP GESCHIEHT. Wer in
     * OsmAnd den Schalter umlegt, aendert nichts an unserer Verbindung - sie
     * steht ja. Nur das Abonnement wurde damals abgewiesen, und von selbst
     * fragt niemand ein zweites Mal. Ohne diesen Weg muesste man den Dienst
     * beenden und neu starten, um etwas zu merken.
     *
     * Wird beim Oeffnen des Hauptschirms gerufen: dort kommt man ohnehin
     * vorbei, wenn man wissen will, ob es jetzt geht.
     */
    fun versucheErneut(context: Context) {
        if (api != null) abonniere(context.applicationContext)
        else binde(context, ziel ?: Kieselstrasse.UUID)
    }

    fun loese(context: Context) {
        val verb = verbindung ?: return
        try {
            context.applicationContext.unbindService(verb)
        } catch (e: IllegalArgumentException) {
            // War schon gelöst. Kein Grund für Lärm.
        }
        verbindung = null
        api = null
        navigiert = false
        stoppeTakt()
    }


    /**
     * OsmAnd ein Ziel geben und die Fuehrung starten.
     *
     * KEIN START MITGEGEBEN. Mit `startLat = 0.0` nimmt OsmAnd die eigene
     * Position - das ist fast immer gemeint, und es erspart dieser App eine
     * Standortberechtigung, die sie sonst nirgends braucht.
     *
     * `force = true`, weil hier jemand gerade aktiv auf einen Link getippt
     * hat: eine schon laufende Fuehrung soll dann weichen, nicht eine
     * Rueckfrage erzeugen, die im Auto niemand beantwortet.
     *
     * Rueckgabe false heisst: OsmAnd ist nicht verbunden oder hat abgelehnt -
     * typischerweise, weil die App dort unter Plugins noch nicht
     * freigeschaltet ist.
     */
    fun navigiere(name: String?, lat: Double, lon: Double, profil: String = "car"): Boolean =
        try {
            api?.navigate(
                NavigateParams(
                    null, 0.0, 0.0,
                    name ?: "Ziel", lat, lon,
                    profil, true, false,
                )
            ) ?: false
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "navigate: " + e.message)
            false
        }

    /**
     * Dasselbe fuer einen Link, der nur einen Namen traegt.
     *
     * Ein Google-Maps-Link auf ein Lokal enthaelt oft keine Koordinate,
     * sondern nur dessen Namen. Dann sucht OsmAnd selbst - besser, als dem
     * Menschen zu sagen, sein Link sei ungeeignet.
     */
    fun sucheUndNavigiere(
        text: String,
        naheLat: Double,
        naheLon: Double,
        profil: String = "car",
    ): Boolean = try {
        api?.navigateSearch(
            NavigateSearchParams(
                null, 0.0, 0.0,
                text, naheLat, naheLon,
                profil, true, false,
            )
        ) ?: false
    } catch (e: Exception) {
        Log.w(PebbleEmpfaenger.TAG, "navigateSearch: " + e.message)
        false
    }

    /** Ob gerade eine Verbindung zu OsmAnd steht. */
    val verbunden: Boolean get() = api != null

    private fun abonniere(ctx: Context) {
        val schnitt = api ?: return
        try {
            // DER RUECKGABEWERT IST EINE ANTWORT, keine Zierde: OsmAnd gibt
            // die Nummer des Abonnements zurueck und -1, wenn es nichts
            // eingerichtet hat. Wer ihn wegwirft, meldet "abonniert" und
            // wartet danach auf Daten, die nie kommen.
            val nummer = schnitt.registerForNavigationUpdates(
                ANavigationUpdateParams().apply { setSubscribeToUpdates(true) },
                rueckruf,
            )
            if (nummer < 0) {
                // NICHT "geht nicht", sondern WAS ZU TUN IST.
                //
                // OsmAnd gibt -1 zurueck, wenn getApi() null liefert, und das
                // tut es, solange die aufrufende App nicht freigeschaltet ist.
                // Beim ersten Kontakt traegt OsmAnd sie selbst als
                // "verbundene App" ein - aber AUSGESCHALTET. Erst ein Schalter
                // in OsmAnd macht sie gueltig. Nachgelesen in OsmandAidlApi:
                // isAppEnabled legt den Eintrag mit enabled=false an.
                lage = "in OsmAnd freischalten: Menü › Plugins › Kiesel-Helper"
                Verlauf(ctx).merkeMeldung(
                    "OsmAnd: noch nicht freigeschaltet (Menü › Plugins)"
                )
                Log.w(TAG, "registerForNavigationUpdates gab $nummer")
            } else {
                lage = "verbunden, Abbiegedaten abonniert"
                Log.i(TAG, "Abbiegedaten abonniert (Nr. $nummer)")
            }
        } catch (e: Exception) {
            // Auch ein RemoteException faellt hierher. Es waere nichts
            // gewonnen, die App deswegen zu beenden.
            Log.w(TAG, "Abonnieren fehlgeschlagen: " + e.message)
            lage = "Abonnieren fehlgeschlagen: " + (e.message ?: "unbekannt")
            Verlauf(ctx).merkeMeldung("OsmAnd: Abonnieren fehlgeschlagen")
        }
    }

    // --- Was OsmAnd meldet ---

    private val rueckruf = object : IOsmAndAidlCallback.Stub() {

        /**
         * Die eigentliche Meldung: Entfernung zur Abzweigung und Abbiegeart.
         *
         * KOMMT AUF EINEM BINDER-STRANG, nicht auf dem der Oberflaeche. Hier
         * darf nichts gezeichnet werden; ein Broadcast an die Uhr ist in
         * Ordnung.
         */
        override fun updateNavigationInfo(info: ADirectionInfo?) {
            // Nur als Anlass, nicht als Quelle: die Nachfrage holt ohnehin
            // alles, und so gibt es EINEN Weg zu den Werten statt zweier, die
            // sich widersprechen koennen.
            app?.let { frageAb(it) }
        }

        override fun onUpdate() {}
        override fun onAppInitialized() {}
        override fun onSearchComplete(resultSet: MutableList<SearchResult>?) {}
        override fun onGpxBitmapCreated(bitmap: AGpxBitmap?) {}
        override fun onContextMenuButtonClicked(buttonId: Int, pointId: String?, layerId: String?) {}
        override fun onVoiceRouterNotify(params: OnVoiceNavigationParams?) {}
        override fun onKeyEvent(params: KeyEvent?) {}
        override fun onLogcatMessage(params: OnLogcatMessageParams?) {}
    }

    /**
     * Eine Meldung von OsmAnd an die Uhr weiterreichen.
     *
     * Zwei Riegel: Unveraendertes gar nicht, und sonst hoechstens alle
     * [TAKT_MS] - ausser die Abbiegeart hat gewechselt, denn das ist der
     * naechste Schritt und der darf nicht warten.
     */
    private fun melde(meter: Int, art: Int, strasse: String?, ankunft: Long, rest: Int) {
        val ctx = app ?: return
        val an = ziel ?: return

        navigiert = true

        if (art == letzteArt && meter == letzteMeter) return
        val jetzt = System.currentTimeMillis()
        val neuerSchritt = art != letzteArt
        if (!neuerSchritt && jetzt - letzterTakt < TAKT_MS) return

        letzteArt = art
        letzteMeter = meter
        letzterTakt = jetzt

        val felder = mutableMapOf<Int, Wert>(
            Kieselstrasse.ABBIEGEART to Wert.Zahl(art.toLong()),
            Kieselstrasse.ENTFERNUNG to Wert.Zahl(meter.toLong()),
            // Leer heisst auf der Uhr LOESCHEN - sonst bliebe die Strasse von
            // vorhin unter der neuen Abzweigung stehen.
            Kieselstrasse.STRASSE to Wert.Text(strasse ?: ""),
        )
        if (ankunft > 0) felder[Kieselstrasse.ANKUNFT] = Wert.Zahl(ankunft)
        if (rest > 0) felder[Kieselstrasse.REST] = Wert.Zahl(rest.toLong())

        // NEUER SCHRITT STARTET DIE UHR-APP. Eine AppMessage erreicht nur eine
        // laufende Watchapp; ohne das bliebe der Schirm dunkel, bis man sie von
        // Hand oeffnet.
        UhrSender.sende(ctx, an, neuerSchritt, felder)
        Verlauf(ctx).merkeMeldung(
            "OsmAnd: Art $art, $meter m" + if (strasse.isNullOrBlank()) "" else " — $strasse"
        )
    }

    // --- Nachfragen statt warten ---

    private var takt: android.os.Handler? = null

    /**
     * OsmAnd im Takt fragen, statt auf seinen Rueckruf zu warten.
     *
     * WARUM BEIDES. Der Rueckruf `updateNavigationInfo` ist der schoenere Weg -
     * er meldet sich von selbst, genau wenn sich etwas aendert. Nur kam er beim
     * ersten Versuch am Geraet nie an, obwohl Verbindung und Abonnement
     * bestaetigt waren. Woran es lag, laesst sich ohne Kabel nicht feststellen.
     *
     * `getAppInfo` braucht ihn nicht: es liefert dasselbe auf Nachfrage, und
     * sogar mehr - Entfernung, Abbiegeart, Strasse, Restweg und Ankunftszeit in
     * einem Zug. Zwei Sekunden Takt sind fuer eine Anzeige am Handgelenk
     * reichlich; die Bremse in `melde` haelt die Funkstrecke ohnehin frei.
     *
     * Der Rueckruf bleibt trotzdem angemeldet. Kommt er, ist die Anzeige
     * schneller; kommt er nicht, faellt es niemandem auf.
     */
    private fun starteTakt(ctx: Context) {
        if (takt != null) return
        val faden = android.os.HandlerThread("osmand-takt").apply { start() }
        val h = android.os.Handler(faden.looper)
        takt = h
        h.post(object : Runnable {
            override fun run() {
                frageAb(ctx)
                h.postDelayed(this, ABFRAGE_MS)
            }
        })
    }

    private fun stoppeTakt() {
        takt?.removeCallbacksAndMessages(null)
        takt = null
    }

    /**
     * Einmal nachsehen, was OsmAnd gerade weiss.
     *
     * Kein Abbiegeziel heisst: es wird nicht navigiert. Dann einmal das Ende
     * melden und danach schweigen - nicht alle zwei Sekunden dasselbe.
     */
    private fun frageAb(ctx: Context) {
        val schnitt = api ?: return
        try {
            val info = schnitt.appInfo ?: return
            val kurve: Bundle? = info.turnInfo

            if (!kurveGemeldet && kurve != null && !kurve.isEmpty) {
                kurveGemeldet = true
                // EINMAL aufschreiben, was wirklich drinsteht. Die
                // Schluesselnamen sind aus OsmAnds Quelltext gelesen, nicht
                // gemessen - und ein Name, der danebenliegt, liefert still
                // null statt einer Warnung.
                Log.i(TAG, "turnInfo: " + kurve.keySet().joinToString(", "))
                Verlauf(ctx).merkeMeldung("OsmAnd-Felder: " + kurve.keySet().joinToString(", "))
            }

            val meter = kurve?.getInt(SCHL_ENTFERNUNG, -1) ?: -1
            if (kurve == null || meter <= 0) {
                if (navigiert) meldeEnde()
                return
            }
            val kuerzel = kurve.getString(SCHL_ART)
            val nr = ausfahrt(kuerzel)
            val name = kurve.getString(SCHL_NAME).orEmpty()
            // Die Ausfahrtnummer gehoert VOR den Strassennamen: im Kreisel ist
            // sie die eigentliche Anweisung, der Name bloss die Bestaetigung.
            val strasse = if (nr > 0) "$nr. Ausfahrt" + (if (name.isBlank()) "" else " · $name")
                          else name
            melde(meter, artAusText(kuerzel), strasse, info.arrivalTime, info.leftDistance)
        } catch (e: Exception) {
            Log.w(TAG, "Nachfrage fehlgeschlagen: " + e.message)
        }
    }

    /**
     * Aus OsmAnds Kuerzel die Kennzahl machen.
     *
     * Die Schnittstelle liefert hier Text, nicht die Zahl - dieselben Kuerzel,
     * die OsmAnd in seine Routendateien schreibt. Kreisverkehre tragen die
     * Ausfahrtnummer gleich mit ("RNDB3"), deshalb wird nur der Anfang
     * verglichen.
     */
    private fun artAusText(s: String?): Int = when {
        s == null -> 0
        s.startsWith("RNDB") -> 13
        s.startsWith("RNLB") -> 14
        s == "C" -> 1
        s == "TL" -> 2
        s == "TSLL" -> 3
        s == "TSHL" -> 4
        s == "TR" -> 5
        s == "TSLR" -> 6
        s == "TSHR" -> 7
        s == "KL" -> 8
        s == "KR" -> 9
        s == "TU" -> 10
        s == "TRU" -> 11
        s == "OFFR" -> 12
        else -> 0
    }

    /** Die Ausfahrtnummer aus "RNDB3" - 0, wenn keine dasteht. */
    private fun ausfahrt(s: String?): Int {
        if (s == null || !(s.startsWith("RNDB") || s.startsWith("RNLB"))) return 0
        return s.drop(4).toIntOrNull() ?: 0
    }

    /**
     * Der Uhr sagen, dass Schluss ist.
     *
     * Ohne das zeigte sie die letzte Anweisung weiter. Minus eins heisst
     * "keine Zahl"; eine Null hiesse "null Meter", also genau hier abbiegen.
     */
    fun meldeEnde() {
        val ctx = app ?: return
        val an = ziel ?: return
        navigiert = false
        letzteArt = -1
        letzteMeter = -1
        UhrSender.sende(
            ctx, an, false,
            mapOf(
                Kieselstrasse.ABBIEGEART to Wert.Zahl(0L),
                Kieselstrasse.ENTFERNUNG to Wert.Zahl(-1L),
                Kieselstrasse.STRASSE to Wert.Text(""),
                Kieselstrasse.REST to Wert.Zahl(-1L),
            ),
        )
        Verlauf(ctx).merkeMeldung("OsmAnd: Navigation beendet")
    }
}

/**
 * Die Feldnummern der Uhr-App Kieselstrasse.
 *
 * SIE ERGEBEN SICH AUS DER REIHENFOLGE der `messageKeys` in deren
 * package.json, beginnend bei 10000. Wer dort eine Zeile dazwischenschiebt,
 * verschiebt alle folgenden - und diese App schickte danach still die falschen
 * Werte ins falsche Feld. Darum stehen sie hier an EINER Stelle und nicht
 * verstreut.
 */
object Kieselstrasse {
    val UUID: UUID = java.util.UUID.fromString("a83bf269-e798-45bc-9bf0-a0f68362a79a")

    const val ABBIEGEART = 10000   // OsmAnds TurnType, 1..14
    const val ENTFERNUNG = 10001   // Meter bis zur Abzweigung, -1 = keine
    const val STRASSE = 10002      // Strassenname
    const val ANKUNFT = 10003      // Ankunftszeit, Sekunden seit 1970
    const val REST = 10004         // Meter bis zum Ziel
}
