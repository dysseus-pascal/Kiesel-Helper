package ch.dysseus.kieselhelper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drei Reiter unten, ein Zahnrad oben.
 *
 * DIE REITER TRENNEN DREI FRAGEN, NICHT DREI DATENQUELLEN:
 * - **Gesundheit**, was der Tag mit einem gemacht hat: Bewegung, Schlaf, Herz.
 * - **Training**, was man selbst getan hat - die Aufzeichnungen der Uhr, mit
 *   der Strecke auf der Karte.
 * - **Ernährung**, was hineingeht: Wasser, Präparate, Koffein.
 *
 * DER TREND WAR EINMAL EIN REITER UND IST JETZT EIN SCHIRM DAHINTER. Er ist
 * eine Antwort und keine eigene Frage: man sieht 7985 Schritte und will
 * wissen, ob das viel ist. Dafuer fuehrt jede Karte weiter - ein Tippen, und
 * zwar gleich bei der richtigen Groesse, statt unten umschalten und oben
 * suchen.
 *
 * DIE TECHNIK - Zustand, Erlaubnisse, Aufgabenliste - stand lange als Reiter
 * daneben und nahm den anderen Platz weg, obwohl man sie zweimal im Jahr
 * braucht. Sie liegt hinter dem Zahnrad in [EinstellungenActivity].
 *
 * DIE REITER SITZEN UNTEN, weil der Daumen dort ist. Der Kopf mit Name und
 * Zahnrad steht fest, dazwischen scrollt der Inhalt und laesst sich von oben
 * zum Auffrischen ziehen.
 */
class HauptActivity : ComponentActivity(), Eingaben {

    private lateinit var wurzel: LinearLayout
    private lateinit var gesundheit: LinearLayout
    private lateinit var training: LinearLayout
    private lateinit var ernaehrung: LinearLayout
    private lateinit var wischer: SwipeRefreshLayout

    private var erlaubnisStarter: ActivityResultLauncher<Set<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vertrag: ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()
        erlaubnisStarter = registerForActivityResult(vertrag) { auffrischen() }

        // Ab Android 13 muss die Meldung des Vordergrunddienstes erlaubt sein.
        // Ohne sie laeuft der Dienst zwar, aber das System darf ihn frueher
        // beenden — und man sieht nicht, dass er ueberhaupt da ist.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        setContentView(baueAnsicht())
        EmpfangsDienst.starte(this)

        // EINMAL JE START, nicht bei jedem Zurueckkehren: die Akte haelt rund
        // dreissig Tage, und die einmal abzuschreiben ist der Unterschied
        // zwischen "in drei Wochen sagt dir die App etwas" und "jetzt".
        lifecycleScope.launch {
            Gesundheit(this@HauptActivity).nachtragen()
        }
    }

    override fun onResume() {
        super.onResume()
        TrainingTab.weiter()
        auffrischen()
    }

    override fun onPause() {
        TrainingTab.anhalten()
        super.onPause()
    }

    override fun onDestroy() {
        TrainingTab.vergiss()
        super.onDestroy()
    }

    /**
     * Kopf fest, Reiter fest, Inhalt beweglich.
     *
     * NUR DIE MITTE SCROLLT. Vorher lagen Titel und Reiter im Roller und waren
     * nach der ersten Karte weg - man wusste dann nicht mehr, in welchem
     * Reiter man steht, und kam nur durch Hochscrollen zurueck.
     */
    private fun baueAnsicht(): View {
        wurzel = spalte().apply { setPadding(dp(16f), 0, dp(16f), dp(20f)) }
        gesundheit = spalte()
        training = spalte()
        ernaehrung = spalte()

        val aussen = spalte()
        aussen.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        aussen.addView(kopfleiste(getString(R.string.app_name)) {
            startActivity(Intent(this, EinstellungenActivity::class.java))
        })

        wurzel.addView(gesundheit)
        wurzel.addView(training)
        wurzel.addView(ernaehrung)
        training.visibility = View.GONE
        ernaehrung.visibility = View.GONE

        val roller = ScrollView(this)
        roller.addView(wurzel)

        wischer = SwipeRefreshLayout(this).apply {
            addView(roller)
            setOnRefreshListener { auffrischen() }
            setColorSchemeColors(farbe(R.color.akzent))
            setProgressBackgroundColorSchemeColor(farbe(R.color.karte))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        aussen.addView(wischer)

        // Beim Wechsel nach oben rollen: der neue Reiter faengt oben an, und
        // eine halb heruntergescrollte Seite, die man nie angeschaut hat,
        // sieht aus wie ein Fehler.
        aussen.addView(fussleiste(listOf("Gesundheit", "Training", "Ernährung")) { welcher ->
            gesundheit.visibility = if (welcher == 0) View.VISIBLE else View.GONE
            training.visibility = if (welcher == 1) View.VISIBLE else View.GONE
            ernaehrung.visibility = if (welcher == 2) View.VISIBLE else View.GONE
            roller.scrollTo(0, 0)
        })

        aussen.randUmSystemleisten()
        return aussen
    }

    /**
     * Alles neu holen - der Reihe nach, damit der Wischer die Wahrheit sagt.
     *
     * Nebenlaeufig waere es schneller, aber dann muesste jemand zaehlen, wann
     * der Letzte fertig ist. Hintereinander dauert es zwei Sekunden und der
     * Kreisel verschwindet genau dann, wenn nichts mehr nachkommt.
     */
    private fun auffrischen() {
        lifecycleScope.launch {
            try {
                standLaden()
                trainingLaden()
            } finally {
                wischer.isRefreshing = false
            }
        }
    }

    /**
     * Die Zahlen neu holen - einmal fuer zwei Reiter.
     *
     * EINE ABFRAGE, NICHT ZWEI. Gesundheit und Ernaehrung lesen denselben
     * Tagesstand; ihn je Reiter zu holen hiesse, die teuerste Stelle der App
     * doppelt zu bezahlen - und die beiden Schirme koennten auseinanderlaufen.
     *
     * JEDES MAL NEU, auch beim blossen Zurueckkehren. Die Akte aendert sich,
     * waehrend die App im Hintergrund liegt - ein gemerkter Stand von heute
     * Morgen saehe genauso aus wie einer von eben.
     */
    private suspend fun standLaden() {
        val ich = this@HauptActivity
        val stand = Gesundheit(ich).lies()
        val fehlt =
            if (stand == null) emptySet()
            else Akte(ich).fehlendeBerechtigungen(Gesundheit.BERECHTIGUNGEN)

        gesundheit.removeAllViews()

        // DIESE KARTE STEHT OBEN, nicht unten. Ohne Lese-Erlaubnis antwortet
        // die Akte nicht mit Nein, sondern gar nicht - die Felder blieben leer
        // und saehen aus wie ein Fehler der App.
        if (fehlt.isNotEmpty()) {
            val k = karte()
            k.addView(schild(false, "Lese-Erlaubnis fehlt"))
            k.addView(zart(
                fehlt.size.toString() + " von " +
                    Gesundheit.BERECHTIGUNGEN.size + " Werten sind " +
                    "gesperrt. Gesperrt heisst hier leer — die Akte sagt " +
                    "nicht Nein, sie schweigt."
            ))
            k.addView(knopfHaupt("Erlaubnis erteilen", breit = true) {
                erlaubnisStarter?.launch(fehlt) ?: melde("Noch nicht bereit")
            })
            gesundheit.addView(k)
        }

        // Zwei Abfragen mehr, beide gruppiert: das Profil von heute und der
        // Schnitt der letzten zwei Wochen.
        val profilHeute = Gesundheit(ich).bewegungsprofil(1)
        val profilTypisch = Gesundheit(ich).bewegungsprofil(14)
        gesundheit.addView(GesundheitTab.baue(ich, stand, profilHeute, profilTypisch, ich))

        ernaehrung.removeAllViews()
        ernaehrung.addView(ErnaehrungTab.baue(ich, stand, ich))

        // Die Zahlen sind eben gelesen; das Widget soll nicht aelteres zeigen
        // als der Schirm daneben.
        GesundheitWidget.stosseAn(ich)
    }

    /** Die Trainings der letzten drei Monate, samt ihrer Strecke. */
    private suspend fun trainingLaden() {
        val sitzungen = TrainingTab.hole(this@HauptActivity)
        training.removeAllViews()
        training.addView(TrainingTab.baue(this@HauptActivity, sitzungen))
    }

    // --- Was kein Sensor weiss ---

    /**
     * Sofort schreiben, dann den Schirm neu bauen.
     *
     * Kein Zwischenzustand im Speicher der Activity: der Schirm liest, was in
     * der Tabelle steht. Ein Knopf, der sich faerbt, bevor der Wert
     * angekommen ist, luegt in genau dem Fall, in dem das Schreiben scheitert.
     */
    override fun setzeEnergie(wert: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                Speicher(this@HauptActivity).merke(
                    Einstellungen.heute(this@HauptActivity),
                    mapOf("energie" to wert.toDouble()),
                )
            }
            standLaden()
        }
    }

    /**
     * Koffein dazuzaehlen - und den Zeitpunkt merken.
     *
     * DER ZEITPUNKT IST DIE INTERESSANTE HAELFTE. Wie viel Koffein ein Tag
     * hatte, sagt wenig; wann das letzte kam, erklaert die Nacht.
     */
    override fun fuegeKoffein(mg: Int) {
        lifecycleScope.launch {
            val augenblick = java.time.Instant.now()
            withContext(Dispatchers.IO) {
                val heute = Einstellungen.heute(this@HauptActivity)
                val jetzt = java.time.LocalTime.now()
                val speicher = Speicher(this@HauptActivity)
                speicher.zaehleDazu(heute, "koffein_mg", mg.toDouble())
                speicher.merke(
                    heute,
                    mapOf("koffein_letzt" to (jetzt.hour * 60 + jetzt.minute).toDouble()),
                )
            }
            // UND IN DIE AKTE. Die eigene Tabelle traegt den Trend, die Akte
            // traegt es zu allen anderen Apps - und ueberlebt eine
            // Neuinstallation dieser hier.
            Aufgaben.koffein(this@HauptActivity, mg, augenblick)?.let {
                Verlauf(this@HauptActivity).merkeMeldung(it)
            }
            standLaden()
        }
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
