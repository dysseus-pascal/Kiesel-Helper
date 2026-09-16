package ch.dysseus.kieselhelper

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Die einzige Ansicht: zeigen, was geladen ist, und das Noetige erfragen.
 *
 * Bewusst ohne Compose und ohne Layout-Dateien - die App besteht im Kern aus
 * einem Empfaenger, und ein Bildschirm, den man dreimal im Leben oeffnet,
 * rechtfertigt keinen Baukasten. Weniger Abhaengigkeiten heisst hier auch:
 * weniger, was schiefgehen kann an einer App, die sich nur am Telefon selbst
 * erproben laesst.
 *
 * Gegenueber der Vorgaenger-App ist eines dazugekommen: die Liste der
 * geladenen Beschreibungen mit ihren Knoepfen. Wer nicht sieht, WAS gerade in
 * seine Gesundheitsakte schreibt, kann es auch nicht abstellen.
 */
class HauptActivity : ComponentActivity() {

    private lateinit var zustand: TextView
    private lateinit var modulListe: LinearLayout
    private lateinit var adresse: EditText
    private lateinit var erlaubnisStarter: ActivityResultLauncher<Set<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vertrag: ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()
        erlaubnisStarter = registerForActivityResult(vertrag) { auffrischen() }

        // Ab Android 13 muss die Meldung des Vordergrunddienstes erlaubt sein.
        // Ohne sie laeuft der Dienst zwar, aber das System darf ihn frueher
        // beenden - und man sieht nicht, dass er ueberhaupt da ist.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // Der Empfaenger muss zur Laufzeit angemeldet sein, sonst erreicht ihn
        // der implizite Broadcast der Pebble-App nicht. Siehe EmpfangsDienst.
        EmpfangsDienst.starte(this)

        setContentView(ScrollView(this).apply { addView(baueAnsicht()) })
    }

    override fun onResume() {
        super.onResume()
        auffrischen()
    }

    // --- Aufbau ---

    private fun baueAnsicht(): LinearLayout {
        val wurzel = spalte().apply { setPadding(48, 64, 48, 64) }

        wurzel.addView(titel(getString(R.string.app_name), 26f))
        wurzel.addView(absatz(getString(R.string.intro)))

        zustand = TextView(this).apply {
            textSize = 15f
            setPadding(0, 24, 0, 0)
        }
        wurzel.addView(zustand)

        wurzel.addView(titel(getString(R.string.module), 20f, 40))
        modulListe = spalte()
        wurzel.addView(modulListe)

        adresse = EditText(this).apply {
            hint = getString(R.string.adresse_hinweis)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        wurzel.addView(adresse, breit(24))

        wurzel.addView(knopf(getString(R.string.laden)) {
            lade(adresse.text.toString(), neu = true)
        })
        wurzel.addView(kleingedrucktes(getString(R.string.kein_selbstlauf)))

        wurzel.addView(knopf(getString(R.string.erlaubnis)) { frageErlaubnis() })

        wurzel.addView(titel(getString(R.string.katalog), 20f, 48))
        wurzel.addView(absatz(Satzart.entries.joinToString("\n") {
            val einheit = if (it.einheit.isEmpty()) "" else " (" + it.einheit + ")"
            it.id + " - " + it.klartext + einheit
        }))
        wurzel.addView(kleingedrucktes(getString(R.string.katalog_hinweis)))

        return wurzel
    }

    // --- Anzeige auffrischen ---

    private fun auffrischen() {
        zeichneModule()
        lifecycleScope.launch { zustand.text = baueZustand() }
    }

    /**
     * Die Modulliste neu zeichnen.
     *
     * Gelesen wird ueber alleEintraege() und nicht ueber alle(): eine
     * Beschreibung, die sich nicht einlesen laesst, faellt dort still heraus.
     * Genau die muss man aber SEHEN - sonst wundert man sich wochenlang, warum
     * nichts ankommt.
     */
    private fun zeichneModule() {
        val speicher = ModulSpeicher(this)
        val eintraege = speicher.alleEintraege()
        modulListe.removeAllViews()

        if (eintraege.isEmpty()) {
            modulListe.addView(absatz(getString(R.string.keine_module)))
            return
        }

        for (e in eintraege) {
            val kasten = spalte().apply { setPadding(0, 24, 0, 24) }
            val ergebnis = Modul.lies(e.text)
            val m = ergebnis.modul

            if (m == null) {
                kasten.addView(titel(e.quelle, 16f))
                kasten.addView(
                    absatz(
                        "Nicht verwendbar:\n" +
                            ergebnis.fehler.joinToString("\n") { f -> "- " + f }
                    )
                )
            } else {
                kasten.addView(titel(m.name, 18f))
                if (m.beschreibung.isNotEmpty()) kasten.addView(absatz(m.beschreibung))
                kasten.addView(
                    kleingedrucktes(
                        m.regeln.joinToString("\n") { r ->
                            "traegt " + r.art.klartext + " ein, aus " + (r.wertAus ?: r.zeitAus)
                        } +
                            "\n" + getString(R.string.geholt_am, stempel(e.geholtAm)) +
                            "\n" + e.quelle
                    )
                )
                // Fehlende Erlaubnisse stehen bei der Beschreibung, die sie
                // braucht, und nicht in einer allgemeinen Liste: so sieht man,
                // WELCHE Beschreibung gerade nicht arbeiten kann.
                val marke = TextView(this).apply { textSize = 14f }
                kasten.addView(marke)
                lifecycleScope.launch {
                    val fehlt = Akte(this@HauptActivity)
                        .fehlendeBerechtigungen(m.berechtigungen())
                    marke.text =
                        if (fehlt.isEmpty()) "+ " + getString(R.string.erlaubnis_da)
                        else "! " + getString(R.string.erlaubnis_fehlt)
                }
            }

            val reihe = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            reihe.addView(knopf(getString(R.string.erneuern)) { lade(e.quelle, neu = false) })
            reihe.addView(
                knopf(getString(R.string.entfernen)) {
                    speicher.entferne(e.quelle)
                    auffrischen()
                }
            )
            kasten.addView(reihe)
            modulListe.addView(kasten)
        }
    }

    private suspend fun baueZustand(): String {
        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_UNAVAILABLE ->
                return getString(R.string.hc_fehlt)
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                return getString(R.string.hc_update)
        }

        val verlauf = Verlauf(this)
        val letzte = verlauf.letzteMeldung()
        val text = StringBuilder(getString(R.string.dienst_hinweis))
        text.append("\n\n")
        if (letzte.isEmpty()) {
            text.append(getString(R.string.nichts_bisher))
        } else {
            text.append(getString(R.string.zuletzt, stempel(verlauf.letzteMeldungAm())))
            text.append("\n")
            text.append(letzte)
        }
        return text.toString()
    }

    // --- Handlungen ---

    /**
     * Eine Beschreibung holen und ablegen.
     *
     * Eingelesen wird SOFORT, und bei Fehlern wird nichts abgelegt. Eine
     * kaputte Beschreibung im Speicher faende man sonst erst, wenn nachts eine
     * Messung ankaeme und still verschwaende.
     */
    private fun lade(eingabe: String, neu: Boolean) {
        val ziel = ModulSpeicher.zuDateiAdresse(eingabe)
        if (ziel.isEmpty()) return
        lifecycleScope.launch {
            val geholt = ModulSpeicher.hole(ziel)
            val text = geholt.getOrElse { fehler ->
                melde("Nicht geholt: " + (fehler.message ?: "unbekannter Fehler"))
                return@launch
            }
            val ergebnis = Modul.lies(text)
            val m = ergebnis.modul
            if (m == null) {
                melde(ergebnis.fehler.joinToString("\n"))
                return@launch
            }
            ModulSpeicher(this@HauptActivity).lege(ziel, text)
            if (neu) adresse.setText("")
            melde(m.name + " geladen")

            // Gleich nach dem Laden fragen, nicht erst beim Schreiben. Sonst
            // faellt eine fehlende Erlaubnis erst auf, wenn eine Messung da ist.
            val fehlt = Akte(this@HauptActivity).fehlendeBerechtigungen(m.berechtigungen())
            auffrischen()
            if (fehlt.isNotEmpty()) erlaubnisStarter.launch(fehlt)
        }
    }

    /**
     * Die Erlaubnis erfragen - genau die, die die geladenen Beschreibungen
     * brauchen.
     *
     * NICHT den ganzen Katalog: Health Connect zeigt im Dialog, was angefragt
     * UND im Manifest angemeldet ist. Wer alles anfragt, laesst jemanden sechs
     * Haken setzen, von denen er zwei braucht - und fragt damit nach Zugriff
     * auf Gesundheitsdaten, den die App gar nicht benutzt.
     */
    private fun frageErlaubnis() {
        val noetig = ModulSpeicher(this).alle().flatMap { it.berechtigungen() }.toSet()
        if (noetig.isEmpty()) {
            melde(getString(R.string.keine_module))
            return
        }
        lifecycleScope.launch {
            val fehlt = Akte(this@HauptActivity).fehlendeBerechtigungen(noetig)
            if (fehlt.isEmpty()) melde(getString(R.string.erlaubnis_da))
            else erlaubnisStarter.launch(fehlt)
        }
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    // --- kleine Bausteine ---

    private fun spalte() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun titel(text: String, groesse: Float, obenPx: Int = 0) = TextView(this).apply {
        this.text = text
        textSize = groesse
        setPadding(0, obenPx, 0, 8)
    }

    private fun absatz(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(0, 8, 0, 8)
    }

    private fun kleingedrucktes(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, 8, 0, 8)
    }

    private fun knopf(text: String, tue: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { tue() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16; gravity = Gravity.START }
    }

    private fun breit(obenPx: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = obenPx }

    private fun stempel(epochSekunden: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(epochSekunden * 1000))
}
