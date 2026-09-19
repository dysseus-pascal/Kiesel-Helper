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
            // KEIN WARTEN AUF DIE AIDL-VERBINDUNG MEHR. Uebergeben wird per
            // Intent; der startet OsmAnd selbst und braucht weder einen
            // gebundenen Dienst noch die Freischaltung unter Plugins.
            val aus = Kartenlink.uebergib(this@KartenlinkActivity, ziel)

            if (aus.geschafft) {
                Verlauf(this@KartenlinkActivity).merkeMeldung(
                    "Kartenlink an OsmAnd (" + aus.beschreibung + ")"
                )
                finish()
            } else {
                fertig(
                    "OsmAnd hat abgelehnt. Meist fehlt die Freigabe dort: " +
                        "Menü → Plugins → Kiesel-Helper einschalten."
                )
            }
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
