package ch.dysseus.kieselhelper

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Der zweite Bildschirm: eine Beschreibung EINBINDEN.
 *
 * Alles, was man einmal tut und dann lange nicht mehr, steht hier: das
 * Eingabefeld, die Erklaerung, wozu das gut ist, und der Katalog dessen, was
 * eine Beschreibung ueberhaupt verlangen darf. Der erste Bildschirm bleibt so
 * frei fuer die Frage, die man oft hat — was traegt gerade ein?
 */
class EinbindenActivity : ComponentActivity() {

    private lateinit var adresse: EditText
    private lateinit var erlaubnisStarter: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vertrag: ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()
        // Nach dem Erlaubnisdialog ist hier nichts mehr zu tun: der erste
        // Bildschirm frischt sich beim Zurueckkommen selbst auf.
        erlaubnisStarter = registerForActivityResult(vertrag) { finish() }

        setContentView(baueAnsicht())
    }

    private fun baueAnsicht(): ScrollView {
        val wurzel = spalte().apply {
            setPadding(dp(20f), dp(28f), dp(20f), dp(32f))
        }

        wurzel.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        wurzel.luft(16f)
        wurzel.addView(kopf(getString(R.string.einbinden_titel)))
        wurzel.luft(8f)
        wurzel.addView(zart(getString(R.string.intro)))

        // --- Adresse ---
        wurzel.addView(abschnitt(getString(R.string.abschnitt_quelle)))
        val quelle = karte()
        quelle.addView(fliesstext(getString(R.string.adresse_titel)))
        quelle.luft(10f)
        adresse = eingabefeld(getString(R.string.adresse_hinweis)).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        quelle.addView(adresse)
        quelle.luft(12f)
        quelle.addView(knopfHaupt(getString(R.string.laden), breit = true) {
            lade(adresse.text.toString())
        })
        quelle.luft(12f)
        quelle.addView(zart(getString(R.string.kein_selbstlauf)))
        wurzel.addView(quelle)

        // --- Katalog ---
        wurzel.addView(abschnitt(getString(R.string.abschnitt_katalog)))
        val katalog = karte()
        katalog.addView(fliesstext(getString(R.string.katalog)))
        for (art in Satzart.entries) {
            katalog.addView(strich())
            katalog.addView(fliesstext(art.klartext))
            val einheit = if (art.einheit.isEmpty()) "" else "  ·  " + art.einheit
            katalog.addView(zart(art.id + einheit))
        }
        katalog.luft(14f)
        katalog.addView(zart(getString(R.string.katalog_hinweis)))
        wurzel.addView(katalog)

        return ScrollView(this).apply {
            isFillViewport = true
            addView(wurzel)
        }
    }

    /**
     * Holen, einlesen, ablegen — in dieser Reihenfolge und nur ganz.
     *
     * Bei Fehlern wird NICHTS abgelegt. Eine kaputte Beschreibung im Speicher
     * faende man sonst erst, wenn nachts eine Messung ankaeme und still
     * verschwaende.
     */
    private fun lade(eingabe: String) {
        val ziel = ModulSpeicher.zuDateiAdresse(eingabe)
        if (ziel.isEmpty()) return
        lifecycleScope.launch {
            val text = ModulSpeicher.hole(ziel).getOrElse { fehler ->
                melde(getString(R.string.nicht_geholt, fehler.message ?: "?"))
                return@launch
            }
            val ergebnis = Modul.lies(text)
            val m = ergebnis.modul
            if (m == null) {
                melde(ergebnis.fehler.joinToString("\n"))
                return@launch
            }
            ModulSpeicher(this@EinbindenActivity).lege(ziel, text)
            melde(getString(R.string.geladen, m.name))

            // Gleich nach dem Laden fragen, nicht erst beim Schreiben. Sonst
            // faellt eine fehlende Erlaubnis erst auf, wenn eine Messung da
            // ist. Gefragt wird nur nach dem, was DIESE Beschreibung braucht —
            // nicht nach dem ganzen Katalog.
            val fehlt = Akte(this@EinbindenActivity).fehlendeBerechtigungen(m.berechtigungen())
            if (fehlt.isEmpty()) finish() else erlaubnisStarter.launch(fehlt)
        }
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

}
