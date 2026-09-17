package ch.dysseus.kieselhelper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
 * Der erste Bildschirm: was eingebunden IST.
 *
 * Getrennt vom Einbinden, und das mit Absicht. Auf diesen Bildschirm schaut
 * man, um zu sehen, was gerade in die eigene Gesundheitsakte schreibt — eine
 * Frage, die man oefter hat als die nach einer neuen Beschreibung. Das
 * Eingabefeld, der Katalog und die Erklaerungen dazu stehen deshalb hinter dem
 * Knopf unten, in [EinbindenActivity].
 */
class HauptActivity : ComponentActivity() {

    private lateinit var zustand: TextView
    private lateinit var liste: LinearLayout
    private lateinit var erlaubnisStarter: ActivityResultLauncher<Set<String>>

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

        // Der Empfaenger muss zur Laufzeit angemeldet sein, sonst erreicht ihn
        // der implizite Broadcast der Pebble-App nicht. Siehe EmpfangsDienst.
        EmpfangsDienst.starte(this)

        setContentView(baueAnsicht())
    }

    override fun onResume() {
        super.onResume()
        auffrischen()
    }

    private fun baueAnsicht(): ScrollView {
        val wurzel = spalte().apply {
            setPadding(dp(20f), dp(28f), dp(20f), dp(32f))
        }

        wurzel.addView(kopf(getString(R.string.app_name)))
        wurzel.luft(6f)
        wurzel.addView(zart(getString(R.string.intro_kurz)))

        wurzel.addView(abschnitt(getString(R.string.abschnitt_zustand)))
        val zustandskarte = karte()
        zustand = fliesstext("")
        zustandskarte.addView(zustand)
        // Der Weg zum Verlauf gehört in die Zustandskarte und nicht ans Ende
        // des Bildschirms: wer wissen will, was zuletzt geschah, fragt an
        // derselben Stelle auch, was davor geschah.
        zustandskarte.luft(12f)
        zustandskarte.addView(knopfLeise(getString(R.string.verlauf_kurz)) {
            startActivity(Intent(this, VerlaufActivity::class.java))
        })
        wurzel.addView(zustandskarte)

        wurzel.addView(abschnitt(getString(R.string.abschnitt_eingebunden)))
        liste = spalte()
        wurzel.addView(liste)

        wurzel.luft(4f)
        wurzel.addView(knopfHaupt(getString(R.string.einbinden), breit = true) {
            startActivity(Intent(this, EinbindenActivity::class.java))
        })

        return ScrollView(this).apply {
            isFillViewport = true
            addView(wurzel)
            randUmSystemleisten()
        }
    }

    private fun auffrischen() {
        zeichneListe()
        lifecycleScope.launch { zustand.text = baueZustand() }
    }

    /**
     * Die Beschreibungen als Karten.
     *
     * Gelesen wird ueber alleEintraege() und nicht ueber alle(): eine
     * Beschreibung, die sich nicht einlesen laesst, faellt dort still heraus.
     * Genau die muss man aber SEHEN — sonst wundert man sich wochenlang, warum
     * nichts ankommt.
     */
    private fun zeichneListe() {
        val speicher = ModulSpeicher(this)
        val eintraege = speicher.alleEintraege()
        liste.removeAllViews()

        if (eintraege.isEmpty()) {
            val leer = karte()
            leer.addView(kartentitel(getString(R.string.keine_module_titel)))
            leer.luft(6f)
            leer.addView(zart(getString(R.string.keine_module)))
            liste.addView(leer)
            return
        }

        for (e in eintraege) {
            val ergebnis = Modul.lies(e.text)
            liste.addView(
                if (ergebnis.modul == null) kaputteKarte(e, ergebnis.fehler)
                else modulKarte(e, ergebnis.modul)
            )
        }
    }

    private fun modulKarte(e: ModulSpeicher.Eintrag, m: Modul): LinearLayout {
        val k = karte()
        k.addView(kartentitel(m.name))
        if (m.beschreibung.isNotEmpty()) {
            k.luft(6f)
            k.addView(fliesstext(m.beschreibung))
        }

        k.addView(strich())
        k.addView(zart(m.quelle.klartext()))
        k.luft(4f)
        for (t in m.taetigkeiten()) k.addView(zart("→ " + t))
        k.luft(8f)
        k.addView(zart(kurzeQuelle(e.quelle) + "\n" + getString(R.string.geholt_am, stempel(e.geholtAm))))

        // Das Schildchen und der Knopf haengen an einer Abfrage, die dauern
        // kann. Also erst den Kasten, dann fuellen.
        //
        // UNTEREINANDER und nicht nebeneinander: "Zugriff auf
        // Benachrichtigungen fehlt" ist so lang, dass daneben nichts mehr
        // hinpasst - der Knopf wurde zu einem Balken ohne Aufschrift
        // gequetscht. Eine Reihe haelt nur, solange alles kurz bleibt.
        k.luft(12f)
        val erlaubnis = spalte()
        k.addView(erlaubnis)
        lifecycleScope.launch {
            erlaubnis.removeAllViews()
            // Zwei ganz verschiedene Freigaben, und beide gehoeren an die Karte
            // des Zettels, der sie braucht. Die eine erteilt Health Connect in
            // einem Dialog, die andere nur die Systemeinstellung - dafuer gibt
            // es keine Abfrage zur Laufzeit, eine App kann nur hinfuehren.
            if (m.brauchtBenachrichtigungen() &&
                !BenachrichtigungsHorcher.freigegeben(this@HauptActivity)
            ) {
                erlaubnis.addView(schild(false, getString(R.string.benachrichtigungen_fehlt)))
                erlaubnis.addView(
                    knopfHaupt(getString(R.string.freigeben)) {
                        startActivity(BenachrichtigungsHorcher.einstellungen())
                    }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10f) }
                )
                return@launch
            }
            val fehlt = Akte(this@HauptActivity).fehlendeBerechtigungen(m.berechtigungen())
            if (fehlt.isEmpty()) {
                erlaubnis.addView(
                    schild(
                        true,
                        if (m.brauchtBenachrichtigungen()) getString(R.string.benachrichtigungen_da)
                        else getString(R.string.erlaubnis_da)
                    )
                )
            } else {
                erlaubnis.addView(schild(false, getString(R.string.erlaubnis_fehlt)))
                erlaubnis.addView(
                    knopfHaupt(getString(R.string.erlaubnis)) { erlaubnisStarter.launch(fehlt) }
                        .apply {
                            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(10f)
                        }
                )
            }
        }

        k.luft(12f)
        k.addView(knoepfe(e))
        return k
    }

    private fun kaputteKarte(e: ModulSpeicher.Eintrag, fehler: List<String>): LinearLayout {
        val k = karte()
        k.addView(kartentitel(kurzeQuelle(e.quelle)))
        k.luft(8f)
        k.addView(schild(false, getString(R.string.nicht_verwendbar)))
        k.luft(10f)
        k.addView(zart(fehler.joinToString("\n") { "• " + it }))
        k.luft(12f)
        k.addView(knoepfe(e))
        return k
    }

    private fun knoepfe(e: ModulSpeicher.Eintrag): LinearLayout {
        val r = reihe()
        r.addView(knopfLeise(getString(R.string.erneuern)) { erneuere(e.quelle) })
        r.addView(
            knopfLeise(getString(R.string.entfernen), warnend = true) {
                ModulSpeicher(this).entferne(e.quelle)
                auffrischen()
                // Diese beiden stehen NEBENeinander - hier ist der linke
                // Abstand der richtige, nicht der obere.
            }.apply { (layoutParams as LinearLayout.LayoutParams).marginStart = dp(10f) }
        )
        return r
    }

    /** Neu holen — dieselbe Quelle, dieselbe Pruefung wie beim Einbinden. */
    private fun erneuere(quelle: String) {
        lifecycleScope.launch {
            val text = ModulSpeicher.hole(quelle).getOrElse { fehler ->
                melde(getString(R.string.nicht_geholt, fehler.message ?: "?"))
                return@launch
            }
            val ergebnis = Modul.lies(text)
            val m = ergebnis.modul
            if (m == null) {
                melde(ergebnis.fehler.joinToString("\n"))
                return@launch
            }
            ModulSpeicher(this@HauptActivity).lege(quelle, text)
            melde(getString(R.string.erneuert, m.name))
            auffrischen()
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
        return if (letzte.isEmpty()) {
            getString(R.string.dienst_hinweis) + "\n\n" + getString(R.string.nichts_bisher)
        } else {
            getString(R.string.dienst_hinweis) + "\n\n" +
                getString(R.string.zuletzt, stempel(verlauf.letzteMeldungAm())) + "\n" + letzte
        }
    }

    private fun melde(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    /**
     * Aus der langen Rohadresse das, was einen Menschen interessiert.
     *
     * Die volle raw.githubusercontent-Adresse fuellte in der ersten Fassung
     * drei Zeilen der Karte und sagte dabei nichts, was die letzten beiden
     * Glieder nicht auch sagen.
     */
    private fun kurzeQuelle(adresse: String): String {
        val teile = adresse.removePrefix("https://").split("/")
        val i = teile.indexOf("raw.githubusercontent.com")
        return if (i == 0 && teile.size >= 3) teile[1] + "/" + teile[2] else adresse
    }

    private fun stempel(epochSekunden: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(epochSekunden * 1000))
}
