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
    private const val SCHL_NAME = "current_turn_name"
    private const val SCHL_WINKEL = "current_turn_angle"

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
    }

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
                lage = "verbunden, aber OsmAnd nimmt das Abonnement nicht an"
                Verlauf(ctx).merkeMeldung("OsmAnd lehnt das Abonnement ab ($nummer)")
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
            val i = info ?: return
            melde(i.distanceTo, i.turnType)
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
    private fun melde(meter: Int, art: Int) {
        val ctx = app ?: return
        val an = ziel ?: return

        navigiert = art > 0

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
        )
        strasseUndAnkunft(felder)

        UhrSender.sende(ctx, an, neuerSchritt, felder)
        Verlauf(ctx).merkeMeldung("OsmAnd: Art $art, $meter m")
    }

    /**
     * Strassenname, Ankunftszeit und Restweg dazuholen.
     *
     * Sie stehen nicht in der Abbiegemeldung, sondern in `getAppInfo`. Das ist
     * ein zweiter Aufruf je Meldung - er kostet wenig, und ohne ihn haette die
     * Uhr eine Zahl ohne Ort.
     *
     * Schlaegt er fehl, wird trotzdem geschickt: eine Entfernung ohne
     * Strassennamen ist brauchbar, gar nichts nicht.
     */
    private fun strasseUndAnkunft(felder: MutableMap<Int, Wert>) {
        val schnitt = api ?: return
        try {
            val info = schnitt.appInfo ?: return
            val kurve: Bundle? = info.turnInfo
            // EINMAL aufschreiben, was wirklich drinsteht. Die Schluesselnamen
            // sind aus OsmAnds Quelltext gelesen, nicht gemessen - und ein
            // Name, der danebenliegt, liefert still null statt einer Warnung.
            if (!kurveGemeldet && kurve != null) {
                kurveGemeldet = true
                Log.i(TAG, "turnInfo-Schluessel: " + kurve.keySet().joinToString(", "))
                Log.i(TAG, "  Rest " + info.leftDistance + " m, Restzeit " + info.leftTime +
                    " s, Ankunft " + info.arrivalTime)
            }
            val name = kurve?.getString(SCHL_NAME)
            if (!name.isNullOrBlank()) felder[Kieselstrasse.STRASSE] = Wert.Text(name)
            if (info.arrivalTime > 0L) {
                felder[Kieselstrasse.ANKUNFT] = Wert.Zahl(info.arrivalTime)
            }
            if (info.leftDistance > 0) {
                felder[Kieselstrasse.REST] = Wert.Zahl(info.leftDistance.toLong())
            }
        } catch (e: Exception) {
            Log.w(TAG, "getAppInfo fehlgeschlagen: " + e.message)
        }
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
