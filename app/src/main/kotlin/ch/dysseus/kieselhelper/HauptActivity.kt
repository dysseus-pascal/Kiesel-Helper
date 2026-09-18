package ch.dysseus.kieselhelper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
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
 * Der einzige Bildschirm: was die App tut und ob sie es darf.
 *
 * ER ZEIGT DREI FESTE AUFGABEN UND FRAGT NACH NICHTS. Vorher stand hier eine
 * Liste eingebundener Beschreibungen mit einem Knopf zum Nachladen - die App
 * konnte Dinge tun, die ihr niemand einprogrammiert hatte. Das war richtig
 * gedacht fuer eine App, die viele benutzen und die neue Uhr-Apps kennenlernen
 * soll, ohne neu gebaut zu werden. Diese benutzt einer, und fuer ihn sind es
 * drei Aufgaben; die stehen jetzt im Code statt in einer Datei aus dem Netz.
 *
 * Was bleibt, ist die Frage nach der Erlaubnis. Die kann kein Code sich selbst
 * geben.
 */
class HauptActivity : ComponentActivity() {

    private lateinit var wurzel: LinearLayout
    private lateinit var zustand: LinearLayout
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
    }

    override fun onResume() {
        super.onResume()
        auffrischen()
    }

    private fun baueAnsicht(): ScrollView {
        wurzel = spalte()
        zustand = spalte()

        wurzel.addView(kopf(getString(R.string.app_name)))
        wurzel.addView(fliesstext(
            "Nimmt entgegen, was die Uhr meldet, und holt bei OsmAnd, was für " +
                "die Navigation auf die Uhr gehört."
        ))
        wurzel.luft(8f)
        wurzel.addView(abschnitt("ZUSTAND"))
        wurzel.addView(zustand)

        wurzel.luft(8f)
        wurzel.addView(abschnitt("WAS SIE TUT"))
        wurzel.addView(aufgabenKarte(
            "Drinktervall → Gesundheitsakte",
            "Jedes getrunkene Glas wird als Wassermenge eingetragen, mit dem " +
                "Zeitpunkt von der Uhr. Dasselbe Glas nur einmal."
        ))
        wurzel.addView(aufgabenKarte(
            "Herzintervall → Gesundheitsakte",
            "Die nächtliche RMSSD-Messung wird als Herzratenvariabilität " +
                "eingetragen."
        ))
        wurzel.addView(aufgabenKarte(
            "OsmAnd → Kieselstrasse",
            "Abbiegeart, Entfernung, Strasse und Ankunftszeit gehen an die Uhr " +
                "— aus OsmAnds eigener Schnittstelle, nicht aus seiner " +
                "Benachrichtigung."
        ))

        wurzel.luft(12f)
        wurzel.addView(knopfHaupt("Verlauf ansehen", breit = true) {
            startActivity(Intent(this, VerlaufActivity::class.java))
        })
        wurzel.luft(12f)

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
                "Ohne Verbindung zu OsmAnd bleibt Kieselstrasse auf der Uhr " +
                    "leer. OsmAnd muss installiert sein; die Verbindung " +
                    "entsteht, sobald dieser Dienst läuft."
            ))
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
                        kk.addView(schild(true, "Erlaubnis erteilt"))
                    } else {
                        kk.addView(schild(false, "Erlaubnis fehlt"))
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
        }
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun stempel(epochSekunden: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(epochSekunden * 1000))
}
