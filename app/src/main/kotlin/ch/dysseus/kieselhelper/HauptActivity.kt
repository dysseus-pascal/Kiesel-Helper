package ch.dysseus.kieselhelper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Zwei Reiter: was der Koerper meldet, und was die App tut.
 *
 * GESUNDHEIT STEHT VORNE, weil man deswegen die App oeffnet. Die
 * Gesundheitsakte selbst kann alles und zeigt darum nichts zuerst; hier stehen
 * acht Zahlen auf einem Schirm, ohne Suchen.
 *
 * TECHNIK IST DER ALTE SCHIRM. Er zeigt drei feste Aufgaben und fragt nach
 * nichts. Vorher stand dort eine Liste eingebundener Beschreibungen mit einem
 * Knopf zum Nachladen - die App konnte Dinge tun, die ihr niemand
 * einprogrammiert hatte. Das war richtig gedacht fuer eine App, die viele
 * benutzen; diese benutzt einer, und fuer ihn sind es drei Aufgaben.
 *
 * Was bleibt, ist die Frage nach der Erlaubnis. Die kann kein Code sich selbst
 * geben.
 */
class HauptActivity : ComponentActivity() {

    private lateinit var wurzel: LinearLayout
    private lateinit var zustand: LinearLayout
    private lateinit var gesundheit: LinearLayout
    private lateinit var technik: LinearLayout
    private lateinit var trend: LinearLayout
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
            auffrischenTrend()
        }
    }

    override fun onResume() {
        super.onResume()
        // Nochmal bei OsmAnd anklopfen. Wer dort eben den Schalter umgelegt
        // hat, kommt als Naechstes hierher und will sehen, dass es wirkt.
        OsmandNavigation.versucheErneut(this)
        auffrischen()
    }

    private fun baueAnsicht(): ScrollView {
        wurzel = spalte()
        zustand = spalte()
        gesundheit = spalte()
        trend = spalte()
        technik = spalte()

        wurzel.addView(kopf(getString(R.string.app_name)))
        wurzel.addView(reiterleiste(listOf("Gesundheit", "Trend", "Technik")) { welcher ->
            gesundheit.visibility = if (welcher == 0) View.VISIBLE else View.GONE
            trend.visibility = if (welcher == 1) View.VISIBLE else View.GONE
            technik.visibility = if (welcher == 2) View.VISIBLE else View.GONE
        })
        wurzel.luft(10f)
        wurzel.addView(gesundheit)
        wurzel.addView(trend)
        wurzel.addView(technik)
        trend.visibility = View.GONE
        technik.visibility = View.GONE

        technik.addView(fliesstext(
            "Nimmt entgegen, was die Uhr meldet, und holt bei OsmAnd, was für " +
                "die Navigation auf die Uhr gehört."
        ))
        technik.luft(8f)
        technik.addView(abschnitt("ZUSTAND"))
        technik.addView(zustand)

        technik.luft(8f)
        technik.addView(abschnitt("WAS SIE TUT"))
        technik.addView(aufgabenKarte(
            "Drinktervall → Gesundheitsakte",
            "Jedes getrunkene Glas wird als Wassermenge eingetragen, mit dem " +
                "Zeitpunkt von der Uhr. Dasselbe Glas nur einmal."
        ))
        technik.addView(aufgabenKarte(
            "Herzintervall → Gesundheitsakte",
            "Die nächtliche RMSSD-Messung wird als Herzratenvariabilität " +
                "eingetragen."
        ))
        technik.addView(aufgabenKarte(
            "OsmAnd → Kieselstrasse",
            "Abbiegeart, Entfernung, Strasse und Ankunftszeit gehen an die Uhr " +
                "— aus OsmAnds eigener Schnittstelle, nicht aus seiner " +
                "Benachrichtigung."
        ))

        technik.luft(12f)
        technik.addView(knopfHaupt("Verlauf ansehen", breit = true) {
            startActivity(Intent(this, VerlaufActivity::class.java))
        })
        technik.luft(12f)

        val roller = ScrollView(this)
        roller.addView(wurzel)
        roller.randUmSystemleisten()
        return roller
    }

    private fun aufgabenKarte(titel: String, text: String): LinearLayout {
        val k = karte()
        k.addView(kartentitel(titel))
        k.addView(zart(text))
        return k
    }

    private fun auffrischen() {
        auffrischenGesundheit()
        auffrischenTrend()
        auffrischenTechnik()
    }

    /**
     * Den Trend-Schirm neu rechnen.
     *
     * DIE TABELLE WIRD IM HINTERGRUND GELESEN. Ein Jahr sind dreihundert
     * Zeilen - das ist schnell, aber SQLite auf dem Hauptfaden ist es nie,
     * und der Fehler faellt erst auf, wenn die Tabelle gross genug ist.
     */
    private fun auffrischenTrend() {
        lifecycleScope.launch {
            val groesse = TrendTab.GROESSEN[trendWahl]
            val (reihe, umfang) = withContext(Dispatchers.IO) {
                val speicher = Speicher(this@HauptActivity)
                speicher.reihe(groesse.spalte) to speicher.umfang()
            }
            trend.removeAllViews()
            trend.addView(TrendTab.baue(
                this@HauptActivity, trendWahl, reihe, umfang
            ) { gewaehlt ->
                trendWahl = gewaehlt
                auffrischenTrend()
            })
        }
    }

    /**
     * Die Zahlen neu holen.
     *
     * JEDES MAL NEU, auch beim blossen Zurueckkehren. Die Akte aendert sich,
     * waehrend die App im Hintergrund liegt - ein gemerkter Stand von heute
     * Morgen saehe genauso aus wie einer von eben.
     */
    private fun auffrischenGesundheit() {
        lifecycleScope.launch {
            val stand = Gesundheit(this@HauptActivity).lies()
            val fehlt =
                if (stand == null) emptySet()
                else Akte(this@HauptActivity)
                    .fehlendeBerechtigungen(Gesundheit.BERECHTIGUNGEN)

            gesundheit.removeAllViews()

            // DIESE KARTE STEHT OBEN, nicht unten. Ohne Lese-Erlaubnis
            // antwortet die Akte nicht mit Nein, sondern gar nicht - die
            // Felder blieben leer und saehen aus wie ein Fehler der App.
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

            gesundheit.addView(GesundheitTab.baue(this@HauptActivity, stand))

            // Die Zahlen sind eben gelesen; das Widget soll nicht
            // aelteres zeigen als der Schirm daneben.
            GesundheitWidget.stosseAn(this@HauptActivity)
        }
    }

    private fun auffrischenTechnik() {
        zustand.removeAllViews()

        val k = karte()
        val verlauf = Verlauf(this)
        val letzte = verlauf.letzteMeldung()
        k.addView(fliesstext(
            getString(R.string.dienst_hinweis) + "\n\n" +
                if (letzte.isEmpty()) getString(R.string.nichts_bisher)
                else getString(R.string.zuletzt, stempel(verlauf.letzteMeldungAm())) +
                    "\n" + letzte
        ))
        zustand.addView(k)

        // OSMAND STEHT HIER, weil man es sonst nirgends sieht. Der erste
        // Anlauf scheiterte daran, dass OsmAnd im Manifest nicht unter
        // <queries> stand und damit unsichtbar war - nichts stuerzte ab,
        // nichts warnte, und auf der Uhr kam einfach nichts an.
        val ko = karte()
        val lage = OsmandNavigation.lage
        ko.addView(schild(lage.startsWith("verbunden"), "OsmAnd: $lage"))
        if (!lage.startsWith("verbunden")) {
            ko.addView(zart(
                if (lage.startsWith("in OsmAnd freischalten"))
                    "OsmAnd lässt fremde Apps erst nach einem Schalter zu. " +
                        "Kiesel-Helper steht dort schon in der Liste — der " +
                        "erste Verbindungsversuch hat ihn eingetragen, nur " +
                        "ausgeschaltet. Nach dem Umlegen hierher " +
                        "zurückkehren, das genügt."
                else
                    "Ohne Verbindung zu OsmAnd bleibt Kieselstrasse auf der " +
                        "Uhr leer. OsmAnd muss installiert sein; die " +
                        "Verbindung entsteht, sobald dieser Dienst läuft."
            ))
            ko.addView(knopfHaupt("OsmAnd öffnen", breit = true) {
                val start = packageManager.getLaunchIntentForPackage("net.osmand.plus")
                    ?: packageManager.getLaunchIntentForPackage("net.osmand")
                if (start != null) startActivity(start) else melde("OsmAnd nicht gefunden")
            })
        }
        zustand.addView(ko)

        lifecycleScope.launch {
            val kk = karte()
            when (HealthConnectClient.getSdkStatus(this@HauptActivity)) {
                HealthConnectClient.SDK_UNAVAILABLE -> {
                    kk.addView(schild(false, getString(R.string.hc_fehlt)))
                    kk.addView(zart(
                        "Ohne Health Connect lässt sich nichts eintragen. Die " +
                            "Navigation zur Uhr geht trotzdem."
                    ))
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    kk.addView(schild(false, getString(R.string.hc_update)))
                else -> {
                    val fehlt = Akte(this@HauptActivity)
                        .fehlendeBerechtigungen(Aufgaben.BERECHTIGUNGEN)
                    if (fehlt.isEmpty()) {
                        kk.addView(schild(true, "Schreib-Erlaubnis erteilt"))
                    } else {
                        kk.addView(schild(false, "Schreib-Erlaubnis fehlt"))
                        kk.addView(zart(
                            "Ohne sie kommt eine Messung an und verschwindet " +
                                "still — das fällt erst auf, wenn man sie sucht."
                        ))
                        kk.addView(knopfHaupt("Erlaubnis erteilen", breit = true) {
                            erlaubnisStarter?.launch(fehlt)
                                ?: melde("Noch nicht bereit")
                        })
                    }
                }
            }
            zustand.addView(kk)

            // WAS IN DER AKTE STEHT. Ohne diese Liste raet man bei einem
            // leeren Feld: fehlt die Erlaubnis, fehlt die Satzart, oder
            // schreibt schlicht niemand?
            val befunde = Gesundheit(this@HauptActivity).pruefe()
            if (befunde.isNotEmpty()) {
                val kb = karte()
                kb.addView(kartentitel("Was in der Akte steht (48 h)"))
                befunde.forEach { b ->
                    kb.addView(zart(
                        b.name + ": " +
                            (if (b.anzahl == 0) "nichts" else b.anzahl.toString() + " Sätze") +
                            (if (b.quellen.isEmpty()) "" else " — " + b.quellen.joinToString(", "))
                    ))
                }
                kb.addView(zart(
                    "Ein leeres Feld auf dem Gesundheits-Schirm hat hier seine " +
                        "Antwort: steht nichts in der Akte, schreibt es niemand."
                ))
                zustand.addView(kb)
            }
        }
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun stempel(epochSekunden: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(epochSekunden * 1000))
}
