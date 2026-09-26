package ch.dysseus.kieselhelper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Eine Nacht im Verlauf.
 *
 * DIE KARTE SAGT, WIE VIEL; DIESE SEITE SAGT, WANN. Oben das Hypnogramm,
 * darunter auf derselben Zeitachse Bewegung, Puls, HRV und SpO2 - und ein
 * Finger darauf sagt, was um diese Minute war. Mit den Pfeilen zurueck bis
 * zu sieben Naechte; die Rohdaten reichen dreissig Tage, die Akte weiter,
 * aber eine Woche ist die Frage, die man morgens stellt.
 */
class NachtActivity : KieselActivity() {

    private lateinit var inhalt: LinearLayout
    private lateinit var titel: TextView
    private lateinit var zurueck: TextView
    private lateinit var vor: TextView
    private var versatz = 0
    private val zone = ZoneId.systemDefault()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ton.setze(Ton.GESUNDHEIT)
        setContentView(baueAnsicht())
        lade()
    }

    private fun baueAnsicht(): ScrollView {
        val wurzel = spalte().apply { setPadding(dp(16f), dp(16f), dp(16f), dp(28f)) }
        wurzel.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        wurzel.luft(10f)
        wurzel.addView(kopf(getString(R.string.n_titel)))
        wurzel.luft(6f)

        // Blaettern: links die Nacht davor, rechts die danach.
        fun pfeil(text: String, tue: () -> Unit) = TextView(this).apply {
            this.text = text
            textSize = 22f
            setTextColor(akzentfarbe())
            setPadding(dp(14f), dp(4f), dp(14f), dp(4f))
            setOnClickListener { tue() }
        }
        zurueck = pfeil("‹") { if (versatz < MAX_ZURUECK) { versatz++; lade() } }
        vor = pfeil("›") { if (versatz > 0) { versatz--; lade() } }
        titel = fliesstext("").apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        wurzel.addView(reihe().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(zurueck); addView(titel); addView(vor)
        })
        wurzel.luft(6f)
        inhalt = spalte()
        wurzel.addView(inhalt)
        return ScrollView(this).apply {
            isFillViewport = true
            addView(wurzel)
            randUmSystemleisten()
        }
    }

    private fun tagText(tag: LocalDate): String {
        val sprache = Locale.getDefault()
        val f = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(sprache, "EEEdMMM"), sprache)
        return f.format(tag.minusDays(1)) + " → " + f.format(tag)
    }

    private fun uhr(t: Instant): String {
        val z = t.atZone(zone)
        return String.format("%02d:%02d", z.hour, z.minute)
    }

    private fun lade() {
        val g = Gesundheit(this)
        val tag = g.heutigeNacht().minusDays(versatz.toLong())
        titel.text = tagText(tag)
        zurueck.alpha = if (versatz < MAX_ZURUECK) 1f else 0.3f
        vor.alpha = if (versatz > 0) 1f else 0.3f
        inhalt.removeAllViews()
        inhalt.addView(zart(getString(R.string.n_lade)))
        lifecycleScope.launch {
            val bild = g.nacht(tag)
            Ton.setze(Ton.GESUNDHEIT)
            inhalt.removeAllViews()
            if (bild == null) {
                inhalt.addView(karte().apply { addView(zart(getString(R.string.n_keine))) })
                return@launch
            }
            zeige(bild)
        }
    }

    private fun zeige(b: Gesundheit.NachtBild) {
        val h = b.hypnogramm
        val wach = h.stufen.filter { it.bahn == Hypnogramm.WACH }.sumOf { it.minuten }

        // --- Kopf und Bild ---
        val k = karte()
        k.addView(reihe().apply {
            addView(messwert(getString(R.string.schlaf), Zahlen.dauer(b.schlafMinuten), "", 0f, false))
            addView(messwert(getString(R.string.ruhepuls), Zahlen.ganz(b.ruhepuls), "bpm", 0f, false))
            addView(messwert(getString(R.string.hrv), Zahlen.ganz(b.hrv), "ms", 0f, false))
        })
        k.addView(zart(getString(R.string.n_zeiten, uhr(h.von), uhr(h.bis), wach.toInt())))
        val info = zart(getString(R.string.n_tipp))
        k.addView(hypnogrammbild(h, b) { t -> info.text = zeile(b, t) })
        k.addView(info.apply { setPadding(0, dp(6f), 0, 0) })
        inhalt.addView(k)

        // --- Phasen ---
        b.phasen?.takeIf { it.da }?.let { p ->
            inhalt.addView(abschnitt(getString(R.string.n_phasen)))
            inhalt.addView(karte().apply {
                addView(phasenbild(p, mitBalken = false))
                addView(zart(getString(R.string.n_schaetzung)).apply { setPadding(0, dp(8f), 0, 0) })
            })
        }

        // --- Wachphasen ---
        inhalt.addView(abschnitt(getString(R.string.n_wachphasen)))
        inhalt.addView(karte().apply {
            val phasen = h.wachphasen()
            if (phasen.isEmpty()) {
                addView(zart(getString(R.string.n_keine_wachphasen)))
            } else {
                phasen.forEach { s ->
                    addView(fliesstext(getString(R.string.n_wach_zeile, uhr(s.von), uhr(s.bis), s.minuten.toInt())))
                }
            }
        })
    }

    /** Was um [t] war: Uhrzeit, Phase, Puls, Bewegung. */
    private fun zeile(b: Gesundheit.NachtBild, t: Instant): String {
        val phase = when (b.hypnogramm.bahnBei(t)) {
            Hypnogramm.WACH -> getString(R.string.phase_wach)
            Hypnogramm.REM -> getString(R.string.phase_rem)
            Hypnogramm.LEICHT -> getString(R.string.phase_leicht)
            Hypnogramm.TIEF -> getString(R.string.phase_tief)
            else -> "–"
        }
        // Der naechste Puls innerhalb von fuenf Minuten - die Uhr misst nachts
        // nicht jede Minute.
        val puls = b.puls.minByOrNull { kotlin.math.abs(it.first.epochSecond - t.epochSecond) }
            ?.takeIf { kotlin.math.abs(it.first.epochSecond - t.epochSecond) <= 300 }?.second
        val bewegung = b.bewegung.lastOrNull { !it.first.isAfter(t) }
            ?.takeIf { t.epochSecond - it.first.epochSecond < 60 }?.second
        val bew = when {
            bewegung == null -> "–"
            bewegung < 0 -> getString(R.string.n_keine_daten)
            else -> bewegung.toString()
        }
        return getString(R.string.n_info, uhr(t), phase, puls?.toString() ?: "–", bew)
    }

    companion object {
        private const val MAX_ZURUECK = 6

        fun zeige(ctx: Context) {
            ctx.startActivity(Intent(ctx, NachtActivity::class.java))
        }
    }
}
