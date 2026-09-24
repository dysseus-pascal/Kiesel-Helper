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

    /** Die Einstellungen der Uhr-Apps, zum Aendern (siehe UhrKarten). */
    private val uhr by lazy { UhrKarten(this) }

    /** Seite und Menue, zum Neufuellen ohne neues Seitenmenue (siehe fuelle). */
    private var inhalt: LinearLayout? = null
    private var inhaltRoller: ScrollView? = null
    private var menueListe: LinearLayout? = null

    /** Das Seitenmenue - und welche Seite gerade offen ist. */
    private var lade: androidx.drawerlayout.widget.DrawerLayout? = null
    private var seite: String = "app"
    private val menueZu = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            lade?.closeDrawer(android.view.Gravity.START)
        }
    }

    /**
     * Die Seiten der Einstellungen: eine je Thema der App - Gesundheit,
     * Training, Ernaehrung - und eine fuer Kiesel-Helper selbst.
     *
     * NACH THEMA, WIE DIE REITER. Wer sein Wasserziel aendern will, denkt an
     * Ernaehrung, nicht an den Namen der Uhr-App, die es zaehlt. Auf jeder
     * Seite stehen die Dienste dieses Themas untereinander, jeder mit seinem
     * Namen darueber. Was nur die App angeht (Erscheinungsbild, Sicherung,
     * Zustand), steht auf ihrer eigenen Seite.
     *
     * Name und Unterzeile als Ressourcen-Nummern: die Liste entsteht mit der
     * Activity, bevor sie Ressourcen lesen kann.
     */
    private data class Seite(
        val schluessel: String, val nameId: Int, val unterId: Int, val farbe: Int, val ton: Int,
    )

    private val THEMEN = listOf(
        Seite("gesundheit", R.string.reiter_gesundheit, R.string.ei_unter_gesundheit, R.color.akzent, Ton.GESUNDHEIT),
        Seite("training", R.string.training, R.string.ei_unter_training, R.color.akzent_training, Ton.TRAINING),
        Seite("ernaehrung", R.string.reiter_ernaehrung, R.string.ei_unter_ernaehrung, R.color.akzent_ernaehrung, Ton.ERNAEHRUNG),
        // KEIN REITER, ABER EIN EIGENES THEMA: Navigation ist kein Training -
        // man faehrt auch zum Einkaufen mit OsmAnd.
        Seite("navigation", R.string.ei_navigation, R.string.ei_unter_navigation, R.color.sport_wandern, Ton.GESUNDHEIT),
    )
    private val APP_SEITE = Seite("app", R.string.app_name, R.string.ei_unter_app, R.color.akzent, Ton.GESUNDHEIT)

    /** Die Seiten bis 0.45.0 hiessen nach den Uhr-Apps; gemerkt ist womoeglich noch eine davon. */
    private fun thema(alt: String): String = when (alt) {
        "herzintervall" -> "gesundheit"
        "kieselsport" -> "training"
        "kieselstrasse" -> "navigation"
        "drinktervall", "supcycle" -> "ernaehrung"
        else -> alt
    }

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
        ) { neuAufbauen() }
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
                neuAufbauen()
            }
        }
        // Vom Zahnrad kommt die Seite des Reiters mit; sonst die zuletzt offene.
        // Nicht nach einem recreate() (Hell/Dunkel umgestellt): da bleibt man,
        // wo man war.
        seite = intent.getStringExtra(SEITE)?.takeIf { savedInstanceState == null }?.takeIf { s -> alleSeiten().any { it.schluessel == s } }
            ?.also { Einstellungen.setzeEinstellungenSeite(this, it) }
            ?: thema(Einstellungen.einstellungenSeite(this))
        onBackPressedDispatcher.addCallback(this, menueZu)
        neuAufbauen()
    }

    override fun onResume() {
        super.onResume()
        // ZURUECK AUS DEN SYSTEMEINSTELLUNGEN: dort kann sich die Standort-
        // erlaubnis geaendert haben, und der Schirm zeigte sonst den alten Stand.
        if (wartetAufEinstellungen) {
            wartetAufEinstellungen = false
            neuAufbauen()
        }
        // Nochmal bei OsmAnd anklopfen. Wer dort eben den Schalter umgelegt
        // hat, kommt als Naechstes hierher und will sehen, dass es wirkt.
        OsmandNavigation.versucheErneut(this)
        auffrischen()
        // Meldet die Uhr ihren Stand, zieht die offene Seite nach.
        Uhreinstellungen.beobachter = { uhr.auffrischen() }
        uhr.auffrischen()
    }

    override fun onPause() {
        super.onPause()
        Uhreinstellungen.beobachter = null
    }

    private fun alleSeiten() = THEMEN + APP_SEITE
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
        inhalt = wurzel
        val roller = ScrollView(this).apply {
            layoutParams = androidx.drawerlayout.widget.DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(wurzel)
        }
        inhaltRoller = roller
        roller.randUmSystemleisten()
        drawer.addView(roller)

        // --- Das Menue ---
        drawer.addView(menue(drawer))
        fuelle()
        return drawer
    }

    /**
     * Seite und Menue neu fuellen - IN DEMSELBEN SEITENMENUE.
     *
     * FRUEHER WURDE DAS GANZE SEITENMENUE ERSETZT, auch beim Wechsel der
     * Seite, waehrend es noch zuging. Das alte meldet sich unter Android 13+
     * fuer die Zurueck-Geste an, solange es offen ist - und abgehaengt, bevor
     * es zu war, meldete es sich nie wieder ab. Zurueck tat danach nichts
     * mehr, der Weg zum Hauptschirm war verbaut.
     */
    private fun fuelle() {
        val wurzel = inhalt ?: return
        val drawer = lade ?: return
        zustand = spalte()
        // DIE SEITE TRAEGT DEN TON IHRES REITERS - Knoepfe und Auswahl in
        // derselben Farbe wie dort.
        Ton.setze(aktuelleSeite().ton)
        wurzel.removeAllViews()
        wurzel.addView(reihe().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(HamburgerView(this@EinstellungenActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply { marginEnd = dp(8f) }
                contentDescription = getString(R.string.ei_menue)
                setOnClickListener { drawer.openDrawer(android.view.Gravity.START) }
            })
            addView(spalte().apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(zart(getString(R.string.ei_einstellungen)))
                addView(kopf(getString(aktuelleSeite().nameId)))
            })
        })
        wurzel.luft(6f)
        when (seite) {
            "gesundheit" -> seiteGesundheit(wurzel)
            "training" -> seiteTraining(wurzel)
            "ernaehrung" -> seiteErnaehrung(wurzel)
            "navigation" -> seiteNavigation(wurzel)
            else -> seiteApp(wurzel)
        }
        menueListe?.let { fuelleMenue(it, drawer) }
    }

    /** Statt setContentView(baueAnsicht()): dasselbe Seitenmenue behalten. */
    private fun neuAufbauen() {
        if (inhalt == null) setContentView(baueAnsicht()) else fuelle()
    }

    private fun fuelleMenue(liste: LinearLayout, drawer: androidx.drawerlayout.widget.DrawerLayout) {
        liste.removeAllViews()
        liste.addView(kopf(getString(R.string.ei_einstellungen)).apply { setPadding(dp(12f), 0, 0, dp(12f)) })
        THEMEN.forEach { liste.addView(menuepunkt(it, drawer)) }
        liste.addView(strich().apply {
            (layoutParams as? LinearLayout.LayoutParams)?.setMargins(dp(12f), dp(10f), dp(12f), dp(10f))
        })
        liste.addView(menuepunkt(APP_SEITE, drawer))
    }

    private fun menue(drawer: androidx.drawerlayout.widget.DrawerLayout): View {
        val liste = spalte().apply { setPadding(dp(12f), dp(20f), dp(12f), dp(20f)) }
        menueListe = liste

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
                fuelle()
                inhaltRoller?.scrollTo(0, 0)
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
                addView(fliesstext(getString(s.nameId)).apply {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    if (gewaehlt) setTextColor(akzentfarbe())
                })
                addView(zart(getString(s.unterId)))
            })
        }.also {
            (it.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(2f)
        }
    }

    // --- Die Seiten ---------------------------------------------------------

    private fun seiteApp(w: LinearLayout) {
        w.addView(fliesstext(getString(R.string.ei_app_text)))
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_erscheinungsbild)))
        w.addView(darstellungskarte())
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_der_tag)))
        w.addView(grenzkarte())
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_sicherung_titel)))
        w.addView(sicherungskarte())
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_fruehere_daten)))
        w.addView(historienkarte())
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_zustand)))
        w.addView(zustand)
        w.luft(12f)
        w.addView(knopfHaupt(getString(R.string.ei_verlauf_ansehen), breit = true) {
            startActivity(Intent(this, VerlaufActivity::class.java))
        })
    }

    private fun seiteGesundheit(w: LinearLayout) {
        w.addView(dienstkopf("Herzintervall", R.color.phase_rem, erster = true))
        seiteHerzintervall(w)
    }

    private fun seiteTraining(w: LinearLayout) {
        w.addView(dienstkopf("Kieselsport", R.color.akzent_training, erster = true))
        seiteKieselsport(w)
    }

    private fun seiteNavigation(w: LinearLayout) {
        w.addView(dienstkopf("Kieselstrasse", R.color.sport_wandern, erster = true))
        seiteKieselstrasse(w)
    }

    private fun seiteErnaehrung(w: LinearLayout) {
        w.addView(dienstkopf("Drinktervall", R.color.wasser, erster = true))
        seiteDrinktervall(w)
        w.addView(dienstkopf("SupCycle", R.color.akzent_ernaehrung))
        seiteSupCycle(w)
        w.addView(dienstkopf(getString(R.string.ei_koffein), R.color.koffein))
        w.addView(aufgabenKarte(
            getString(R.string.ei_koffein_akte),
            getString(R.string.ei_koffein_akte_text)
        ))
    }

    /** Der Name eines Dienstes ueber seinen Karten, mit seiner Farbe davor. */
    private fun dienstkopf(name: String, ton: Int, erster: Boolean = false): View = reihe().apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(4f), dp(if (erster) 8f else 28f), 0, dp(4f))
        addView(View(this@EinstellungenActivity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12f), dp(12f)).apply { marginEnd = dp(10f) }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(farbe(ton))
            }
        })
        addView(TextView(this@EinstellungenActivity).apply {
            text = name
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 21f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(farbe(R.color.schrift))
        })
    }

    private fun seiteDrinktervall(w: LinearLayout) {
        w.addView(aufgabenKarte(
            getString(R.string.ei_dt_akte),
            getString(R.string.ei_dt_akte_text)
        ))
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_heute)))
        w.addView(karte().apply {
            addView(kartentitel(getString(R.string.ei_tagesziel)))
            val glaeser = Einstellungen.wasserGlaeser(this@EinstellungenActivity)
            val ml = Einstellungen.glasMl(this@EinstellungenActivity)
            addView(TextView(this@EinstellungenActivity).apply {
                text = "$glaeser × $ml ml"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(farbe(R.color.schrift))
                setPadding(0, dp(10f), 0, dp(6f))
            })
            addView(zart(getString(R.string.ei_tagesziel_text)))
        })
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_einstellungen_gross)))
        w.addView(uhr.drinktervall())
    }

    private fun seiteHerzintervall(w: LinearLayout) {
        w.addView(aufgabenKarte(
            getString(R.string.ei_hz_akte),
            getString(R.string.ei_hz_akte_text)
        ))
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_schlaf)))
        w.addView(schlafkarte())
    }

    private fun seiteSupCycle(w: LinearLayout) {
        w.addView(aufgabenKarte(
            getString(R.string.ei_sc_akte),
            getString(R.string.ei_sc_akte_text)
        ))
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_plan)))
        w.addView(uhr.supCycle())
    }

    private fun seiteKieselsport(w: LinearLayout) {
        w.addView(aufgabenKarte(
            getString(R.string.ei_ks_akte),
            getString(R.string.ei_ks_akte_text)
        ))
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_strecke)))
        w.addView(spurkarte())
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_einstellungen_gross)))
        w.addView(uhr.kieselsport())
    }

    private fun seiteKieselstrasse(w: LinearLayout) {
        w.addView(aufgabenKarte(
            getString(R.string.ei_ks_osmand),
            getString(R.string.ei_ks_osmand_text)
        ))
        w.addView(osmandKarte())
        w.luft(8f)
        w.addView(abschnitt(getString(R.string.ei_kartenlinks)))
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
        k.addView(kartentitel(getString(R.string.ei_hell_dunkel)))
        val modus = Einstellungen.themaModus(this)
        val reihe = reihe()
        listOf(
            Thema.SYSTEM to getString(R.string.ei_system), Thema.HELL to getString(R.string.ei_hell), Thema.DUNKEL to getString(R.string.ei_dunkel),
        ).forEach { (m, name) ->
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
        k.addView(zart(getString(R.string.ei_system_text)))

        k.luft(14f)
        k.addView(kartentitel("Material You"))
        if (Thema.materialYouMoeglich()) {
            val an = Einstellungen.materialYou(this)
            k.addView(zart(getString(R.string.ei_my_text)))
            k.addView(knopfLeise(getString(if (an) R.string.ei_my_an else R.string.ei_my_aus)) {
                Einstellungen.setzeMaterialYou(this, !an)
                GesundheitWidget.stosseAn(this)
                recreate()
            }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8f) })
        } else {
            k.addView(zart(getString(R.string.ei_my_fehlt)))
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
        k.addView(kartentitel(getString(R.string.ei_idealwert)))
        k.addView(zart(getString(R.string.ei_idealwert_text)))

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
        k.addView(kartentitel(getString(R.string.ei_tag_beginnt)))
        k.addView(zart(getString(R.string.ei_tag_beginnt_text)))

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
            if (stunde == 0) " " + getString(R.string.ei_mitternacht) else ""
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
        k.addView(kartentitel(getString(R.string.ei_link_probieren)))
        k.addView(zart(getString(R.string.ei_link_probieren_text)))

        linkfeld = eingabefeld("https://maps.app.goo.gl/…")
        k.addView(linkfeld)

        linkbefund = zart("")
        k.addView(linkbefund)

        val zeile = reihe()
        zeile.addView(knopfLeise(getString(R.string.ei_einfuegen)) {
            val ablage = getSystemService(android.content.ClipboardManager::class.java)
            val text = ablage?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (text.isNullOrBlank()) melde(getString(R.string.ei_ablage_leer))
            else linkfeld.setText(text.trim())
        })
        zeile.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8f), dp(1f))
        })
        zeile.addView(knopfLeise(getString(R.string.ei_nur_pruefen)) { pruefeLink(false) })
        k.addView(zeile)
        k.addView(knopfHaupt(getString(R.string.ei_an_osmand), breit = true) { pruefeLink(true) })

        k.addView(strich())
        k.addView(kartentitel(getString(R.string.ei_was_link)))
        val wahl = reihe()
        listOf(getString(R.string.ei_ort_zeigen) to false, getString(R.string.ei_fuehrung) to true).forEach { (name, fuehrt) ->
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
                    neuAufbauen()
                }
            })
        }
        k.addView(wahl)
        k.addView(zart(getString(R.string.ei_ort_zeigen_text)))
        return k
    }


    private fun pruefeLink(weitergeben: Boolean) {
        val roh = linkfeld.text?.toString()?.trim().orEmpty()
        if (roh.isEmpty()) {
            linkbefund.text = getString(R.string.ei_kein_link)
            return
        }
        linkbefund.text = getString(R.string.ei_wird_gelesen)
        lifecycleScope.launch {
            val voll = withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (Kartenlink.istKurzlink(roh)) Kartenlink.folge(roh) else roh
            }
            val ziel = Kartenlink.zerlege(voll)
            if (ziel == null || !ziel.brauchbar) {
                linkbefund.text = getString(R.string.ei_kein_ziel) + "\n\n" +
                    getString(R.string.ei_aufgeloest, voll.take(160))
                return@launch
            }
            val gefunden = buildString {
                append(getString(R.string.ei_erkannt) + " ")
                if (ziel.lat != null && ziel.lon != null) {
                    append(Zahlen.zwei(ziel.lat) + ", " + Zahlen.zwei(ziel.lon))
                }
                if (!ziel.text.isNullOrBlank()) {
                    if (ziel.lat != null) append(" — ")
                    append("»" + ziel.text + "«")
                }
                if (voll != roh) append("\n" + getString(R.string.ei_aufgeloest, voll.take(120)))
            }
            if (!weitergeben) {
                linkbefund.text = gefunden
                return@launch
            }
            linkbefund.text = gefunden + "\n\n" + getString(R.string.ei_wird_uebergeben)
            val aus = Kartenlink.uebergib(this@EinstellungenActivity, ziel)
            linkbefund.text = gefunden + "\n\n" + if (aus.geschafft) {
                getString(R.string.ei_uebergeben, aus.beschreibung)
            } else {
                getString(R.string.ei_abgelehnt, aus.weg)
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
    private fun spurkarte(): LinearLayout {
        val k = karte()
        k.addView(kartentitel(getString(R.string.ei_strecke_aufzeichnen)))

        val erlaubt = SpurDienst.darfOrten(this)
        val immer = SpurDienst.darfImmerOrten(this)
        k.addView(schild(
            erlaubt && immer,
            when {
                !erlaubt -> getString(R.string.ei_standort_nicht)
                !immer -> getString(R.string.ei_standort_nutzung)
                else -> getString(R.string.ei_standort_immer)
            }
        ))
        k.addView(zart(getString(R.string.ei_kein_gps)))
        if (!erlaubt) {
            k.addView(knopfHaupt(getString(R.string.ei_standort_erlauben), breit = true) {
                ortStarter?.launch(arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ))
            })
        } else if (!immer) {
            // DAS IST DER HAEUFIGE FALL, und er sieht aus wie ein Fehler der
            // App: die Erlaubnis steht da, die Karte bleibt leer.
            k.addView(fliesstext(getString(R.string.ei_nur_nutzung_text)))
            k.addView(knopfHaupt(getString(R.string.ei_immer_erlauben), breit = true) {
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
                this, getString(R.string.ei_einstellungen_fehlen), android.widget.Toast.LENGTH_SHORT
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
        k.addView(kartentitel(getString(R.string.ei_nachladen_titel)))
        k.addView(zartMitHinweis(
            getString(R.string.ei_nachladen_kurz),
            getString(R.string.ei_nachladen_lang)
        ))
        k.addView(knopfHaupt(getString(R.string.ei_nachladen), breit = true) {
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
        melde(getString(R.string.ei_hole_hc))
        val erteilt = runCatching {
            Akte(this).bereit()?.permissionController?.getGrantedPermissions()
        }.getOrNull().orEmpty()
        val tage = Gesundheit(this).nachtragen()
        melde(
            if (tage == 0) getString(R.string.ei_nichts_gefunden)
            else resources.getQuantityString(
                if (Gesundheit.HISTORIE in erteilt) R.plurals.ei_tage_geholt else R.plurals.ei_tage_geholt_ohne,
                tage, tage,
            )
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
        k.addView(kartentitel(getString(R.string.ei_sicherung)))

        val zuletzt = Einstellungen.sicherungZuletzt(this)
        k.addView(schild(
            zuletzt > 0,
            if (zuletzt > 0) getString(
                R.string.ei_zuletzt,
                java.text.DateFormat
                    .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                    .format(java.util.Date(zuletzt)),
            )
            else getString(R.string.ei_nie_gesichert)
        ))
        k.addView(zartMitHinweis(
            getString(R.string.ei_sicherung_kurz),
            getString(R.string.ei_sicherung_lang)
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
                ordnerName != null -> getString(R.string.ei_ordner_da, ordnerName)
                ordnerUri.isNotBlank() -> getString(R.string.ei_ordner_weg)
                else -> getString(R.string.ei_kein_ordner)
            }
        ))
        k.addView(zartMitHinweis(
            getString(R.string.ei_ordner_kurz),
            getString(R.string.ei_ordner_lang)
        ))
        val ordnerReihe = reihe()
        ordnerReihe.addView(knopfHaupt(
            getString(if (ordnerName != null) R.string.ei_anderer_ordner else R.string.ei_ordner_waehlen)
        ) {
            ordnerStarter?.launch(null)
        }.breitInReihe())
        if (ordnerUri.isNotBlank()) {
            ordnerReihe.addView(knopfLeise(getString(R.string.ei_ordner_entfernen)) {
                Einstellungen.setzeSicherungOrdner(this, "")
                neuAufbauen()
            }.breitInReihe())
        }
        k.addView(ordnerReihe)

        val reihe = reihe()
        reihe.addView(knopfLeise(getString(R.string.ei_jetzt_sichern)) {
            lifecycleScope.launch {
                melde(getString(R.string.ei_sichere))
                melde(Sichern.jetzt(this@EinstellungenActivity))
                neuAufbauen()
            }
        }.breitInReihe())
        reihe.addView(knopfLeise(getString(R.string.ei_zurueckholen)) {
            // ZWEIMAL FRAGEN, WEIL ES DIE TABELLE ANFASST. Zurueckholen
            // schreibt zwar nur in Luecken - aber das muss jemand wissen,
            // bevor er tippt, und nicht danach.
            bestaetige(getString(R.string.ei_zurueckholen_frage)) {
                lifecycleScope.launch {
                    melde(getString(R.string.ei_hole))
                    melde(Sichern.zurueck(this@EinstellungenActivity))
                }
            }
        }.breitInReihe())
        k.addView(reihe)

        val taeglich = Einstellungen.sicherungTaeglich(this)
        k.addView(knopfLeise(
            getString(if (taeglich) R.string.ei_taeglich_an else R.string.ei_taeglich_aus)
        ) {
            val neu = !taeglich
            Einstellungen.setzeSicherungTaeglich(this, neu)
            if (neu) Sichern.planen(this) else Sichern.abbestellen(this)
            neuAufbauen()
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
                    kk.addView(zart(getString(R.string.ei_ohne_hc)))
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    kk.addView(schild(false, getString(R.string.hc_update)))
                else -> {
                    val fehlt = Akte(this@EinstellungenActivity)
                        .fehlendeBerechtigungen(Aufgaben.BERECHTIGUNGEN)
                    if (fehlt.isEmpty()) {
                        kk.addView(schild(true, getString(R.string.ei_schreib_ok)))
                    } else {
                        kk.addView(schild(false, getString(R.string.ei_schreib_fehlt)))
                        kk.addView(zart(getString(R.string.ei_schreib_fehlt_text)))
                        kk.addView(knopfHaupt(getString(R.string.erlaubnis_erteilen), breit = true) {
                            erlaubnisStarter?.launch(fehlt)
                                ?: melde(getString(R.string.noch_nicht_bereit))
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
                kb.addView(kartentitel(getString(R.string.ei_in_der_akte)))
                befunde.forEach { b ->
                    kb.addView(zart(
                        b.name + ": " +
                            (if (b.anzahl == 0) getString(R.string.ei_nichts) else resources.getQuantityString(R.plurals.ei_saetze, b.anzahl, b.anzahl)) +
                            (if (b.quellen.isEmpty()) "" else " — " + b.quellen.joinToString(", "))
                    ))
                }
                kb.addView(zart(getString(R.string.ei_leeres_feld)))
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
        val verbunden = lage == OsmandNavigation.Lage.VERBUNDEN || lage == OsmandNavigation.Lage.ABONNIERT
        ko.addView(schild(verbunden, getString(R.string.ei_osmand_x, OsmandNavigation.lageText(this))))
        if (!verbunden) {
            ko.addView(zart(
                getString(
                    if (lage == OsmandNavigation.Lage.FREISCHALTEN) R.string.ei_osmand_freischalten
                    else R.string.ei_osmand_fehlt
                )
            ))
            ko.addView(knopfHaupt(getString(R.string.ei_osmand_oeffnen), breit = true) {
                val start = packageManager.getLaunchIntentForPackage("net.osmand.plus")
                    ?: packageManager.getLaunchIntentForPackage("net.osmand")
                if (start != null) startActivity(start) else melde(getString(R.string.ei_osmand_nicht_gefunden))
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

    companion object {
        /** Welche Seite oeffnen: "gesundheit", "training", "ernaehrung" oder "app". */
        const val SEITE = "seite"
    }
}
