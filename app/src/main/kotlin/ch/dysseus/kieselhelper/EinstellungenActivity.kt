package ch.dysseus.kieselhelper

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
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
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Die Technik, aus dem Weg geraeumt.
 *
 * SIE WAR EIN REITER, und das war eine Fehleinschaetzung. Ein Reiter ist eine
 * Behauptung darueber, wie oft man etwas anschaut - und Zustand, Erlaubnisse
 * und Aufgabenliste schaut man an, wenn etwas nicht geht. Das ist zweimal im
 * Jahr. Dafuer stand der dritte Reiter jeden Tag daneben und nahm den beiden
 * anderen Platz weg.
 *
 * Hinter dem Zahnrad ist es nicht versteckt, sondern einsortiert: dort sucht
 * man es, wenn man es sucht.
 */
class EinstellungenActivity : ComponentActivity() {

    private lateinit var zustand: LinearLayout
    private lateinit var schlafwert: TextView
    private lateinit var grenzwert: TextView
    private lateinit var linkfeld: android.widget.EditText
    private lateinit var linkbefund: TextView
    private var erlaubnisStarter: ActivityResultLauncher<Set<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vertrag: ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()
        erlaubnisStarter = registerForActivityResult(vertrag) { auffrischen() }
        setContentView(baueAnsicht())
    }

    override fun onResume() {
        super.onResume()
        // Nochmal bei OsmAnd anklopfen. Wer dort eben den Schalter umgelegt
        // hat, kommt als Naechstes hierher und will sehen, dass es wirkt.
        OsmandNavigation.versucheErneut(this)
        auffrischen()
    }

    private fun baueAnsicht(): ScrollView {
        val wurzel = spalte().apply {
            setPadding(dp(16f), dp(24f), dp(16f), dp(28f))
        }
        zustand = spalte()

        wurzel.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        wurzel.luft(14f)
        wurzel.addView(kopf("Einstellungen"))
        wurzel.luft(6f)
        wurzel.addView(fliesstext(
            "Nimmt entgegen, was die Uhr meldet, und holt bei OsmAnd, was für " +
                "die Navigation auf die Uhr gehört."
        ))

        wurzel.luft(8f)
        wurzel.addView(abschnitt("SCHLAF"))
        wurzel.addView(schlafkarte())

        wurzel.luft(8f)
        wurzel.addView(abschnitt("DER TAG"))
        wurzel.addView(grenzkarte())

        wurzel.luft(8f)
        wurzel.addView(abschnitt("KARTENLINKS"))
        wurzel.addView(linkkarte())

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
            "SupCycle → Ernährung",
            "Was heute ansteht, was davon abgehakt ist, und die Namen dazu. " +
                "Jedes genommene Präparat geht als Ernährungssatz in die Akte " +
                "— ohne Mengen, denn SupCycle kennt Namen und Zyklen, keine " +
                "Milligramm."
        ))
        wurzel.addView(aufgabenKarte(
            "Kieselsport → Gesundheitsakte",
            "Ein beendetes Training wird als Trainingssitzung eingetragen — " +
                "nur die Sitzung, nicht die Zahlen darin. Schritte, Distanz " +
                "und Kalorien trägt die Pebble-App längst selbst ein; sie hier " +
                "zu wiederholen zählte denselben Kilometer zweimal."
        ))
        wurzel.addView(aufgabenKarte(
            "Koffein → Gesundheitsakte",
            "Was du in der App antippst, wird als Ernährungssatz mit " +
                "Koffeinmenge eingetragen. Die Akte ist damit auch hier die " +
                "Quelle: gelesen wird, was dort steht, nicht die eigene Zählung."
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

        val roller = ScrollView(this)
        roller.addView(wurzel)
        roller.randUmSystemleisten()
        return roller
    }

    /**
     * Der persoenliche Idealwert fuer den Schlaf.
     *
     * KEIN EINGABEFELD, sondern zwei Knoepfe in Viertelstunden. Eine Tastatur
     * fuer eine Zahl zwischen vier und zwoelf Stunden waere der umstaendlichere
     * Weg, und sie liesse Eingaben zu, die niemand meint.
     *
     * Der Wert ist die EINZIGE Einstellung dieser Art. Schritte und Bewegung
     * bleiben Konstanten: zehntausend und dreissig Minuten sind Hausnummern,
     * an denen sich ohnehin niemand misst. Acht Stunden Schlaf dagegen sind
     * ein Mittelwert ueber Menschen, keine Vorgabe fuer einen.
     */
    private fun schlafkarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel("Mein Idealwert"))
        k.addView(zart(
            "Er steht als farbige Linie im Schlafbild und im Wochenprofil — " +
                "und der Trend zählt, in wie vielen Nächten du ihn erreicht hast."
        ))

        schlafwert = TextView(this).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(farbe(R.color.schrift))
            setPadding(0, dp(10f), 0, dp(6f))
        }
        k.addView(schlafwert)

        val zeile = reihe()
        zeile.addView(knopfLeise("− 15 min") { schiebe(-Einstellungen.SCHLAF_SCHRITT) })
        zeile.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10f), dp(1f))
        })
        zeile.addView(knopfLeise("+ 15 min") { schiebe(Einstellungen.SCHLAF_SCHRITT) })
        k.addView(zeile)

        zeigeSchlafziel()
        return k
    }

    /**
     * Wann ein Tag anfaengt.
     *
     * WER UM ZWEI UHR NOCH WACH IST, hat seine Schritte am Vortag gemacht -
     * der Kalender sieht das anders. Mit einer Grenze um sechs zaehlt die
     * Nacht zu dem Tag, an dem sie begann, und das Widget zeigt um fuenf Uhr
     * morgens nicht einen frisch begonnenen, leeren Tag.
     *
     * Die Grenze gilt fuer ALLES, was "heute" heisst: Tageswerte,
     * Wochenbilder, der Trend, die Supplementliste. Nur der Schlaf hat sein
     * eigenes Fenster - eine Nacht faengt um achtzehn Uhr an, egal wo der Tag
     * beginnt.
     */
    private fun grenzkarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel("Ein Tag beginnt um"))
        k.addView(zart(
            "Gilt für alles, was »heute« heisst. Der Schlaf hat sein eigenes " +
                "Fenster: eine Nacht beginnt um 18 Uhr, egal wo der Tag beginnt."
        ))

        grenzwert = TextView(this).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(farbe(R.color.schrift))
            setPadding(0, dp(10f), 0, dp(6f))
        }
        k.addView(grenzwert)

        val zeile = reihe()
        zeile.addView(knopfLeise("− 1 h") { schiebeGrenze(-1) })
        zeile.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10f), dp(1f))
        })
        zeile.addView(knopfLeise("+ 1 h") { schiebeGrenze(1) })
        k.addView(zeile)

        zeigeGrenze()
        return k
    }

    private fun schiebeGrenze(stunden: Int) {
        Einstellungen.setzeTagesgrenze(
            this, Einstellungen.tagesgrenze(this) + stunden
        )
        zeigeGrenze()
        // Das Widget rechnet mit derselben Grenze - es soll nicht bis zur
        // naechsten Minute einen anderen Tag zeigen als die App.
        GesundheitWidget.stosseAn(this)
    }

    private fun zeigeGrenze() {
        val stunde = Einstellungen.tagesgrenze(this)
        grenzwert.text = String.format("%02d:00", stunde) +
            if (stunde == 0) " (Mitternacht)" else ""
    }

    private fun schiebe(minuten: Int) {
        Einstellungen.setzeSchlafziel(
            this, Einstellungen.schlafziel(this) + minuten
        )
        zeigeSchlafziel()
    }

    private fun zeigeSchlafziel() {
        schlafwert.text = Zahlen.dauer(Einstellungen.schlafziel(this).toDouble())
    }

    /**
     * Ein Kartenlink zum Ausprobieren.
     *
     * WARUM DAS HIER STEHT: an der Umleitung haengen drei Dinge hintereinander
     * - Android muss den Link ueberhaupt hierher geben, die Zerlegung muss ihn
     * verstehen, und OsmAnd muss ihn annehmen. Geht es nicht, weiss man nicht,
     * welches der drei schuld ist.
     *
     * Dieser Prüfstand ueberspringt das erste. Was hier klappt und draussen
     * nicht, ist eine Sache der Link-Freigabe in den Android-Einstellungen -
     * und was hier schon scheitert, liegt an uns oder an OsmAnd.
     */
    private fun linkkarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel("Kartenlink ausprobieren"))
        k.addView(zart(
            "Link einfügen und prüfen. Überspringt Androids Link-Freigabe — " +
                "was hier klappt und draussen nicht, liegt an ihr."
        ))

        linkfeld = eingabefeld("https://maps.app.goo.gl/…")
        k.addView(linkfeld)

        linkbefund = zart("")
        k.addView(linkbefund)

        val zeile = reihe()
        zeile.addView(knopfLeise("Einfügen") {
            val ablage = getSystemService(android.content.ClipboardManager::class.java)
            val text = ablage?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (text.isNullOrBlank()) melde("Zwischenablage ist leer")
            else linkfeld.setText(text.trim())
        })
        zeile.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8f), dp(1f))
        })
        zeile.addView(knopfLeise("Nur prüfen") { pruefeLink(false) })
        k.addView(zeile)
        k.addView(knopfHaupt("An OsmAnd geben", breit = true) { pruefeLink(true) })

        k.addView(strich())
        k.addView(kartentitel("Was ein Link auslöst"))
        val wahl = reihe()
        listOf("Ort zeigen" to false, "Führung starten" to true).forEach { (name, fuehrt) ->
            val gewaehlt = Einstellungen.kartenlinkFuehrt(this) == fuehrt
            wahl.addView(TextView(this).apply {
                text = name
                gravity = android.view.Gravity.CENTER
                maxLines = 1
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(8f), dp(11f), dp(8f), dp(11f))
                setTextColor(farbe(
                    if (gewaehlt) R.color.akzent_schrift else R.color.schrift_zart
                ))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(farbe(if (gewaehlt) R.color.akzent else R.color.karte))
                    cornerRadius = dp(10f).toFloat()
                    if (!gewaehlt) setStroke(dp(1f), farbe(R.color.linie))
                }
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ).apply { marginEnd = dp(6f) }
                setOnClickListener {
                    Einstellungen.setzeKartenlinkFuehrt(this@EinstellungenActivity, fuehrt)
                    setContentView(baueAnsicht())
                }
            })
        }
        k.addView(wahl)
        k.addView(zart(
            "»Ort zeigen« setzt den Punkt auf die Karte und überlässt dir den " +
                "Start. Eine Führung, die von selbst anspringt, entscheidet " +
                "sonst mit, ob jetzt überhaupt gefahren wird."
        ))
        return k
    }


    private fun pruefeLink(weitergeben: Boolean) {
        val roh = linkfeld.text?.toString()?.trim().orEmpty()
        if (roh.isEmpty()) {
            linkbefund.text = "Kein Link eingefügt."
            return
        }
        linkbefund.text = "Wird gelesen …"
        lifecycleScope.launch {
            val voll = withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (Kartenlink.istKurzlink(roh)) Kartenlink.folge(roh) else roh
            }
            val ziel = Kartenlink.zerlege(voll)
            if (ziel == null || !ziel.brauchbar) {
                linkbefund.text = "Kein Ziel erkannt.\n\nAufgelöst: " + voll.take(160)
                return@launch
            }
            val gefunden = buildString {
                append("Erkannt: ")
                if (ziel.lat != null && ziel.lon != null) {
                    append(Zahlen.zwei(ziel.lat) + ", " + Zahlen.zwei(ziel.lon))
                }
                if (!ziel.text.isNullOrBlank()) {
                    if (ziel.lat != null) append(" — ")
                    append("»" + ziel.text + "«")
                }
                if (voll != roh) append("\nAufgelöst: " + voll.take(120))
            }
            if (!weitergeben) {
                linkbefund.text = gefunden
                return@launch
            }
            linkbefund.text = gefunden + "\n\nWird übergeben …"
            val aus = Kartenlink.uebergib(this@EinstellungenActivity, ziel)
            linkbefund.text = gefunden + "\n\n" + if (aus.geschafft) {
                "Übergeben — " + aus.beschreibung
            } else {
                "OsmAnd hat abgelehnt (" + aus.weg + "). Meist fehlt dort " +
                    "die Freigabe unter Menü → Plugins."
            }
        }
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
            when (HealthConnectClient.getSdkStatus(this@EinstellungenActivity)) {
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
                    val fehlt = Akte(this@EinstellungenActivity)
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
            val befunde = Gesundheit(this@EinstellungenActivity).pruefe()
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
