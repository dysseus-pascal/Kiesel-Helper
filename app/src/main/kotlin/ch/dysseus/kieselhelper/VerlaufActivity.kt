package ch.dysseus.kieselhelper

import android.os.Bundle
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import java.text.DateFormat
import java.util.Date

/**
 * Was die App getan hat, als niemand hinsah.
 *
 * DER GRUND, WARUM ES DIESEN BILDSCHIRM GIBT: Kiesel-Helper arbeitet im
 * Hintergrund, und geprüft wurde er bis hierher über das Logbuch des Systems.
 * Das trägt nicht weit genug. Der Logcat-Puffer hält Stunden, nicht Tage; eine
 * Deinstallation nimmt den Prozess mit; und an ein Kabel kommt man unterwegs
 * ohnehin nicht. An genau dieser Kette ist die Auswertung einer Autofahrt
 * gescheitert — die Fahrt war vorbei, die Spur weg.
 *
 * Der Verlauf steht deshalb in der App. Er übersteht Neustarts, braucht kein
 * Kabel und keinen Rechner, und man liest ihn, wo man gerade steht.
 */
class VerlaufActivity : KieselActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // AUSDRUECKLICH ZURUECK: sonst traegt dieser Schirm den Ton des
        // Reiters, aus dem er geoeffnet wurde.
        Ton.setze(Ton.GESUNDHEIT)
        setContentView(baueAnsicht())
    }

    override fun onResume() {
        super.onResume()
        setContentView(baueAnsicht())
    }

    private fun baueAnsicht(): ScrollView {
        val wurzel = spalte().apply {
            setPadding(dp(20f), dp(28f), dp(20f), dp(32f))
        }

        wurzel.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        wurzel.luft(16f)
        wurzel.addView(kopf(getString(R.string.verlauf)))
        wurzel.luft(8f)
        wurzel.addView(zart(getString(R.string.verlauf_hinweis)))

        val zeilen = Verlauf(this).verlauf()
        wurzel.addView(abschnitt(getString(R.string.verlauf_anzahl, zeilen.size)))

        if (zeilen.isEmpty()) {
            val leer = karte()
            leer.addView(zart(getString(R.string.nichts_bisher)))
            wurzel.addView(leer)
        } else {
            // Alles in EINE Karte: hundert einzelne Karten wären hundert
            // Rahmen, und was man hier sucht, ist eine Folge, kein Einzelstück.
            val k = karte()
            for ((i, z) in zeilen.withIndex()) {
                if (i > 0) k.addView(strich())
                k.addView(zart(stempel(z.am)))
                k.addView(fliesstext(z.text))
            }
            wurzel.addView(k)

            wurzel.addView(
                knopfLeise(getString(R.string.leeren), warnend = true) {
                    Verlauf(this).leereVerlauf()
                    setContentView(baueAnsicht())
                }
            )
        }

        return ScrollView(this).apply {
            isFillViewport = true
            addView(wurzel)
            randUmSystemleisten()
        }
    }

    private fun stempel(epochSekunden: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
            .format(Date(epochSekunden * 1000))
}
