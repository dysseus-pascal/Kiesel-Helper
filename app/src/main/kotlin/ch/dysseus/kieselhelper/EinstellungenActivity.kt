package ch.dysseus.kieselhelper

import android.content.Intent
import android.os.Bundle
import android.view.View
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
class EinstellungenActivity : KieselActivity() {

    private lateinit var zustand: LinearLayout
    private lateinit var schlafwert: TextView
    private lateinit var grenzwert: TextView
    private lateinit var linkfeld: android.widget.EditText
    private lateinit var linkbefund: TextView
    private var erlaubnisStarter: ActivityResultLauncher<Set<String>>? = null
    private var ortStarter: ActivityResultLauncher<Array<String>>? = null
    private var ordnerStarter: ActivityResultLauncher<android.net.Uri?>? = null
    private var wartetAufEinstellungen = false
    /** Das Nachladen wartet auf die Antwort zur Erlaubnis fuer aeltere Daten. */
    private var ladeNachErlaubnis = false
    /** Einmal je Schirm fragen; wer ablehnt, bekommt trotzdem den Monat. */
    private var historieGefragt = false

    /** Das Seitenmenue - und welche Seite gerade offen ist. */
    private var lade: androidx.drawerlayout.widget.DrawerLayout? = null
    private var seite: String = "app"
    private val menueZu = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            lade?.closeDrawer(android.view.Gravity.START)
        }
    }

    /**
     * Die Seiten der Einstellungen: eine je Dienst auf der Uhr, und eine fuer
     * Kiesel-Helper selbst.
     *
     * NACH DIENST, NICHT NACH ART DER EINSTELLUNG. Wer etwas an Drinktervall
     * aendern will, sucht bei Drinktervall - nicht unter "Ziele" oder
     * "Erlaubnisse". Was nur die App angeht (Erscheinungsbild, Sicherung,
     * Zustand), steht auf ihrer eigenen Seite.
     */
    private data class Seite(val schluessel: String, val name: String, val unter: String, val farbe: Int)

    private val UHR_SEITEN = listOf(
        Seite("drinktervall", "Drinktervall", "Wasser", R.color.wasser),
        Seite("herzintervall", "Herzintervall", "HRV, Schlaf, Ruhepuls", R.color.phase_rem),
        Seite("supcycle", "SupCycle", "Präparate", R.color.akzent_ernaehrung),
        Seite("kieselsport", "Kieselsport", "Training, Strecke, Puls", R.color.akzent_training),
        Seite("kieselstrasse", "Kieselstrasse", "Navigation, Kartenlinks", R.color.sport_wandern),
    )
    private val APP_SEITE = Seite("app", "Kiesel-Helper", "Darstellung, Sicherung, Zustand", R.color.akzent)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AUSDRUECKLICH ZURUECK: sonst traegt dieser Schirm den Ton des
        // Reiters, aus dem er geoeffnet wurde.
        Ton.setze(Ton.GESUNDHEIT)
        val vertrag: ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()
        erlaubnisStarter = registerForActivityResult(vertrag) {
            auffrischen()
            if (ladeNachErlaubnis) {
                ladeNachErlaubnis = false
                lifecycleScope.launch { ladeNach() }
            }
        }
        ortStarter = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { setContentView(baueAnsicht()) }
        ordnerStarter = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            // DAUERHAFT, sonst gilt die Erlaubnis nur bis zum Neustart - und
            // die taegliche Sicherung liefe danach ins Leere.
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Einstellungen.setzeSicherungOrdner(this, uri.toString())
            lifecycleScope.launch {
                melde(Sichern.pruefe(this@EinstellungenActivity))
                setContentView(baueAnsicht())
            }
        }
        seite = Einstellungen.einstellungenSeite(this)
        onBackPressedDispatcher.addCallback(this, menueZu)
        setContentView(baueAnsicht())
    }

    override fun onResume() {
        super.onResume()
        // ZURUECK AUS DEN SYSTEMEINSTELLUNGEN: dort kann sich die Standort-
        // erlaubnis geaendert haben, und der Schirm zeigte sonst den alten Stand.
        if (wartetAufEinstellungen) {
            wartetAufEinstellungen = false
            setContentView(baueAnsicht())
        }
        // Nochmal bei OsmAnd anklopfen. Wer dort eben den Schalter umgelegt
        // hat, kommt als Naechstes hierher und will sehen, dass es wirkt.
        OsmandNavigation.versucheErneut(this)
        auffrischen()
    }

    private fun alleSeiten() = UHR_SEITEN + APP_SEITE
    private fun aktuelleSeite() = alleSeiten().firstOrNull { it.schluessel == seite } ?: APP_SEITE

    /**
     * Der ganze Schirm: das Seitenmenue und die gewaehlte Seite.
     *
     * EIN SEITENMENUE STATT EINER LANGEN LISTE. Die Einstellungen waren eine
     * Rolle von fuenfzehn Karten, und wer die Kartenlinks suchte, scrollte an
     * Schlaf, Sicherung und Pulszonen vorbei. Jetzt hat jeder Dienst auf der
     * Uhr seine Seite, und die App ihre.
     */
    private fun baueAnsicht(): View {
        zustand = spalte()
        val drawer = androidx.drawerlayout.widget.DrawerLayout(this)
        drawer.setScrimColor(0x66000000)
        drawer.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) { menueZu.isEnabled = true }
            override fun onDrawerClosed(drawerView: View) { menueZu.isEnabled = false }
        })
        lade = drawer
        menueZu.isEnabled = false

        // --- Der Inhalt ---
        val wurzel = spalte().apply { setPadding(dp(16f), dp(12f), dp(16f), dp(28f)) }
        wurzel.addView(reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(HamburgerView(this@EinstellungenActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginEnd = dp(8f) }
                contentDescription = "Menü"
                setOnClickListener { drawer.openDrawer(android.view.Gravity.START) }
            })
            addView(spalte().apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(zart("Einstellungen"))
                addView(kopf(aktuelleSeite().name))
            })
        })
        wurzel.luft(6f)
        when (seite) {
            "drinktervall" -> seiteDrinktervall(wurzel)
            "herzintervall" -> seiteHerzintervall(wurzel)
            "supcycle" -> seiteSupCycle(wurzel)
            "kieselsport" -> seiteKieselsport(wurzel)
            "kieselstrasse" -> seiteKieselstrasse(wurzel)
            else -> seiteApp(wurzel)
        }
        val roller = ScrollView(this).apply {
            layoutParams = androidx.drawerlayout.widget.DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(wurzel)
        }
        roller.randUmSystemleisten()
        drawer.addView(roller)

        // --- Das Menue ---
        drawer.addView(menue(drawer))
        return drawer
    }

    private fun menue(drawer: androidx.drawerlayout.widget.DrawerLayout): View {
        val liste = spalte().apply { setPadding(dp(12f), dp(20f), dp(12f), dp(20f)) }
        liste.addView(kopf("Einstellungen").apply { setPadding(dp(12f), 0, 0, dp(12f)) })
        liste.addView(abschnitt("AUF DER UHR").apply { setPadding(dp(12f), dp(8f), 0, dp(6f)) })
        UHR_SEITEN.forEach { liste.addView(menuepunkt(it, drawer)) }
        liste.addView(strich().apply {
            (layoutParams as? LinearLayout.LayoutParams)?.setMargins(dp(12f), dp(10f), dp(12f), dp(10f))
        })
        liste.addView(menuepunkt(APP_SEITE, drawer))

        val roller = ScrollView(this).apply {
            setBackgroundColor(farbe(R.color.karte))
            layoutParams = androidx.drawerlayout.widget.DrawerLayout.LayoutParams(
                dp(296f), ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.START
            )
            addView(liste)
            elevation = dp(6f).toFloat()
        }
        roller.randUmSystemleisten()
        return roller
    }

    private fun menuepunkt(s: Seite, drawer: androidx.drawerlayout.widget.DrawerLayout): View {
        val gewaehlt = s.schluessel == seite
        val ton = farbe(s.farbe)
        return reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(farbe(R.color.linie)),
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12f).toFloat()
                    setColor(if (gewaehlt) (akzentfarbe() and 0x00FFFFFF) or 0x26000000 else 0)
                },
                null,
            )
            isClickable = true
            setOnClickListener {
                seite = s.schluessel
                Einstellungen.setzeEinstellungenSeite(this@EinstellungenActivity, seite)
                drawer.closeDrawer(android.view.Gravity.START)
                setContentView(baueAnsicht())
                if (seite == "app") auffrischen()
            }
            addView(View(this@EinstellungenActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(12f), dp(12f)).apply { marginEnd = dp(14f) }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ton)
                }
            })
            addView(spalte().apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(fliesstext(s.name).apply {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    if (gewaehlt) setTextColor(akzentfarbe())
                })
                addView(zart(s.unter))
            })
        }.also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(2f)
        }
    }

    // --- Die Seiten ---------------------------------------------------------

    private fun seiteApp(w: LinearLayout) {
        w.addView(fliesstext(
            "Nimmt entgegen, was die Uhr meldet, und holt bei OsmAnd, was für " +
                "die Navigation auf die Uhr gehört."
        ))
        w.luft(8f)
        w.addView(abschnitt("ERSCHEINUNGSBILD"))
        w.addView(darstellungskarte())
        w.luft(8f)
        w.addView(abschnitt("DER TAG"))
        w.addView(grenzkarte())
        w.luft(8f)
        w.addView(abschnitt("SICHERUNG"))
        w.addView(sicherungskarte())
        w.luft(8f)
        w.addView(abschnitt("FRÜHERE DATEN"))
        w.addView(historienkarte())
        w.luft(8f)
        w.addView(abschnitt("ZUSTAND"))
        w.addView(zustand)
        w.luft(8f)
        w.addView(abschnitt("EIGENE EINTRÄGE"))
        w.addView(aufgabenKarte(
            "Koffein → Gesundheitsakte",
            "Was du in der App antippst, wird als Ernährungssatz mit " +
                "Koffeinmenge eingetragen. Die Akte ist damit auch hier die " +
                "Quelle: gelesen wird, was dort steht, nicht die eigene Zählung."
        ))
        w.luft(12f)
        w.addView(knopfHaupt("Verlauf ansehen", breit = true) {
            startActivity(Intent(this, VerlaufActivity::class.java))
        })
    }

    private fun seiteDrinktervall(w: LinearLayout) {
        w.addView(aufgabenKarte(
            "Drinktervall → Gesundheitsakte",
            "Jedes getrunkene Glas wird als Wassermenge eingetragen, mit dem " +
                "Zeitpunkt von der Uhr. Dasselbe Glas nur einmal."
        ))
        w.luft(8f)
        w.addView(abschnitt("VON DER UHR"))
        w.addView(karte().apply {
            addView(kartentitel("Tagesziel"))
            val glaeser = Einstellungen.wasserGlaeser(this@EinstellungenActivity)
            val ml = Einstellungen.glasMl(this@EinstellungenActivity)
            addView(TextView(this@EinstellungenActivity).apply {
                text = "$glaeser × $ml ml"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(farbe(R.color.schrift))
                setPadding(0, dp(10f), 0, dp(6f))
            })
            addView(zart(
                "Eingestellt wird es in Drinktervall — dort das Soll, und mit " +
                    "»Ziel+« mehr für heute. Jede Meldung der Uhr bringt das " +
                    "heutige Ziel mit, jedes Glas seine Grösse. Hier steht nur, " +
                    "was zuletzt ankam."
            ))
        })
    }

    private fun seiteHerzintervall(w: LinearLayout) {
        w.addView(aufgabenKarte(
            "Herzintervall → Gesundheitsakte",
            "Die nächtliche RMSSD-Messung wird als Herzratenvariabilität " +
                "eingetragen. Mit ihr kommen die letzte Nacht — Schlafbeginn " +
                "und -ende — und der mittlere Nachtpuls als Ruhepuls."
        ))
        w.luft(8f)
        w.addView(abschnitt("SCHLAF"))
        w.addView(schlafkarte())
    }

    private fun seiteSupCycle(w: LinearLayout) {
        w.addView(aufgabenKarte(
            "SupCycle → Ernährung",
            "Was heute ansteht, was davon abgehakt ist, und die Namen dazu. " +
                "Jedes genommene Präparat geht als Ernährungssatz in die Akte " +
                "— ohne Mengen, denn SupCycle kennt Namen und Zyklen, keine " +
                "Milligramm."
        ))
        w.addView(zart(
            "Präparate, Uhrzeiten und Zyklen werden in den Einstellungen von " +
                "SupCycle in der Pebble-App eingetragen, nicht hier."
        ).apply { setPadding(dp(4f), dp(4f), dp(4f), 0) })
    }

    private fun seiteKieselsport(w: LinearLayout) {
        w.addView(aufgabenKarte(
            "Kieselsport → Gesundheitsakte",
            "Ein beendetes Training wird als Trainingssitzung eingetragen, mit " +
                "Sätzen oder Bahnen, der Pulskurve und — bei Laufen, Bike, " +
                "Wandern — der Strecke vom Telefon. Schritte, Distanz und " +
                "Kalorien trägt die Pebble-App selbst ein."
        ))
        w.luft(8f)
        w.addView(abschnitt("STRECKE"))
        w.addView(spurkarte())
        w.luft(8f)
        w.addView(abschnitt("PULS"))
        w.addView(pulskarte())
    }

    private fun seiteKieselstrasse(w: LinearLayout) {
        w.addView(aufgabenKarte(
            "OsmAnd → Kieselstrasse",
            "Abbiegeart, Entfernung, Strasse und Ankunftszeit gehen an die Uhr " +
                "— aus OsmAnds eigener Schnittstelle, nicht aus seiner " +
                "Benachrichtigung."
        ))
        w.addView(osmandKarte())
        w.luft(8f)
        w.addView(abschnitt("KARTENLINKS"))
        w.addView(linkkarte())
    }

    /**
     * Hell, dunkel oder wie das System - und Material You.
     *
     * DREI KNOEPFE STATT EINES SCHALTERS: "wie das System" ist eine eigene
     * Wahl, kein Zwischending. Der gewaehlte ist gefuellt.
     */
    private fun darstellungskarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel("Hell oder dunkel"))
        val modus = Einstellungen.themaModus(this)
        val reihe = reihe()
        listOf(Thema.SYSTEM to "System", Thema.HELL to "Hell", Thema.DUNKEL to "Dunkel").forEach { (m, name) ->
            val knopf = if (m == modus) knopfHaupt(name) {} else knopfLeise(name) {
                Einstellungen.setzeThemaModus(this, m)
                GesundheitWidget.stosseAn(this)
                recreate()
            }
            reihe.addView(knopf.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if (m != Thema.DUNKEL) dp(8f) else 0
                }
            })
        }
        k.addView(reihe.apply { setPadding(0, dp(10f), 0, dp(4f)) })
        k.addView(zart(
            "»System« folgt dem Nachtschalter des Telefons. Das Widget folgt " +
                "immer dem System — der Startbildschirm gehört nicht dieser App."
        ))

        k.luft(14f)
        k.addView(kartentitel("Material You"))
        if (Thema.materialYouMoeglich()) {
            val an = Einstellungen.materialYou(this)
            k.addView(zart(
                "Grund, Karten, Schrift und die Farben der drei Reiter kommen aus " +
                    "dem Hintergrundbild. Sportarten, Schlafphasen, Pulszonen, " +
                    "Wasser und Koffein behalten ihre Farben — sie tragen Bedeutung."
            ))
            k.addView(knopfLeise(if (an) "Material You: an" else "Material You: aus") {
                Einstellungen.setzeMaterialYou(this, !an)
                recreate()
            }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8f) })
        } else {
            k.addView(zart("Material You gibt es ab Android 12."))
        }
        return k
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
    /**
     * Die Standortberechtigung - fuer die Strecke, und nur dafuer.
     *
     * SIE STEHT HIER UND NICHT BEIM ERSTEN START. Wer die App oeffnet, um
     * seinen Schlaf zu sehen, soll nicht nach seinem Standort gefragt werden;
     * gefragt wird, wer eine Strecke will. Ohne sie laeuft alles andere
     * weiter - das Training wird eingetragen, nur ohne Karte.
     */
    /**
     * Der Maximalpuls - derselbe wie auf der Uhr, damit die Zonen der
     * Auswertung die sind, die man beim Training gesehen hat.
     */
    private fun pulskarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel("Pulszonen"))
        k.addView(zart(
            "Die Zonen rechnen sich aus dem Maximalpuls: Zone 1 ab 50 %, Zone 5 ab " +
                "90 %. Trag denselben Wert ein wie in den Einstellungen von " +
                "Kieselsport — die Uhr schickt ihn nicht mit."
        ))
        val feld = feld("Maximalpuls", Einstellungen.maxpuls(this).toString()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        k.addView(feld)
        k.addView(knopfLeise("Speichern") {
            val wert = feld.text().toIntOrNull()
            if (wert == null || wert < 120 || wert > 230) {
                melde("Zwischen 120 und 230")
            } else {
                Einstellungen.setzeMaxpuls(this, wert)
                melde("Maximalpuls $wert gespeichert")
            }
        })
        return k
    }

    private fun spurkarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel("Strecke aufzeichnen"))

        val erlaubt = SpurDienst.darfOrten(this)
        val immer = SpurDienst.darfImmerOrten(this)
        k.addView(schild(
            erlaubt && immer,
            when {
                !erlaubt -> "Standort nicht erlaubt"
                !immer -> "Nur während der Nutzung — zu wenig"
                else -> "Standort immer erlaubt"
            }
        ))
        k.addView(zart(
            "Die Uhr hat kein GPS. Während eines Trainings zeichnet das Telefon " +
                "die Strecke auf — mit sichtbarer Meldung in der Leiste, und nur " +
                "zwischen Start und Stop. Ohne die Erlaubnis wird das Training " +
                "trotzdem eingetragen, nur ohne Karte."
        ))
        if (!erlaubt) {
            k.addView(knopfHaupt("Standort erlauben", breit = true) {
                ortStarter?.launch(arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ))
            })
        } else if (!immer) {
            // DAS IST DER HAEUFIGE FALL, und er sieht aus wie ein Fehler der
            // App: die Erlaubnis steht da, die Karte bleibt leer.
            k.addView(fliesstext(
                "»Nur während der Nutzung« genügt hier nicht. Das Training " +
                    "beginnt auf der Uhr, während das Telefon in der Tasche " +
                    "liegt und diese App zu ist — in diesem Zustand lässt " +
                    "Android keine Ortung zu, und die Strecke bliebe leer. " +
                    "Nötig ist »Immer erlauben«. Gemessen wird trotzdem nur " +
                    "zwischen Start und Stop eines Trainings."
            ))
            k.addView(knopfHaupt("Immer erlauben", breit = true) {
                oeffneAppEinstellungen()
            })
        }
        return k
    }

    /**
     * Zu den Systemeinstellungen dieser App.
     *
     * DAS HINTERGRUNDRECHT GIBT ES NICHT ALS DIALOG. Seit Android 11 zeigt das
     * System dafuer kein Fenster mehr; »Immer erlauben« steht nur in den
     * Einstellungen, und dorthin kann eine App nur den Weg zeigen. Sie soll
     * ihn wenigstens zeigen, statt den Menschen suchen zu lassen.
     */
    private fun oeffneAppEinstellungen() {
        wartetAufEinstellungen = true
        try {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null),
                )
            )
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this, "Einstellungen nicht erreichbar", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Die Vergangenheit aus Health Connect in die eigene Tabelle holen.
     *
     * NACH EINER NEUINSTALLATION IST DIE TABELLE LEER, die Akte aber nicht.
     * Von allein holt die App nur einen Monat; mit der Erlaubnis fuer
     * aeltere Daten ein Jahr. Die Erlaubnis wird erst hier erfragt und nicht
     * beim Start: sie ist eine eigene Frage, und wer sie nicht versteht,
     * lehnt am Start alles ab.
     */
    private fun historienkarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel("Aus Health Connect nachladen"))
        k.addView(zartMitHinweis(
            "Füllt den Trend aus dem, was Health Connect gespeichert hat",
            "Ohne weitere Erlaubnis gibt Health Connect nur die dreissig Tage " +
                "vor der ersten Erlaubnis heraus — nach einer Neuinstallation " +
                "also einen Monat. Mit der Erlaubnis für ältere Daten holt die " +
                "App ein Jahr. Was nur in der App steht — die Einschätzung des " +
                "Tages, die Präparate —, bleibt unberührt. Nicht jedes Telefon kennt " +
                "diese Erlaubnis; dann bleibt es beim Monat."
        ))
        k.addView(knopfHaupt("Nachladen", breit = true) {
            lifecycleScope.launch {
                val erteilt = runCatching {
                    Akte(this@EinstellungenActivity).bereit()
                        ?.permissionController?.getGrantedPermissions()
                }.getOrNull().orEmpty()
                if (Gesundheit.HISTORIE !in erteilt && !historieGefragt) {
                    // Erst fragen, dann holen - das Holen haengt am Ergebnis.
                    historieGefragt = true
                    ladeNachErlaubnis = true
                    erlaubnisStarter?.launch(setOf(Gesundheit.HISTORIE))
                } else {
                    ladeNach()
                }
            }
        })
        return k
    }

    private suspend fun ladeNach() {
        melde("Hole aus Health Connect …")
        val erteilt = runCatching {
            Akte(this).bereit()?.permissionController?.getGrantedPermissions()
        }.getOrNull().orEmpty()
        val tage = Gesundheit(this).nachtragen()
        melde(
            if (tage == 0) "Nichts gefunden. Fehlt die Lese-Erlaubnis?"
            else "$tage Tage geholt" +
                if (Gesundheit.HISTORIE in erteilt) "."
                else " — ältere gibt Health Connect ohne die Erlaubnis nicht frei."
        )
    }

    /**
     * Die Sicherung in einen Ordner auf dem Telefon.
     *
     * WARUM ES SIE GIBT: die Gesundheitsakte hält rund dreissig Tage. Alles,
     * was diese App an Wochenprofilen und Zusammenhängen rechnet, steht danach
     * nur noch in ihrer eigenen Tabelle - und die liegt in den App-Daten eines
     * einzigen Telefons.
     *
     * EIN ORDNER, DEN EINE SYNC-APP ABGLEICHT. Kein eigener Cloud-Code, kein
     * WebDAV mehr: DAVx5, Nextcloud oder mailbox.org Drive tragen den Ordner
     * hinauf, mit ihrer Anmeldung und ihrer Fehlerbehandlung.
     */
    private fun sicherungskarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel("Sicherung"))

        val zuletzt = Einstellungen.sicherungZuletzt(this)
        k.addView(schild(
            zuletzt > 0,
            if (zuletzt > 0) "Zuletzt " + java.text.DateFormat
                .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(java.util.Date(zuletzt))
            else "Noch nie gesichert"
        ))
        k.addView(zartMitHinweis(
            "Tagestabelle und Strecken in einen Ordner auf dem Telefon",
            "Die Gesundheitsakte hält rund dreissig Tage. Alles, was diese App " +
                "an Wochenprofilen und Zusammenhängen rechnet, steht danach nur " +
                "noch in ihrer eigenen Tabelle — und die liegt in den App-Daten " +
                "eines einzigen Telefons. Gesichert wird als lesbares JSON: das " +
                "ist auch dann noch etwas wert, wenn es diese App nicht mehr " +
                "gibt. Die Strecken liegen daneben, eine Datei je Training, und " +
                "gehen nur einmal hinauf."
        ))

        // EIN ORDNER, DEN EINE SYNC-APP ABGLEICHT: er geht mit jedem Anbieter.
        // DAVx5, Nextcloud, mailbox.org Drive oder Syncthing tragen ihn hinauf,
        // mit ihrer eigenen Anmeldung.
        val ordnerUri = Einstellungen.sicherungOrdner(this)
        val ordnerName = if (ordnerUri.isNotBlank()) {
            val uri = android.net.Uri.parse(ordnerUri)
            if (OrdnerZiel.erlaubt(this, uri)) OrdnerZiel(this, uri).name else null
        } else null
        k.addView(schild(
            ordnerName != null,
            when {
                ordnerName != null -> "Ordner auf dem Telefon: $ordnerName"
                ordnerUri.isNotBlank() -> "Ordner auf dem Telefon nicht mehr erreichbar"
                else -> "Kein Ordner auf dem Telefon gewählt"
            }
        ))
        k.addView(zartMitHinweis(
            "Einen Ordner wählen, den eine Sync-App abgleicht",
            "Wähle im Dialog einen Ordner, den DAVx5, Nextcloud, mailbox.org " +
                "Drive oder Syncthing synchronisiert — oder einen Ordner in " +
                "»Dokumente«. Kiesel-Helper schreibt dorthin, die Sync-App trägt " +
                "es hinauf; Anmeldung und Eigenheiten des Servers sind deren " +
                "Sache. Ohne Sync-App bleibt es eine Kopie auf dem Telefon, die " +
                "man abholen kann. Gesichert wird als lesbares JSON."
        ))
        val ordnerReihe = reihe()
        ordnerReihe.addView(knopfHaupt(
            if (ordnerName != null) "Anderen Ordner wählen" else "Ordner auf dem Telefon wählen"
        ) {
            ordnerStarter?.launch(null)
        }.breitInReihe())
        if (ordnerUri.isNotBlank()) {
            ordnerReihe.addView(knopfLeise("Ordner entfernen") {
                Einstellungen.setzeSicherungOrdner(this, "")
                setContentView(baueAnsicht())
            }.breitInReihe())
        }
        k.addView(ordnerReihe)

        val reihe = reihe()
        reihe.addView(knopfLeise("Jetzt sichern") {
            lifecycleScope.launch {
                melde("Sichere …")
                melde(Sichern.jetzt(this@EinstellungenActivity))
                setContentView(baueAnsicht())
            }
        }.breitInReihe())
        reihe.addView(knopfLeise("Zurückholen") {
            // ZWEIMAL FRAGEN, WEIL ES DIE TABELLE ANFASST. Zurueckholen
            // schreibt zwar nur in Luecken - aber das muss jemand wissen,
            // bevor er tippt, und nicht danach.
            bestaetige(
                "Zurückholen ergänzt nur, was hier fehlt — vorhandene Tage und " +
                    "Strecken bleiben, wie sie sind. Weiter?"
            ) {
                lifecycleScope.launch {
                    melde("Hole …")
                    melde(Sichern.zurueck(this@EinstellungenActivity))
                }
            }
        }.breitInReihe())
        k.addView(reihe)

        val taeglich = Einstellungen.sicherungTaeglich(this)
        k.addView(knopfLeise(
            if (taeglich) "Tägliche Sicherung: an" else "Tägliche Sicherung: aus"
        ) {
            val neu = !taeglich
            Einstellungen.setzeSicherungTaeglich(this, neu)
            if (neu) Sichern.planen(this) else Sichern.abbestellen(this)
            setContentView(baueAnsicht())
        })
        return k
    }

    /** Ein Eingabefeld im Stil der Karten. */
    private fun feld(
        hinweis: String,
        wert: String,
        geheim: Boolean = false,
    ): android.widget.EditText = android.widget.EditText(this).apply {
        hint = hinweis
        setText(wert)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(farbe(R.color.schrift))
        setHintTextColor(farbe(R.color.schrift_zart))
        maxLines = 1
        setSingleLine()
        if (geheim) {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(farbe(R.color.grund))
            cornerRadius = dp(8f).toFloat()
            setStroke(dp(1f), farbe(R.color.linie))
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8f) }
    }

    private fun android.widget.EditText.text(): String = text.toString().trim()
    private fun android.widget.EditText.leeren() = setText("")

    private fun android.widget.Button.breitInReihe(): android.widget.Button = apply {
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = dp(6f); topMargin = dp(6f) }
    }

    private fun aufgabenKarte(titel: String, text: String): LinearLayout {
        val k = karte()
        k.addView(kartentitel(titel))
        k.addView(zart(text))
        return k
    }

    private fun auffrischen() {
        // Der Zustand steht nur auf der Seite von Kiesel-Helper.
        if (seite != "app" || !::zustand.isInitialized) return
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

    /** OsmAnd: verbunden oder nicht - und was zu tun ist. */
    private fun osmandKarte(): LinearLayout {
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
        return ko
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun stempel(epochSekunden: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(epochSekunden * 1000))
}
