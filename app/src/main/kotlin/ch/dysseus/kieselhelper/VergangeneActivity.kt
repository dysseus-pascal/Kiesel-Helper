package ch.dysseus.kieselhelper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import java.time.Duration
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
class VergangeneActivity : KieselActivity() {

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
     * Alles, was mehr ist als der erste Blick - je Sportart anders.
     *
     * DIE KARTE GEHOERT NICHT ZUM YOGA. Jede Art hat ihre eigene Frage: beim
     * Laufen und auf dem Rad, wo es schnell war; beim Wandern, wo es steil
     * war; beim Kraft, wie schnell der Puls in der Pause faellt; beim Yoga,
     * ob man ruhiger wurde; im Becken, ob die Bahnen gleich blieben. Die
     * Seite zeigt die Antwort auf diese Frage und nicht dieselben Bilder fuer
     * alle.
     */
    private suspend fun zeigeDetails(e: TrainingTab.Eintrag) {
        val art = Sportart.von(e.sitzung)
        val ton = farbe(art.farbe)
        val maxpuls = Einstellungen.maxpuls(this)

        // Die Zonen gibt es ueberall, wo ein Puls gemessen wurde.
        if (e.puls.size >= 2) zonenkarte(e, maxpuls)

        when (e.sitzung.exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> kraft(e, ton)
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> yoga(e, ton)
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> schwimmen(e, ton)
            else -> strecke(e, ton, maxpuls)
        }
    }

    private fun zonenkarte(e: TrainingTab.Eintrag, maxpuls: Int) {
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
                    zonen[0] + zonen[1] >= summe / 2 -> "Überwiegend locker — Erholung, oder der Puls blieb unten."
                    else -> "Gemischt über die Zonen."
                }
            ).apply { setPadding(0, dp(8f), 0, 0) })
        })
    }

    /** Der Puls ueber die Zeit, mit senkrechten Baendern (Saetze, Bahnen). */
    private fun pulsUeberZeit(e: TrainingTab.Eintrag, ton: Int, baender: List<Triple<Double, Double, Int>>, text: String) {
        if (e.puls.size < 2) return
        val punkte = e.puls.map { Trainingsanalyse.Verlaufspunkt(it.sekunde.toDouble(), it.bpm.toDouble()) }
        inhalt.addView(abschnitt("PULS ÜBER DIE ZEIT"))
        inhalt.addView(karte().apply {
            addView(zart(text))
            addView(streckenverlauf(punkte, ton, "bpm", zeitachse = true, xBaender = baender))
        })
    }

    // --- Laufen, Bike, MTB, Wandern -------------------------------------------

    private fun strecke(e: TrainingTab.Eintrag, ton: Int, maxpuls: Int) {
        val rad = e.sitzung.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        val wandern = e.sitzung.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_HIKING ||
            e.sitzung.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        val strecke = Trainingsanalyse.strecke(e.punkte)
        if (strecke.size < 2) {
            pulsUeberZeit(e, ton, emptyList(), "Ohne Strecke bleibt die Zeit die Achse")
            if (e.puls.size < 2) inhalt.addView(karte().apply {
                addView(zart("Ohne Strecke und ohne Pulskurve gibt es hier nichts weiter zu zeigen."))
            })
            return
        }
        val tempoText: (Double) -> String = { ms ->
            if (rad) Zahlen.eine(ms * 3.6) + " km/h"
            else if (ms > 0.2) { val sek = 1000 / ms; String.format("%d:%02d /km", (sek / 60).toInt(), (sek % 60).toInt()) } else "–"
        }
        val hoehe = Trainingsanalyse.hoeheUeberStrecke(strecke)
        val mitHoehe = hoehe.size >= 2 && hoehe.maxOf { it.wert } - hoehe.minOf { it.wert } >= 10

        // BEIM WANDERN ZUERST DIE HOEHE: dort ist sie die Geschichte der Tour.
        if (wandern && mitHoehe) hoehenkarte(e, hoehe, strecke)

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
        inhalt.addView(abschnitt("TEMPO"))
        inhalt.addView(karte().apply {
            val mittel = strecke.last().meter / strecke.last().sekunde.coerceAtLeast(1)
            val spitze = strecke.maxOf { it.tempo }
            addView(reihe().apply {
                addView(messwert("Schnitt", tempoText(mittel).substringBefore(" "), tempoText(mittel).substringAfter(" ", ""), 0f, false))
                addView(messwert("Spitze", tempoText(spitze).substringBefore(" "), tempoText(spitze).substringAfter(" ", ""), 0f, false))
                if (wandern && mitHoehe) {
                    val hm = Spur.hoehenmeter(e.punkte) / (strecke.last().sekunde / 3600.0)
                    addView(messwert("Aufstieg", Zahlen.ganz(hm), "m/h", 0f, false))
                } else {
                    addView(messwert("Bewegt", Zahlen.dauer(strecke.last().sekunde / 60.0), "", 0f, false))
                }
            })
            addView(zart(if (wandern) "Über die Strecke" else "Über die Strecke, gefärbt wie auf der Karte").apply { setPadding(0, dp(10f), 0, 0) })
            addView(streckenverlauf(
                tempo, ton, "km/h",
                farbeJeWert = if (wandern) null else { kmh -> Zonenfarben.tempofarbe((((kmh / 3.6) - langsam) / (schnell - langsam)).toFloat()) },
                flaeche = wandern, nachkomma = 0,
            ))
        })

        // --- Die Karte: Tempo als Farbe - beim Wandern die Steigung ---
        if (wandern && mitHoehe) {
            val steigung = Trainingsanalyse.steigung(strecke)
            val mitSteigung = strecke.mapIndexed { i, p -> p.copy(tempo = steigung[i]) }
            inhalt.addView(abschnitt("STEIGUNG AUF DER KARTE"))
            inhalt.addView(karte().apply {
                addView(zart("Blau bergab, grün flach, rot bergauf"))
                addView(farbkarte(mitSteigung, karten, { it.tempo }, -15.0, 15.0))
                addView(tempolegende("−15 % bergab", "+15 % bergauf"))
            })
        } else {
            inhalt.addView(abschnitt("TEMPO AUF DER KARTE"))
            inhalt.addView(karte().apply {
                addView(zart("Blau, wo es zäh war — rot, wo es lief"))
                addView(tempokarte(strecke, karten))
                addView(tempolegende(tempoText(langsam), tempoText(schnell)))
            })
        }

        if (!wandern && mitHoehe) hoehenkarte(e, hoehe, strecke)

        // --- Kilometer ---
        val km = Trainingsanalyse.kilometer(strecke, e.puls)
        if (km.size >= 2) kilometerkarte(km, rad, ton, tempoText)
    }

    private fun hoehenkarte(e: TrainingTab.Eintrag, hoehe: List<Trainingsanalyse.Verlaufspunkt>, strecke: List<Trainingsanalyse.Streckenpunkt>) {
        inhalt.addView(abschnitt("HÖHENPROFIL"))
        inhalt.addView(karte().apply {
            addView(reihe().apply {
                addView(messwert("Aufstieg", Zahlen.ganz(Spur.hoehenmeter(e.punkte)), "m", 0f, false))
                addView(messwert("Tiefster", Zahlen.ganz(hoehe.minOf { it.wert }), "m ü. M.", 0f, false))
                addView(messwert("Höchster", Zahlen.ganz(hoehe.maxOf { it.wert }), "m ü. M.", 0f, false))
            })
            val steil = Trainingsanalyse.steigung(strecke).maxOrNull() ?: 0.0
            if (steil >= 5) addView(zart("Steilste Stelle " + Zahlen.ganz(steil) + " %").apply { setPadding(0, dp(6f), 0, 0) })
            addView(streckenverlauf(hoehe, farbe(R.color.sport_wandern), "m", nachkomma = 0, hoehe = 130f))
        })
    }

    private fun kilometerkarte(km: List<Trainingsanalyse.Kilometer>, rad: Boolean, ton: Int, tempoText: (Double) -> String) {
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

    // --- Kraft ------------------------------------------------------------------

    /**
     * Beim Kraft zaehlt nicht die Strecke, sondern der Satz - und die Pause
     * danach: wie schnell der Puls wieder faellt, sagt mehr ueber die Form
     * als jede Zahl im Satz.
     */
    private fun kraft(e: TrainingTab.Eintrag, ton: Int) {
        val start = e.sitzung.startTime
        val saetze = e.sitzung.segments.filter { it.repetitions > 0 }
            .map { Triple(Duration.between(start, it.startTime).seconds, Duration.between(start, it.endTime).seconds, it.repetitions) }
        val pausen = e.sitzung.segments.filter { it.segmentType == ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST }
            .map { Duration.between(start, it.startTime).seconds to Duration.between(start, it.endTime).seconds }

        pulsUeberZeit(e, ton, saetze.map { Triple(it.first.toDouble(), it.second.toDouble(), ton) },
            "Die Bänder sind die Sätze — dazwischen fällt der Puls")

        if (saetze.isEmpty()) {
            if (e.puls.size < 2) inhalt.addView(karte().apply {
                addView(zart("Die Uhr hat keine Sätze gezählt — vielleicht bewegte sich das Handgelenk zu wenig."))
            })
            return
        }

        inhalt.addView(abschnitt("SÄTZE"))
        inhalt.addView(karte().apply {
            val erholung = Trainingsanalyse.erholung(e.puls, pausen)
            val pausenSek = pausen.map { (a, b) -> (b - a).toDouble() }
            addView(reihe().apply {
                addView(messwert("Sätze", saetze.size.toString(), "", 0f, false))
                addView(messwert("Wdh.", saetze.sumOf { it.third }.toString(), "gesamt", 0f, false))
                addView(messwert("Pause Ø", pausenSek.takeIf { it.isNotEmpty() }?.let { Zahlen.ganz(it.average()) }, "s", 0f, false))
            })
            if (erholung != null) {
                addView(zart(
                    "In den Pausen fällt der Puls um " + Zahlen.ganz(erholung) + " Schläge je Minute" +
                        when {
                            erholung >= 20 -> " — schnelle Erholung."
                            erholung >= 10 -> " — solide Erholung."
                            else -> " — der Puls bleibt oben; längere Pausen helfen."
                        }
                ).apply { setPadding(0, dp(8f), 0, dp(4f)) })
            }
            addView(reihe().apply {
                setPadding(0, dp(6f), 0, 0)
                addView(zart("Satz").apply { minWidth = dp(40f) })
                addView(zart("Wdh.").apply { minWidth = dp(48f) })
                addView(zart("Dauer").apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                addView(zart("Puls Ø").apply { minWidth = dp(56f) })
                addView(zart("Pause").apply { minWidth = dp(56f); gravity = android.view.Gravity.END })
            })
            val meiste = saetze.maxOf { it.third }
            saetze.forEachIndexed { i, (von, bis, wdh) ->
                addView(strich())
                val pause = pausen.firstOrNull { it.first >= bis - 2 }?.let { it.second - it.first }
                addView(reihe().apply {
                    setPadding(0, dp(5f), 0, dp(5f))
                    addView(fliesstext((i + 1).toString()).apply { minWidth = dp(40f) })
                    addView(fliesstext(wdh.toString()).apply {
                        minWidth = dp(48f)
                        if (wdh == meiste) { setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(ton) }
                    })
                    addView(fliesstext((bis - von).toString() + " s").apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(fliesstext(Trainingsanalyse.pulsMittel(e.puls, von, bis)?.let { Zahlen.ganz(it) } ?: "–").apply { minWidth = dp(56f) })
                    addView(fliesstext(pause?.let { "$it s" } ?: "–").apply { minWidth = dp(56f); gravity = android.view.Gravity.END })
                })
            }
        })
    }

    // --- Yoga --------------------------------------------------------------------

    /**
     * Beim Yoga ist die Frage, ob man ruhiger wurde: der Puls am Anfang gegen
     * den am Ende, der tiefste Wert, und die HRV, die die Uhr dabei gemessen
     * hat.
     */
    private suspend fun yoga(e: TrainingTab.Eintrag, ton: Int) {
        pulsUeberZeit(e, ton, emptyList(), "Ruhig ist gut — die Kurve soll fallen, nicht steigen")

        val hrv = try {
            Akte(this).bereit()?.readRecords(
                ReadRecordsRequest(
                    HeartRateVariabilityRmssdRecord::class,
                    TimeRangeFilter.between(e.sitzung.startTime, e.sitzung.endTime.plusSeconds(600)),
                )
            )?.records?.maxByOrNull { it.time }
        } catch (ex: Exception) {
            null
        }

        inhalt.addView(abschnitt("RUHE"))
        inhalt.addView(karte().apply {
            if (e.puls.size >= 2) {
                val ende = e.puls.last().sekunde
                val anfang = Trainingsanalyse.pulsMittel(e.puls, 0, 120)
                val schluss = Trainingsanalyse.pulsMittel(e.puls, ende - 120, ende)
                val tiefster = e.puls.minOf { it.bpm }
                addView(reihe().apply {
                    addView(messwert("Anfang", anfang?.let { Zahlen.ganz(it) }, "bpm", 0f, false))
                    addView(messwert("Ende", schluss?.let { Zahlen.ganz(it) }, "bpm", 0f, false))
                    addView(messwert("Tiefster", tiefster.toString(), "bpm", 0f, false))
                })
                if (anfang != null && schluss != null) {
                    val d = anfang - schluss
                    addView(zart(
                        when {
                            d >= 8 -> "Der Puls ist um " + Zahlen.ganz(d) + " Schläge gefallen — die Stunde hat beruhigt."
                            d >= 3 -> "Der Puls ist leicht gefallen, um " + Zahlen.ganz(d) + " Schläge."
                            d > -3 -> "Der Puls blieb, wo er war."
                            else -> "Der Puls ist gestiegen — eine fordernde Stunde."
                        }
                    ).apply { setPadding(0, dp(8f), 0, 0) })
                }
            }
            if (hrv != null) {
                addView(reihe().apply {
                    setPadding(0, dp(10f), 0, 0)
                    addView(messwert("HRV", Zahlen.ganz(hrv.heartRateVariabilityMillis), "ms RMSSD", 0f, false))
                })
                addView(zart(
                    "Die Herzratenvariabilität aus dieser Stunde, von der Uhr gemessen. Höher heisst " +
                        "entspannter; vergleichbar ist sie mit den Nachtwerten von Herzintervall."
                ).apply { setPadding(0, dp(6f), 0, 0) })
            } else if (e.puls.size < 2) {
                addView(zart("Kein Puls und keine HRV zu dieser Stunde."))
            } else {
                addView(zart("Keine HRV zu dieser Stunde — die Uhr misst sie erst seit Kieselsport 0.9.0.").apply { setPadding(0, dp(8f), 0, 0) })
            }
        })
    }

    // --- Schwimmen ---------------------------------------------------------------

    /**
     * Im Becken zaehlt die Bahn: wie gleich sie blieben, und wo die Kraft
     * nachliess. Der Puls laeuft darunter mit, jede zweite Bahn als Band.
     */
    private fun schwimmen(e: TrainingTab.Eintrag, ton: Int) {
        val start = e.sitzung.startTime
        val bahnen = e.sitzung.laps.map {
            Triple(Duration.between(start, it.startTime).seconds, Duration.between(start, it.endTime).seconds, it.length?.inMeters)
        }
        pulsUeberZeit(e, ton, bahnen.filterIndexed { i, _ -> i % 2 == 0 }
            .map { Triple(it.first.toDouble(), it.second.toDouble(), ton) },
            "Jede zweite Bahn als Band")

        if (bahnen.isEmpty()) {
            if (e.puls.size < 2) inhalt.addView(karte().apply {
                addView(zart("Die Uhr hat keine Bahnen gezählt — war der Kompass bereit?"))
            })
            return
        }
        val zeiten = bahnen.map { (it.second - it.first).toDouble() }
        val laenge = bahnen.firstNotNullOfOrNull { it.third } ?: 0.0
        val meter = bahnen.sumOf { it.third ?: laenge }
        val schwankung = Trainingsanalyse.schwankung(zeiten)
        val je100 = if (meter > 0) zeiten.sum() / meter * 100 else 0.0

        inhalt.addView(abschnitt("BAHNEN"))
        inhalt.addView(karte().apply {
            addView(reihe().apply {
                addView(messwert("Bahnen", bahnen.size.toString(), if (laenge > 0) "à " + Zahlen.ganz(laenge) + " m" else "", 0f, false))
                addView(messwert("Strecke", if (meter > 0) Zahlen.ganz(meter) else null, "m", 0f, false))
                addView(messwert("je 100 m", if (je100 > 0) String.format("%d:%02d", (je100 / 60).toInt(), (je100 % 60).toInt()) else null, "min", 0f, false))
            })
            addView(zart(
                "Bahnzeiten schwanken um ±" + Zahlen.ganz(schwankung) + " s" +
                    when {
                        schwankung <= 3 -> " — sehr gleichmässig."
                        schwankung <= 8 -> " — gleichmässig."
                        else -> " — ungleich; hinten wurde es langsamer oder vorne war es zu schnell."
                    }
            ).apply { setPadding(0, dp(8f), 0, dp(4f)) })
            // Jede Bahn ein Balken, gefaerbt vom Schnellsten (rot) zum Langsamsten (blau).
            val schnellste = zeiten.minOrNull() ?: 0.0
            val langsamste = zeiten.maxOrNull() ?: 1.0
            addView(streckenverlauf(
                zeiten.mapIndexed { i, z -> Trainingsanalyse.Verlaufspunkt(i + 1.0, z) },
                ton, "s je Bahn",
                farbeJeWert = { z -> Zonenfarben.tempofarbe(((langsamste - z) / (langsamste - schnellste).coerceAtLeast(1.0)).toFloat()) },
                flaeche = false, nachkomma = 0, hoehe = 140f, zeitachse = true,
            ))
            addView(zart("Bahn für Bahn — rot die schnellen, blau die langsamen. Die Achse zählt Bahnen, nicht Minuten.").apply { setPadding(0, dp(4f), 0, 0) })
        })
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
