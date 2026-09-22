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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

        Ton.setze(Ton.GESUNDHEIT)
        setContentView(baueAnsicht())
        EmpfangsDienst.starte(this)

        // WAS SCHON EINMAL GELESEN WURDE, STEHT SOFORT DA. Solange der Prozess
        // lebt, ist der letzte Stand noch im Speicher; ihn zu zeigen, waehrend
        // der neue kommt, ist besser als ein leerer Schirm - er ist hoechstens
        // ein paar Minuten alt und wird gleich darauf ersetzt.
        zuletzt?.let { zeigeStand(it) }
        zuletztTraining?.let { zeigeTraining(it) }
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
            setColorSchemeColors(akzentfarbe())
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
            // Auch der Kreisel beim Ziehen nimmt den Ton des Reiters an.
            Ton.setze(welcher)
            wischer.setColorSchemeColors(akzentfarbe())
            roller.scrollTo(0, 0)
        })

        aussen.randUmSystemleisten()
        return aussen
    }

    /**
     * Alles neu holen - nebeneinander, und der Wischer wartet auf beide.
     *
     * Die Trainings haengen nicht am Tagesstand. Sie hintereinander zu holen
     * hiesse, die Wartezeiten zu addieren; der Schirm stuende so lange leer
     * wie beide zusammen.
     */
    private fun auffrischen() {
        // Beim allerersten Laden steht noch nichts da. Dann dreht der
        // Kreisel, damit der leere Schirm als "kommt gleich" zu lesen ist und
        // nicht als "hier ist nichts".
        if (zuletzt == null) wischer.isRefreshing = true
        lifecycleScope.launch {
            try {
                val training = async { TrainingTab.hole(this@HauptActivity) }
                standLaden()
                zeigeTraining(training.await().also { zuletztTraining = it })
            } finally {
                wischer.isRefreshing = false
            }

            // EINMAL JE START, nicht bei jedem Zurueckkehren: die Akte haelt
            // rund dreissig Tage, und die einmal abzuschreiben ist der
            // Unterschied zwischen "in drei Wochen sagt dir die App etwas"
            // und "jetzt". ERST NACH DEM ERSTEN BILD - vorher stuende es mit
            // dreissig Tagen Abfragen vor den Zahlen von heute in der Schlange.
            //
            // ABGEHAKT WIRD ES ERST, WENN ETWAS KAM. Nach einer Neuinstallation
            // fehlt beim ersten Laden noch die Erlaubnis; wer das Nachtragen
            // dann als erledigt abhakte, holte die Vergangenheit nie - und
            // der Trend bliebe leer.
            if (!nachgetragen) {
                nachgetragen = Gesundheit(this@HauptActivity).nachtragen() > 0
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
    private suspend fun standLaden() = coroutineScope {
        val ich = this@HauptActivity
        // Zwei Abfragen mehr, beide gruppiert: das Profil von heute und der
        // Schnitt der letzten zwei Wochen. Sie laufen NEBEN dem Tagesstand,
        // nicht danach.
        val profilHeute = async { Gesundheit(ich).bewegungsprofil(1) }
        val profilTypisch = async { Gesundheit(ich).bewegungsprofil(14) }
        val stand = Gesundheit(ich).lies()
        val fehlt =
            if (stand == null) emptySet()
            else Akte(ich).fehlendeBerechtigungen(Gesundheit.BERECHTIGUNGEN)

        val bild = Tagesbild(stand, fehlt, profilHeute.await(), profilTypisch.await())
        zuletzt = bild
        zeigeStand(bild)

        // Die Zahlen sind eben gelesen; das Widget soll nicht aelteres zeigen
        // als der Schirm daneben.
        GesundheitWidget.stosseAn(ich)
    }

    /**
     * Gesundheit und Ernaehrung bauen - ohne zu warten, aus dem, was gelesen ist.
     *
     * JEDER REITER WIRD IN SEINEM TON GEBAUT. Die Farbe steckt bis in die
     * Balken der Diagramme; sie muss stehen, BEVOR gebaut wird - und danach
     * wieder auf dem Reiter, den man sieht. Sonst baute das naechste Stueck
     * im Ton des zuletzt gebauten, und das war die Ernaehrung.
     */
    private fun zeigeStand(bild: Tagesbild) {
        val ich = this@HauptActivity
        gesundheit.removeAllViews()

        // DIESE KARTE STEHT OBEN, nicht unten. Ohne Lese-Erlaubnis antwortet
        // die Akte nicht mit Nein, sondern gar nicht - die Felder blieben leer
        // und saehen aus wie ein Fehler der App.
        Ton.setze(Ton.GESUNDHEIT)
        if (bild.fehlt.isNotEmpty()) {
            val k = karte()
            k.addView(schild(false, "Lese-Erlaubnis fehlt"))
            k.addView(zart(
                bild.fehlt.size.toString() + " von " +
                    Gesundheit.BERECHTIGUNGEN.size + " Werten sind " +
                    "gesperrt. Gesperrt heisst hier leer — die Akte sagt " +
                    "nicht Nein, sie schweigt."
            ))
            k.addView(knopfHaupt("Erlaubnis erteilen", breit = true) {
                erlaubnisStarter?.launch(bild.fehlt) ?: melde("Noch nicht bereit")
            })
            gesundheit.addView(k)
        }
        gesundheit.addView(GesundheitTab.baue(
            ich, bild.stand, bild.profilHeute, bild.profilTypisch, ich
        ))

        ernaehrung.removeAllViews()
        Ton.setze(Ton.ERNAEHRUNG)
        ernaehrung.addView(ErnaehrungTab.baue(ich, bild.stand, ich))

        Ton.setze(sichtbarerReiter())
    }

    /** Die Trainings der letzten drei Monate, samt ihrer Strecke. */
    private fun zeigeTraining(sitzungen: List<TrainingTab.Eintrag>) {
        training.removeAllViews()
        Ton.setze(Ton.TRAINING)
        training.addView(TrainingTab.baue(this@HauptActivity, sitzungen))
        Ton.setze(sichtbarerReiter())
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

    private fun sichtbarerReiter(): Int = when {
        training.visibility == View.VISIBLE -> Ton.TRAINING
        ernaehrung.visibility == View.VISIBLE -> Ton.ERNAEHRUNG
        else -> Ton.GESUNDHEIT
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /** Was der Schirm aus einem Durchgang durch die Akte baut. */
    private class Tagesbild(
        val stand: Gesundheit.Stand?,
        val fehlt: Set<String>,
        val profilHeute: List<Gesundheit.Punkt>,
        val profilTypisch: List<Gesundheit.Punkt>,
    )

    companion object {
        /**
         * Der zuletzt gelesene Stand, solange der Prozess lebt.
         *
         * Nur Zahlen, keine Ansichten: eine gemerkte Ansicht hielte die
         * Activity fest, in der sie gebaut wurde.
         */
        private var zuletzt: Tagesbild? = null
        private var zuletztTraining: List<TrainingTab.Eintrag>? = null

        /** Das Nachtragen gehoert zum Start der App, nicht zu jedem Schirm. */
        private var nachgetragen = false
    }
}
