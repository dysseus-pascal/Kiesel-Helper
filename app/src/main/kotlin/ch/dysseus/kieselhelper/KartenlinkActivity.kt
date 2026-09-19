package ch.dysseus.kieselhelper

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ein Kartenlink, weitergereicht an OsmAnd.
 *
 * WARUM DAS EINE EIGENE ACTIVITY IST und kein stiller Empfaenger: ein
 * Kurzlink muss erst aufgeloest werden, und das ist ein Netzaufruf. Ohne
 * Fenster stuende man drei Sekunden vor einem Bildschirm, auf dem nichts
 * geschieht, und tippte ein zweites Mal.
 *
 * ANDROID GIBT UNS DIESE LINKS NICHT VON SELBST. Seit Android 12 muss eine
 * App den Besitz einer Adresse nachweisen, um sie zu beanspruchen - fuer
 * google.com kann das niemand ausser Google. Der Mensch muss die Verknuepfung
 * deshalb EINMAL von Hand erlauben:
 *
 *   Einstellungen -> Apps -> Kiesel-Helper -> Standardmaessig oeffnen
 *   -> Links hinzufuegen -> Haken bei den Google-Maps-Adressen
 *
 * Das steht auch im Einstellungs-Schirm der App. Es laesst sich nicht
 * automatisieren, und das ist richtig so: eine App, die sich unbemerkt vor
 * fremde Links setzen koennte, waere ein Angriffswerkzeug.
 */
class KartenlinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(baueAnsicht("Link wird gelesen …"))

        val roh = intent?.dataString
        if (roh.isNullOrBlank()) {
            fertig("Kein Link dabei.")
            return
        }

        lifecycleScope.launch {
            // Erst dem Kurzlink folgen - im Hintergrund, ein Netzaufruf
            // gehoert nicht auf den Hauptfaden.
            val voll = withContext(Dispatchers.IO) {
                if (Kartenlink.istKurzlink(roh)) Kartenlink.folge(roh) else roh
            }
            val ziel = Kartenlink.zerlege(voll)
            if (ziel == null || !ziel.brauchbar) {
                fertig("Aus diesem Link liess sich kein Ziel lesen.")
                return@launch
            }
            if (!OsmandNavigation.verbunden) {
                // Der Dienst haelt die Verbindung; laeuft er gerade nicht,
                // einmal anstossen und einen Augenblick geben.
                EmpfangsDienst.starte(this@KartenlinkActivity)
                OsmandNavigation.versucheErneut(this@KartenlinkActivity)
                kotlinx.coroutines.delay(1200)
            }

            val geschafft = when {
                ziel.lat != null && ziel.lon != null && ziel.text.isNullOrBlank() ->
                    OsmandNavigation.navigiere(null, ziel.lat, ziel.lon)
                // MIT NAMEN SUCHT OSMAND SELBST. Die Koordinate aus einem
                // Google-Link ist oft die Bildmitte und nicht der Eingang;
                // der Name trifft genauer, und die Koordinate sagt OsmAnd,
                // wo es suchen soll.
                !ziel.text.isNullOrBlank() && ziel.lat != null && ziel.lon != null ->
                    OsmandNavigation.sucheUndNavigiere(ziel.text, ziel.lat, ziel.lon)
                !ziel.text.isNullOrBlank() ->
                    OsmandNavigation.sucheUndNavigiere(ziel.text, 0.0, 0.0)
                else -> false
            }

            if (geschafft) {
                Verlauf(this@KartenlinkActivity).merkeMeldung(
                    "Kartenlink an OsmAnd: " + (ziel.text ?: (ziel.lat.toString() + ", " + ziel.lon))
                )
                starteOsmAnd()
                finish()
            } else {
                fertig(
                    "OsmAnd hat abgelehnt. Meist fehlt die Freigabe dort: " +
                        "Menü → Plugins → Kiesel-Helper einschalten."
                )
            }
        }
    }

    /**
     * OsmAnd nach vorne holen.
     *
     * Die Fuehrung laeuft nach [OsmandNavigation.navigiere] schon - aber im
     * Hintergrund. Wer auf einen Kartenlink tippt, will die Karte sehen.
     */
    private fun starteOsmAnd() {
        val start = packageManager.getLaunchIntentForPackage("net.osmand.plus")
            ?: packageManager.getLaunchIntentForPackage("net.osmand")
            ?: packageManager.getLaunchIntentForPackage("net.osmand.dev")
        if (start != null) {
            start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(start)
        }
    }

    private fun fertig(meldung: String) {
        setContentView(baueAnsicht(meldung))
    }

    private fun baueAnsicht(meldung: String): LinearLayout {
        val wurzel = spalte().apply {
            setPadding(dp(24f), dp(40f), dp(24f), dp(24f))
        }
        wurzel.addView(kopf("Nach OsmAnd"))
        wurzel.luft(10f)
        wurzel.addView(fliesstext(meldung))
        wurzel.luft(16f)
        wurzel.addView(knopfLeise("Schliessen") { finish() })
        wurzel.randUmSystemleisten()
        return wurzel
    }
}
