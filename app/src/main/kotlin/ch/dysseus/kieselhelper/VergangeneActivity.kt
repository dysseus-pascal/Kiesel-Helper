package ch.dysseus.kieselhelper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Die vergangenen Trainings - und eines davon im Einzelnen.
 *
 * EINE EIGENE SEITE STATT EINER LANGEN LISTE IM REITER. Zwanzig Karten unter
 * den Bildern nahmen dort mehr Platz als alles andere, und gesucht wird in
 * ihnen selten. Hier stehen sie als Zeilen, nach Monaten, und ein Tippen
 * zeigt das Training so gross wie das juengste im Reiter: mit Puls, Saetzen
 * oder Bahnen und der Karte.
 *
 * DERSELBE SCHIRM FUER BEIDES. Ohne [EXTRA_BEGINN] die Liste, mit ihm das
 * eine Training - so fuehrt der Zurueck-Knopf von der Einzelansicht wieder
 * in die Liste, wie man es erwartet.
 */
class VergangeneActivity : ComponentActivity() {

    private lateinit var inhalt: LinearLayout

    /** Die Kartenansichten dieses Schirms - sie wollen seinen Lebenslauf. */
    private val karten = mutableListOf<MapView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ton.setze(Ton.TRAINING)
        setContentView(baueAnsicht())
        lifecycleScope.launch { lade() }
    }

    override fun onResume() {
        super.onResume()
        karten.forEach { it.onResume() }
    }

    override fun onPause() {
        karten.forEach { it.onPause() }
        super.onPause()
    }

    override fun onDestroy() {
        karten.forEach { it.onDetach() }
        karten.clear()
        super.onDestroy()
    }

    private val beginn: Long get() = intent.getLongExtra(EXTRA_BEGINN, -1L)

    private fun baueAnsicht(): ScrollView {
        val wurzel = spalte().apply { setPadding(dp(16f), dp(16f), dp(16f), dp(28f)) }
        wurzel.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        wurzel.luft(10f)
        wurzel.addView(kopf(if (beginn < 0) "Vergangene Trainings" else "Training"))
        wurzel.luft(6f)
        inhalt = spalte()
        wurzel.addView(inhalt)
        return ScrollView(this).apply {
            isFillViewport = true
            addView(wurzel)
            randUmSystemleisten()
        }
    }

    private suspend fun lade() {
        // Was der Reiter eben gebaut hat. Leer nur, wenn der Prozess
        // dazwischen neu begann - dann selbst holen.
        val alle = TrainingTab.zuletzt.ifEmpty { TrainingTab.hole(this) }
        Ton.setze(Ton.TRAINING)
        inhalt.removeAllViews()

        if (beginn < 0) {
            zeigeListe(alle.drop(1))
            return
        }
        val eintrag = alle.firstOrNull { it.sitzung.startTime.epochSecond == beginn }
        if (eintrag == null) {
            inhalt.addView(karte().apply {
                addView(zart("Dieses Training steht nicht mehr in der Gesundheitsakte."))
            })
            return
        }
        val voll = TrainingTab.vervollstaendige(this, eintrag)
        Ton.setze(Ton.TRAINING)
        inhalt.addView(TrainingTab.sitzungskarte(this, voll, gross = true, karten = karten,
            mitKarte = false, antippbar = false))
        zeigeDetails(voll)
        karten.forEach { it.onResume() }
    }

    /**
     * Alles, was mehr ist als der erste Blick.
     *
     * DIE STRECKE IST DIE ACHSE. Puls, Tempo und Hoehe stehen ueber den
     * Kilometern, nicht ueber der Zeit: "am Anstieg bei Kilometer vier" ist,
     * wie man sich an eine Fahrt erinnert. Und auf der Karte traegt die Linie
     * das Tempo als Farbe - blau, wo es zaeh war, rot, wo es lief.
     */
    private fun zeigeDetails(e: TrainingTab.Eintrag) {
        val art = Sportart.von(e.sitzung)
        val ton = farbe(art.farbe)
        val maxpuls = Einstellungen.maxpuls(this)
        val rad = e.sitzung.exerciseType == androidx.health.connect.client.records.ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        val strecke = Trainingsanalyse.strecke(e.punkte)

        // --- Zonen ---
        if (e.puls.size >= 2) {
            val zonen = Trainingsanalyse.zonenSekunden(e.puls, maxpuls)
            val schwer = zonen[4] + zonen[5]
            val summe = zonen.sum().coerceAtLeast(1)
            inhalt.addView(abschnitt("PULSZONEN"))
            inhalt.addView(karte().apply {
                addView(zart("Zeit je Zone, Maximalpuls $maxpuls (Einstellungen)"))
                addView(zonenbalken(zonen))
                addView(zonenliste(zonen, maxpuls))
                addView(zart(
                    when {
                        schwer * 100 / summe >= 50 -> "Mehr als die Hälfte an oder über der Schwelle — ein hartes Training."
                        zonen[2] + zonen[3] >= summe * 6 / 10 -> "Vor allem Grundlage und Ausdauer — so baut man Form auf."
                        zonen[0] + zonen[1] >= summe / 2 -> "Überwiegend locker — Erholung oder ein Spaziergang mit Puls."
                        else -> "Gemischt über die Zonen."
                    }
                ).apply { setPadding(0, dp(8f), 0, 0) })
            })
        }

        if (strecke.size >= 2) {
            // --- Puls ueber die Strecke, mit den Zonen als Baender ---
            val pulsStrecke = Trainingsanalyse.pulsUeberStrecke(e.puls, strecke)
            if (pulsStrecke.size >= 2) {
                val baender = (1..5).map { z ->
                    val von = maxpuls * Trainingsanalyse.ZONEN_PROZENT[z - 1] / 100.0
                    val bis = if (z == 5) 250.0 else maxpuls * Trainingsanalyse.ZONEN_PROZENT[z] / 100.0
                    Triple(von, bis, Zonenfarben.FARBEN[z])
                }
                inhalt.addView(abschnitt("PULS ÜBER DIE STRECKE"))
                inhalt.addView(karte().apply {
                    addView(zart("Wo der Puls stieg — die Bänder sind die Zonen"))
                    addView(streckenverlauf(pulsStrecke, ton, "bpm", baender = baender))
                })
            }

            // --- Tempo ---
            val tempo = Trainingsanalyse.tempoUeberStrecke(strecke)
            val (langsam, schnell) = Trainingsanalyse.tempoSpanne(strecke)
            val tempoText: (Double) -> String = { ms ->
                if (rad) Zahlen.eine(ms * 3.6) + " km/h"
                else if (ms > 0.2) { val s = 1000 / ms; String.format("%d:%02d /km", (s / 60).toInt(), (s % 60).toInt()) } else "–"
            }
            inhalt.addView(abschnitt("TEMPO"))
            inhalt.addView(karte().apply {
                val mittel = strecke.last().meter / strecke.last().sekunde.coerceAtLeast(1)
                val spitze = strecke.maxOf { it.tempo }
                addView(reihe().apply {
                    addView(messwert("Schnitt", tempoText(mittel).substringBefore(" "), tempoText(mittel).substringAfter(" ", ""), 0f, false))
                    addView(messwert("Spitze", tempoText(spitze).substringBefore(" "), tempoText(spitze).substringAfter(" ", ""), 0f, false))
                    addView(messwert("Bewegt", Zahlen.dauer(strecke.last().sekunde / 60.0), "", 0f, false))
                })
                addView(zart("Über die Strecke, gefärbt wie auf der Karte").apply { setPadding(0, dp(10f), 0, 0) })
                addView(streckenverlauf(
                    tempo, ton, "km/h",
                    farbeJeWert = { kmh -> Zonenfarben.tempofarbe((((kmh / 3.6) - langsam) / (schnell - langsam)).toFloat()) },
                    flaeche = false, nachkomma = 0,
                ))
            })

            // --- Die Karte, gefaerbt ---
            inhalt.addView(abschnitt("TEMPO AUF DER KARTE"))
            inhalt.addView(karte().apply {
                addView(zart("Blau, wo es zäh war — rot, wo es lief"))
                addView(tempokarte(strecke, karten))
                addView(tempolegende(tempoText(langsam), tempoText(schnell)))
            })

            // --- Hoehe ---
            val hoehe = Trainingsanalyse.hoeheUeberStrecke(strecke)
            if (hoehe.size >= 2 && hoehe.maxOf { it.wert } - hoehe.minOf { it.wert } >= 10) {
                inhalt.addView(abschnitt("HÖHENPROFIL"))
                inhalt.addView(karte().apply {
                    addView(reihe().apply {
                        addView(messwert("Aufstieg", Zahlen.ganz(Spur.hoehenmeter(e.punkte)), "m", 0f, false))
                        addView(messwert("Tiefster", Zahlen.ganz(hoehe.minOf { it.wert }), "m ü. M.", 0f, false))
                        addView(messwert("Höchster", Zahlen.ganz(hoehe.maxOf { it.wert }), "m ü. M.", 0f, false))
                    })
                    addView(streckenverlauf(hoehe, farbe(R.color.sport_wandern), "m", nachkomma = 0, hoehe = 130f))
                })
            }

            // --- Kilometer ---
            val km = Trainingsanalyse.kilometer(strecke, e.puls)
            if (km.size >= 2) {
                inhalt.addView(abschnitt("KILOMETER FÜR KILOMETER"))
                inhalt.addView(karte().apply {
                    val schnellste = km.filter { it.meter >= 900 }.minByOrNull { it.sekunden / it.meter }
                    addView(reihe().apply {
                        addView(zart("km").apply { minWidth = dp(34f) })
                        addView(zart("Zeit").apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                        addView(zart(if (rad) "km/h" else "/km").apply { minWidth = dp(64f) })
                        addView(zart("Puls").apply { minWidth = dp(48f) })
                        addView(zart("Auf").apply { minWidth = dp(48f); gravity = android.view.Gravity.END })
                    })
                    km.forEach { k ->
                        addView(strich())
                        val ms = k.meter / k.sekunden.coerceAtLeast(1)
                        addView(reihe().apply {
                            setPadding(0, dp(5f), 0, dp(5f))
                            val fett = k == schnellste
                            addView(fliesstext(k.nummer.toString() + (if (k.meter < 900) "*" else "")).apply { minWidth = dp(34f) })
                            addView(fliesstext(Zahlen.dauer(k.sekunden / 60.0) ?: "").apply {
                                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                            })
                            addView(fliesstext(tempoText(ms).substringBefore(" ")).apply {
                                minWidth = dp(64f)
                                if (fett) { setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(ton) }
                            })
                            addView(fliesstext(k.pulsMittel?.let { Zahlen.ganz(it) } ?: "–").apply { minWidth = dp(48f) })
                            addView(fliesstext(if (k.aufstieg >= 1) "+" + Zahlen.ganz(k.aufstieg) else "–").apply {
                                minWidth = dp(48f); gravity = android.view.Gravity.END
                            })
                        })
                    }
                    if (km.any { it.meter < 900 }) addView(zart("* angefangener Kilometer").apply { setPadding(0, dp(6f), 0, 0) })
                })
            }
        } else if (e.puls.size < 2) {
            inhalt.addView(karte().apply {
                addView(zart("Ohne Strecke und ohne Pulskurve gibt es hier nichts weiter zu zeigen."))
            })
        }
    }

    /**
     * Nach Monaten, eine Karte je Monat.
     *
     * Hundert einzelne Karten waeren hundert Rahmen; was man hier sucht, ist
     * eine Folge. Der Monat ist die Einheit, in der man sich erinnert -
     * "der lange Lauf im August".
     */
    private fun zeigeListe(aeltere: List<TrainingTab.Eintrag>) {
        if (aeltere.isEmpty()) {
            inhalt.addView(karte().apply {
                addView(zart("Noch keine älteren Trainings in den letzten drei Monaten."))
            })
            return
        }
        val zone = ZoneId.systemDefault()
        val monat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
        aeltere.groupBy { YearMonth.from(it.sitzung.startTime.atZone(zone)) }
            .forEach { (ym, liste) ->
                val minuten = liste.sumOf { Sportart.minuten(it.sitzung) }
                inhalt.addView(abschnitt(
                    monat.format(ym).uppercase(Locale.GERMAN) + "  ·  " + liste.size + "×  ·  " +
                        (Zahlen.dauer(minuten.toDouble()) ?: "")
                ))
                val k = karte().apply { setPadding(dp(14f), dp(6f), dp(14f), dp(6f)) }
                liste.forEachIndexed { i, e ->
                    if (i > 0) k.addView(strich())
                    k.addView(TrainingTab.zeile(this, e) {
                        zeige(this, e.sitzung.startTime.epochSecond)
                    })
                }
                inhalt.addView(k)
            }
        inhalt.addView(zart(
            "Die letzten drei Monate. Ältere Trainings stehen weiter in der " +
                "Gesundheitsakte, hier aber nicht."
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f) }
        })
    }

    companion object {
        private const val EXTRA_BEGINN = "beginn"

        /** Die Liste - oder, mit [beginn], das eine Training. */
        fun zeige(ctx: Context, beginn: Long = -1L) {
            ctx.startActivity(
                Intent(ctx, VergangeneActivity::class.java).putExtra(EXTRA_BEGINN, beginn)
            )
        }
    }
}
