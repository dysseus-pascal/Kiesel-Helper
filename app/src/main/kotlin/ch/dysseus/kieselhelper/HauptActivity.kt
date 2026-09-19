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
 * Zwei Reiter unten, ein Zahnrad oben.
 *
 * WAS MAN TAEGLICH ANSCHAUT, IST DIE GANZE APP: die Zahlen von heute und das
 * Muster dahinter. Die Technik - Zustand, Erlaubnisse, Aufgabenliste - stand
 * lange als dritter Reiter daneben und nahm den beiden anderen Platz weg,
 * obwohl man sie zweimal im Jahr braucht. Sie liegt jetzt hinter dem Zahnrad
 * in [EinstellungenActivity].
 *
 * DIE REITER SITZEN UNTEN, weil der Daumen dort ist. Der Kopf mit Name und
 * Zahnrad steht fest, dazwischen scrollt der Inhalt und laesst sich von oben
 * zum Auffrischen ziehen.
 */
class HauptActivity : ComponentActivity(), Eingaben {

    private lateinit var wurzel: LinearLayout
    private lateinit var gesundheit: LinearLayout
    private lateinit var trend: LinearLayout
    private lateinit var trendLeiste: TrendTab.Leiste
    private lateinit var trendInhalt: LinearLayout
    private lateinit var wischer: SwipeRefreshLayout

    /** Welche Groesse der Trend-Schirm gerade auswertet. */
    private var trendWahl = 0
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
            trendLaden()
        }
    }

    override fun onResume() {
        super.onResume()
        auffrischen()
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
        trend = spalte()

        val aussen = spalte()
        aussen.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        aussen.addView(kopfleiste(getString(R.string.app_name)) {
            startActivity(Intent(this, EinstellungenActivity::class.java))
        })

        wurzel.addView(gesundheit)
        wurzel.addView(trend)
        trend.visibility = View.GONE

        // Die Auswahlleiste des Trends wird EINMAL gebaut und danach nur noch
        // umgefaerbt - sonst stuende sie nach jedem Umschalten wieder ganz
        // links, waehrend man rechts aussen getippt hat.
        trendLeiste = TrendTab.leiste(this) { gewaehlt ->
            trendWahl = gewaehlt
            trendLeiste.male(gewaehlt)
            lifecycleScope.launch { trendLaden() }
        }
        trendLeiste.male(trendWahl)
        trendInhalt = spalte()
        trend.addView(trendLeiste.sicht)
        trend.addView(trendInhalt)

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
        aussen.addView(fussleiste(listOf("Gesundheit", "Trend")) { welcher ->
            gesundheit.visibility = if (welcher == 0) View.VISIBLE else View.GONE
            trend.visibility = if (welcher == 1) View.VISIBLE else View.GONE
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
                gesundheitLaden()
                trendLaden()
            } finally {
                wischer.isRefreshing = false
            }
        }
    }

    /**
     * Den Trend-Schirm neu rechnen.
     *
     * DIE TABELLE WIRD IM HINTERGRUND GELESEN. Ein Jahr sind dreihundert
     * Zeilen - das ist schnell, aber SQLite auf dem Hauptfaden ist es nie,
     * und der Fehler faellt erst auf, wenn die Tabelle gross genug ist.
     *
     * Getauscht wird NUR der Inhalt unter der Auswahlleiste. Die Leiste selbst
     * bleibt stehen, samt ihrer Schiebestellung.
     */
    private suspend fun trendLaden() {
        val gruppe = TrendTab.GRUPPEN[trendWahl]
        val (daten, umfang) = withContext(Dispatchers.IO) {
            val speicher = Speicher(this@HauptActivity)
            gruppe.spalten.associateWith { speicher.reihe(it) } to speicher.umfang()
        }

        // Die Wolke NUR fuer Herz. Sie liest vierzehn Tage Einzelmessungen aus
        // der Akte - das ist die teuerste Abfrage der App, und fuer die
        // Schritte-Gruppe braucht sie niemand.
        val wolke = if (gruppe.name == "Herz") {
            Gesundheit(this@HauptActivity).pulswolke()
        } else {
            emptyList()
        }

        trendInhalt.removeAllViews()
        trendInhalt.addView(
            TrendTab.inhalt(this@HauptActivity, trendWahl, daten, umfang, wolke)
        )
    }

    /**
     * Die Zahlen neu holen.
     *
     * JEDES MAL NEU, auch beim blossen Zurueckkehren. Die Akte aendert sich,
     * waehrend die App im Hintergrund liegt - ein gemerkter Stand von heute
     * Morgen saehe genauso aus wie einer von eben.
     */
    private suspend fun gesundheitLaden() {
        val stand = Gesundheit(this@HauptActivity).lies()
        val fehlt =
            if (stand == null) emptySet()
            else Akte(this@HauptActivity)
                .fehlendeBerechtigungen(Gesundheit.BERECHTIGUNGEN)

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
        val ich = this@HauptActivity
        val profilHeute = Gesundheit(ich).bewegungsprofil(1)
        val profilTypisch = Gesundheit(ich).bewegungsprofil(14)
        gesundheit.addView(
            GesundheitTab.baue(ich, stand, profilHeute, profilTypisch, this@HauptActivity)
        )

        // Die Zahlen sind eben gelesen; das Widget soll nicht aelteres zeigen
        // als der Schirm daneben.
        GesundheitWidget.stosseAn(this@HauptActivity)
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
            gesundheitLaden()
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
            gesundheitLaden()
        }
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
