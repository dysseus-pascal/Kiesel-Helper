package ch.dysseus.kieselhelper

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Was die Gesundheitsakte weiss - gelesen, nicht geschrieben.
 *
 * WARUM DIESE APP DAS ZEIGT. Die Akte selbst ist ueberladen: sie kann alles und
 * zeigt darum nichts zuerst. Hier stehen die Zahlen, die taeglich zaehlen, auf
 * einem Schirm - und dieselben im Widget, ohne dass man eine App oeffnen muss.
 *
 * KEIN WERT WIRD ERFUNDEN. Steht nichts in der Akte, bleibt das Feld leer und
 * sagt das auch. Eine Null waere eine Behauptung: "du bist heute keinen Schritt
 * gegangen" ist etwas anderes als "niemand hat Schritte eingetragen".
 *
 * Eine einzige Ausnahme, und die ist gekennzeichnet: fehlt ein eingetragener
 * Ruhepuls, wird er aus dem naechtlichen Tiefstwert geschaetzt und mit einem
 * Ungefaehr-Zeichen gezeigt. Siehe [ruhepuls].
 */
class Gesundheit(private val context: Context) {

    /**
     * Ein einzelner Wert. `null` heisst: nichts in der Akte, nicht Null.
     *
     * `ziel` ist nur gesetzt, wo es eines gibt. Ein Balken braucht ein Ziel;
     * fuer einen Ruhepuls gibt es keins, und ein Balken ohne Ziel waere eine
     * Behauptung.
     */
    data class Wert(
        val name: String,
        val zahl: Double?,
        val einheit: String,
        val ziel: Double? = null,
        val geschaetzt: Boolean = false,
    ) {
        val da: Boolean get() = zahl != null
        val anteil: Float
            get() {
                val z = zahl ?: return 0f
                val t = ziel ?: return 0f
                if (t <= 0) return 0f
                return (z / t).coerceIn(0.0, 1.0).toFloat()
            }
    }

    /** Ein Tag im Wochenbild. `zahl == null` heisst: an dem Tag nichts. */
    data class Tageswert(val tag: LocalDate, val zahl: Double?)

    /**
     * Die Schlafphasen einer Nacht, in Minuten.
     *
     * WACH ZAEHLT NICHT ZUM SCHLAF, steht aber im Balken - die Unterbrechungen
     * sind der halbe Sinn der Aufteilung. Was die Uhr nicht unterscheiden kann,
     * kommt als "leicht" herein; das ist die Voreinstellung der Akte fuer
     * blosses "Schlaf", nicht unsere Erfindung.
     */
    data class Phasen(
        val tief: Double,
        val rem: Double,
        val leicht: Double,
        val wach: Double,
    ) {
        val summe: Double get() = tief + rem + leicht + wach
        val da: Boolean get() = summe > 0
    }

    /** Ein Punkt im Tagesverlauf: Minute seit Mitternacht, Wert. */
    data class Punkt(val minute: Int, val wert: Double)

    data class Stand(
        val schritte: Wert,
        val distanz: Wert,
        val kalorien: Wert,
        val aktiv: Wert,
        val wasser: Wert,
        val schlaf: Wert,
        val ruhepuls: Wert,
        val puls: Wert,
        val pulsHoch: Wert,
        val pulsTief: Wert,
        val hrv: Wert,
        val phasen: Phasen?,
        val wocheSchritte: List<Tageswert>,
        val wocheSchlaf: List<Tageswert>,
        val wocheWasser: List<Tageswert>,
        val pulsverlauf: List<Punkt>,
        val gelesen: Instant,
    )

    /** Was von einer Satzart tatsaechlich in der Akte steht, und von wem. */
    data class Befund(val name: String, val anzahl: Int, val quellen: Set<String>)

    companion object {
        /** Die Berechtigungen zum LESEN. Getrennt von denen zum Schreiben. */
        val BERECHTIGUNGEN: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        )

        /**
         * Tagesziele.
         *
         * NUR DAS WASSERZIEL IST ECHT - es kommt aus Drinktervall, das die
         * eingestellte Glaeserzahl mitschickt. Die uebrigen drei sind
         * Hausnummern, wie sie jede Gesundheits-App benutzt. Wer andere will,
         * aendert sie hier; eine Einstellung dafuer waere ein Bildschirm mehr
         * fuer eine Zahl, die man einmal im Leben setzt.
         */
        const val ZIEL_SCHRITTE = 10000.0
        const val ZIEL_AKTIV_MIN = 30.0
        const val ZIEL_SCHLAF_H = 8.0

        /** Sieben Tage im Wochenbild - eine Woche liest man auf einen Blick. */
        const val TAGE = 7

        /**
         * Ab wann eine Nacht zaehlt.
         *
         * Wer um 23 Uhr ins Bett geht, hat seinen Schlaf am Vortag begonnen -
         * ein Fenster ab Mitternacht schnitte ihn in zwei Haelften. 18 Uhr ist
         * die Grenze, an der kein Mensch sinnvoll beides verwechselt.
         */
        val NACHT_AB: LocalTime = LocalTime.of(18, 0)
    }

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * Alles auf einmal holen.
     *
     * Ein Aufruf je Kennzahl waere sauberer zu lesen, aber die Akte antwortet
     * traege; gebuendelt geht es in einem Durchgang. Faellt eine Abfrage aus,
     * fehlt NUR ihr Wert - die uebrigen stehen trotzdem da.
     */
    suspend fun lies(): Stand? {
        val klient = Akte(context).bereit() ?: return null
        val heute = LocalDate.now(zone)
        val jetzt = LocalDateTime.now(zone)
        val tag = TimeRangeFilter.between(heute.atStartOfDay(), jetzt)
        val nacht = nachtfenster(heute)

        val summen = fange("Tagessummen") {
            klient.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                        HydrationRecord.VOLUME_TOTAL,
                        // Tageshoch und Tagestief des Pulses. Sie kosten hier
                        // nichts extra - dieselbe Abfrage, zwei Kennzahlen
                        // mehr - und beantworten, was ein Mittelwert nie sagt:
                        // wie weit der Tag ausgeschlagen hat.
                        HeartRateRecord.BPM_MAX,
                        HeartRateRecord.BPM_MIN,
                    ),
                    timeRangeFilter = tag,
                )
            )
        }

        val schlafMin = fange("Schlaf") {
            klient.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = nacht,
                )
            )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toDouble()
        }

        val sitzungen = fange("Schlafphasen") {
            klient.readRecords(ReadRecordsRequest(SleepSessionRecord::class, nacht)).records
        } ?: emptyList()

        val stand = Stand(
            schritte = Wert("Schritte", summen?.get(StepsRecord.COUNT_TOTAL)?.toDouble(),
                            "", ZIEL_SCHRITTE),
            distanz = Wert("Distanz",
                           summen?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers, "km"),
            kalorien = Wert("Aktive Kalorien",
                            summen?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                                ?.inKilocalories, "kcal"),
            aktiv = Wert("Aktiv",
                         summen?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
                             ?.toMinutes()?.toDouble(), "min", ZIEL_AKTIV_MIN),
            wasser = Wert("Wasser",
                          summen?.get(HydrationRecord.VOLUME_TOTAL)?.inMilliliters,
                          "ml", wasserziel()),
            schlaf = Wert("Schlaf", schlafMin, "min", ZIEL_SCHLAF_H * 60),
            ruhepuls = ruhepuls(klient, nacht),
            puls = Wert("Puls", letzterPuls(klient, tag), "bpm"),
            pulsHoch = Wert("Puls hoch", summen?.get(HeartRateRecord.BPM_MAX)?.toDouble(), "bpm"),
            pulsTief = Wert("Puls tief", summen?.get(HeartRateRecord.BPM_MIN)?.toDouble(), "bpm"),
            hrv = Wert("HRV", letzteHrv(klient), "ms"),
            phasen = phasenAus(sitzungen),
            wocheSchritte = wocheSchritteWasser(klient, StepsRecord.COUNT_TOTAL),
            wocheWasser = wocheSchritteWasser(klient, HydrationRecord.VOLUME_TOTAL),
            wocheSchlaf = wocheSchlaf(klient, heute),
            pulsverlauf = pulsverlauf(klient, tag),
            gelesen = Instant.now(),
        )

        // JEDES LESEN IST EIN EINTRAG. Die Akte selbst vergisst; was hier
        // nicht in die eigene Tabelle faellt, ist in einem Monat als
        // Mittwoch nicht mehr nachweisbar.
        merke(stand, heute)
        return stand
    }

    /** Den Tagesstand in die eigene Tabelle schreiben. */
    private suspend fun merke(stand: Stand, tag: LocalDate) = withContext(Dispatchers.IO) {
        Speicher(context).merke(tag, mapOf(
            "schritte" to stand.schritte.zahl,
            "distanz" to stand.distanz.zahl,
            "kalorien" to stand.kalorien.zahl,
            "aktiv" to stand.aktiv.zahl,
            "wasser" to stand.wasser.zahl,
            "schlaf" to stand.schlaf.zahl,
            "tief" to stand.phasen?.tief,
            "rem" to stand.phasen?.rem,
            "leicht" to stand.phasen?.leicht,
            "wach" to stand.phasen?.wach,
            // GEMESSEN UND GESCHAETZT IN GETRENNTE SPALTEN. Ein aus dem
            // Nachttief hergeleiteter Ruhepuls darf spaeter nicht als
            // eingetragener durchgehen - in einem Jahresmittel sieht man
            // ihm nicht mehr an, woher er kam.
            "ruhepuls" to stand.ruhepuls.zahl.takeUnless { stand.ruhepuls.geschaetzt },
            "puls_min" to stand.ruhepuls.zahl.takeIf { stand.ruhepuls.geschaetzt },
            "puls_hoch" to stand.pulsHoch.zahl,
            "puls_tief" to stand.pulsTief.zahl,
            "hrv" to stand.hrv.zahl,
        ))
    }


    /**
     * Die Vergangenheit einmal aus der Akte holen.
     *
     * BEIM ERSTEN START IST DIE EIGENE TABELLE LEER, die Akte aber nicht: dort
     * liegen meist die letzten dreissig Tage. Sie einmal abzuschreiben ist der
     * Unterschied zwischen "in drei Wochen sagt dir die App etwas" und "sie
     * sagt es jetzt".
     *
     * DREI ABFRAGEN FUER DREISSIG TAGE, nicht dreissig mal drei: die
     * Tagessummen kommen als Eimer zurueck, Schlaf und HRV als Saetze, die
     * hier selbst auf Tage verteilt werden.
     *
     * Laeuft jedes Mal ueber das ganze Fenster und nicht nur ueber die Luecken.
     * Das kostet ein paar hundert Millisekunden im Hintergrund und erspart die
     * Frage, ob ein Tag, der gestern halb leer eingetragen wurde, je wieder
     * angefasst wird - [Speicher.merke] ueberschreibt nichts mit nichts.
     */
    suspend fun nachtragen(tage: Int = 30) {
        val klient = Akte(context).bereit() ?: return
        val heute = LocalDate.now(zone)
        val von = heute.minusDays(tage.toLong())
        val jetzt = LocalDateTime.now(zone)

        val eimer = fange("Nachtragen: Tagessummen") {
            klient.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                        HydrationRecord.VOLUME_TOTAL,
                        RestingHeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MIN,
                        HeartRateRecord.BPM_MAX,
                    ),
                    timeRangeFilter = TimeRangeFilter.between(von.atStartOfDay(), jetzt),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
        } ?: emptyList()

        val sitzungen = fange("Nachtragen: Schlaf") {
            klient.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    TimeRangeFilter.between(von.minusDays(1).atTime(NACHT_AB), jetzt),
                )
            ).records
        } ?: emptyList()

        val hrvSaetze = fange("Nachtragen: HRV") {
            klient.readRecords(
                ReadRecordsRequest(
                    HeartRateVariabilityRmssdRecord::class,
                    TimeRangeFilter.between(von.atStartOfDay(), jetzt),
                )
            ).records
        } ?: emptyList()

        // Jede Schlafsitzung gehoert zu EINER Nacht, und die Nacht heisst nach
        // dem Morgen: wer um 23 Uhr einschlaeft, hat in der Nacht auf morgen
        // geschlafen. Ohne diese Zuordnung landete dieselbe Nacht je nach
        // Einschlafzeit mal auf dem einen, mal auf dem anderen Tag.
        val naechte = HashMap<LocalDate, MutableList<SleepSessionRecord>>()
        sitzungen.forEach { sitzung ->
            val beginn = LocalDateTime.ofInstant(sitzung.startTime, zone)
            val nacht = if (beginn.toLocalTime() >= NACHT_AB) beginn.toLocalDate().plusDays(1)
                        else beginn.toLocalDate()
            naechte.getOrPut(nacht) { mutableListOf() }.add(sitzung)
        }

        val hrvNachTag = HashMap<LocalDate, Double>()
        hrvSaetze.sortedBy { it.time }.forEach { satz ->
            hrvNachTag[LocalDateTime.ofInstant(satz.time, zone).toLocalDate()] =
                satz.heartRateVariabilityMillis
        }

        val speicher = Speicher(context)
        withContext(Dispatchers.IO) {
            eimer.forEach { e ->
                val tag = e.startTime.toLocalDate()
                val nacht = naechte[tag].orEmpty()
                val phasen = phasenAus(nacht)
                val geschlafen = nacht.sumOf {
                    Duration.between(it.startTime, it.endTime).toMinutes()
                }.toDouble().takeIf { it > 0 }

                speicher.merke(tag, mapOf(
                    "schritte" to e.result[StepsRecord.COUNT_TOTAL]?.toDouble(),
                    "distanz" to e.result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers,
                    "kalorien" to e.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                        ?.inKilocalories,
                    "aktiv" to e.result[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]
                        ?.toMinutes()?.toDouble(),
                    "wasser" to e.result[HydrationRecord.VOLUME_TOTAL]?.inMilliliters,
                    "schlaf" to geschlafen,
                    "tief" to phasen?.tief,
                    "rem" to phasen?.rem,
                    "leicht" to phasen?.leicht,
                    "wach" to phasen?.wach,
                    "ruhepuls" to e.result[RestingHeartRateRecord.BPM_AVG]?.toDouble(),
                    // Beim Nachtragen ist das Tagestief zugleich die beste
                    // Schaetzung fuer einen fehlenden Ruhepuls - ein
                    // eigenes Nachtfenster je Tag waere dreissig Abfragen
                    // fuer eine Zahl, die sich kaum unterscheidet.
                    "puls_min" to e.result[HeartRateRecord.BPM_MIN]?.toDouble(),
                    "puls_tief" to e.result[HeartRateRecord.BPM_MIN]?.toDouble(),
                    "puls_hoch" to e.result[HeartRateRecord.BPM_MAX]?.toDouble(),
                    "hrv" to hrvNachTag[tag],
                ))
            }
        }
    }

    /**
     * Der Ruhepuls - in drei Anlaeufen.
     *
     * DIE UHR ZEIGT IHN, die Akte hatte ihn trotzdem nicht: nicht jede App
     * schreibt einen `RestingHeartRateRecord`, manche rechnen ihn nur fuer die
     * eigene Anzeige aus. Deshalb der Weg von hinten:
     *
     *  1. der eingetragene Ruhepuls von heute oder gestern;
     *  2. sonst der juengste eingetragene aus einer Woche - ein Ruhepuls von
     *     vorgestern ist naeher an der Wahrheit als gar keiner;
     *  3. sonst der TIEFSTE Puls der Nacht. Das ist nicht dasselbe, und es
     *     wird auch nicht so getan: der Wert kommt mit `geschaetzt = true`
     *     zurueck und steht mit einem Ungefaehr-Zeichen da.
     */
    private suspend fun ruhepuls(
        klient: HealthConnectClient,
        nacht: TimeRangeFilter,
    ): Wert {
        val jetzt = LocalDateTime.now(zone)
        val gestern = LocalDate.now(zone).minusDays(1).atStartOfDay()

        fange("Ruhepuls") {
            klient.aggregate(
                AggregateRequest(
                    metrics = setOf(RestingHeartRateRecord.BPM_AVG),
                    timeRangeFilter = TimeRangeFilter.between(gestern, jetzt),
                )
            )[RestingHeartRateRecord.BPM_AVG]
        }?.let { return Wert("Ruhepuls", it.toDouble(), "bpm") }

        fange("Ruhepuls (Woche)") {
            klient.readRecords(
                ReadRecordsRequest(
                    RestingHeartRateRecord::class,
                    TimeRangeFilter.between(
                        Instant.now().minus(Duration.ofDays(7)), Instant.now()
                    ),
                )
            ).records.maxByOrNull { it.time }?.beatsPerMinute
        }?.let { return Wert("Ruhepuls", it.toDouble(), "bpm") }

        fange("Ruhepuls (aus Nachtpuls)") {
            klient.aggregate(
                AggregateRequest(
                    metrics = setOf(HeartRateRecord.BPM_MIN),
                    timeRangeFilter = nacht,
                )
            )[HeartRateRecord.BPM_MIN]
        }?.let { return Wert("Ruhepuls", it.toDouble(), "bpm", geschaetzt = true) }

        return Wert("Ruhepuls", null, "bpm")
    }

    /**
     * Die Phasen einer Nacht zusammenzaehlen.
     *
     * Ohne Phasen in der Akte gibt es hier nichts zu holen - dann bleibt es
     * bei der blossen Dauer, und der Balken entfaellt. Eine Nacht in vier
     * gleiche Teile zu malen, weil es huebscher aussieht, waere gelogen.
     */
    private fun phasenAus(sitzungen: List<SleepSessionRecord>): Phasen? {
        var tief = 0L; var rem = 0L; var leicht = 0L; var wach = 0L
        sitzungen.forEach { sitzung ->
            sitzung.stages.forEach { abschnitt ->
                val min = Duration.between(abschnitt.startTime, abschnitt.endTime).toMinutes()
                when (abschnitt.stage) {
                    SleepSessionRecord.STAGE_TYPE_DEEP -> tief += min
                    SleepSessionRecord.STAGE_TYPE_REM -> rem += min
                    SleepSessionRecord.STAGE_TYPE_LIGHT,
                    SleepSessionRecord.STAGE_TYPE_SLEEPING -> leicht += min
                    SleepSessionRecord.STAGE_TYPE_AWAKE,
                    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> wach += min
                    else -> Unit
                }
            }
        }
        val p = Phasen(tief.toDouble(), rem.toDouble(), leicht.toDouble(), wach.toDouble())
        return if (p.da) p else null
    }

    /**
     * Sieben Tage einer Tagessumme.
     *
     * Ein Aufruf, sieben Eimer. Tage ohne Eintrag fehlen in der Antwort und
     * werden hier als `null` ergaenzt - eine Luecke im Balkenbild ist eine
     * Aussage, eine Null waere eine andere.
     */
    private suspend fun wocheSchritteWasser(
        klient: HealthConnectClient,
        metrik: androidx.health.connect.client.aggregate.AggregateMetric<*>,
    ): List<Tageswert> {
        val heute = LocalDate.now(zone)
        val eimer = fange("Woche") {
            klient.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL, HydrationRecord.VOLUME_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(
                        heute.minusDays((TAGE - 1).toLong()).atStartOfDay(),
                        LocalDateTime.now(zone),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
        } ?: return emptyList()

        val nach = HashMap<LocalDate, Double?>()
        eimer.forEach { e ->
            nach[e.startTime.toLocalDate()] = when (metrik) {
                StepsRecord.COUNT_TOTAL -> e.result[StepsRecord.COUNT_TOTAL]?.toDouble()
                else -> e.result[HydrationRecord.VOLUME_TOTAL]?.inMilliliters
            }
        }
        return (0 until TAGE).map { i ->
            val t = heute.minusDays((TAGE - 1 - i).toLong())
            Tageswert(t, nach[t])
        }
    }

    /**
     * Sieben Naechte.
     *
     * EIGENE ABFRAGE JE NACHT, nicht ein Gruppenaufruf: eine Nacht laeuft von
     * 18 Uhr bis 18 Uhr, und Tageseimer ab Mitternacht schnitten jede Nacht in
     * zwei Haelften. Sieben kleine Fragen sind der Preis fuer sieben richtige
     * Antworten.
     */
    private suspend fun wocheSchlaf(
        klient: HealthConnectClient,
        heute: LocalDate,
    ): List<Tageswert> = (0 until TAGE).map { i ->
        val tag = heute.minusDays((TAGE - 1 - i).toLong())
        Tageswert(tag, fange("Schlafwoche") {
            klient.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = nachtfenster(tag),
                )
            )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toDouble()
        })
    }

    /**
     * Der Pulsverlauf des Tages, auf Fuenfminutenmittel gedampft.
     *
     * Eine Uhr misst im Minutentakt; tausend Punkte auf zweihundert Bildpunkte
     * zu zeichnen ergibt einen Tintenfleck, keine Linie. Gemittelt statt
     * ausgeduennt, sonst haengt die Form davon ab, welcher Punkt zufaellig
     * ueberlebt.
     */
    private suspend fun pulsverlauf(
        klient: HealthConnectClient,
        tag: TimeRangeFilter,
    ): List<Punkt> {
        val saetze = fange("Pulsverlauf") {
            klient.readRecords(ReadRecordsRequest(HeartRateRecord::class, tag)).records
        } ?: return emptyList()

        val eimer = HashMap<Int, MutableList<Double>>()
        saetze.forEach { satz ->
            satz.samples.forEach { probe ->
                val z = LocalDateTime.ofInstant(probe.time, zone)
                val fach = (z.hour * 60 + z.minute) / 5
                eimer.getOrPut(fach) { mutableListOf() }
                    .add(probe.beatsPerMinute.toDouble())
            }
        }
        return eimer.entries.sortedBy { it.key }
            .map { (fach, werte) -> Punkt(fach * 5, werte.average()) }
    }

    /**
     * Was tatsaechlich in der Akte steht - und von welcher App.
     *
     * DAS IST DIE ANTWORT AUF "warum ist das Feld leer". Ohne sie raet man:
     * fehlt die Erlaubnis, fehlt die Satzart, oder schreibt schlicht niemand?
     * Zwei Tage sind das Fenster - laenger gefragt haette jede Luecke
     * zugedeckt.
     */
    suspend fun pruefe(): List<Befund> {
        val klient = Akte(context).bereit() ?: return emptyList()
        val fenster = TimeRangeFilter.between(
            Instant.now().minus(Duration.ofDays(2)), Instant.now()
        )
        return listOf(
            "Schritte" to StepsRecord::class,
            "Distanz" to DistanceRecord::class,
            "Kalorien" to ActiveCaloriesBurnedRecord::class,
            "Training" to ExerciseSessionRecord::class,
            "Wasser" to HydrationRecord::class,
            "Schlaf" to SleepSessionRecord::class,
            "Puls" to HeartRateRecord::class,
            "Ruhepuls" to RestingHeartRateRecord::class,
            "HRV" to HeartRateVariabilityRmssdRecord::class,
        ).map { (name, klasse) -> zaehle(klient, name, klasse, fenster) }
    }

    private suspend fun <T : Record> zaehle(
        klient: HealthConnectClient,
        name: String,
        klasse: KClass<T>,
        fenster: TimeRangeFilter,
    ): Befund {
        val saetze = fange("Bestand $name") {
            klient.readRecords(ReadRecordsRequest(klasse, fenster)).records
        } ?: return Befund(name, 0, emptySet())
        return Befund(
            name,
            saetze.size,
            saetze.map { it.metadata.dataOrigin.packageName }.filter { it.isNotEmpty() }.toSet(),
        )
    }

    // --- Kleinkram ---

    private fun nachtfenster(tag: LocalDate): TimeRangeFilter = TimeRangeFilter.between(
        tag.minusDays(1).atTime(NACHT_AB),
        minOf(tag.atTime(NACHT_AB), LocalDateTime.now(zone)),
    )

    /**
     * Das Wasserziel kennt Drinktervall, nicht die Akte.
     *
     * Es schickt die eingestellte Glaeserzahl und die Glasgroesse mit; beides
     * merkt sich [Riegel] nicht, also steht es hier als Vorgabe. Besser als
     * eine erfundene Literzahl ist das allemal - und wenn Drinktervall es
     * einmal mitliefert, steht die Stelle schon.
     */
    private fun wasserziel(): Double = 8 * 300.0

    private suspend fun letzterPuls(
        klient: HealthConnectClient,
        tag: TimeRangeFilter,
    ): Double? = fange("Puls") {
        klient.readRecords(ReadRecordsRequest(HeartRateRecord::class, tag))
            .records.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute?.toDouble()
    }

    /**
     * Die letzte RMSSD-Messung - egal wie alt.
     *
     * Sie kommt nachts von der Uhr und liegt tagsueber unveraendert da; ein
     * Fenster von heute liesse sie am Nachmittag verschwinden, obwohl sie
     * gilt. Darum sieben Tage zurueck und die juengste nehmen.
     */
    private suspend fun letzteHrv(klient: HealthConnectClient): Double? =
        fange("HRV") {
            klient.readRecords(
                ReadRecordsRequest(
                    HeartRateVariabilityRmssdRecord::class,
                    TimeRangeFilter.between(
                        Instant.now().minus(Duration.ofDays(7)), Instant.now()
                    ),
                )
            ).records.maxByOrNull { it.time }?.heartRateVariabilityMillis
        }

    /**
     * Eine Abfrage, die scheitern darf.
     *
     * JEDE EINZELN GEFANGEN, nicht der ganze Block: faellt eine Satzart aus -
     * weil die Erlaubnis fehlt oder die Akte sie nicht kennt -, sollen die
     * uebrigen acht trotzdem dastehen.
     */
    private inline fun <T> fange(was: String, tue: () -> T?): T? = try {
        tue()
    } catch (e: Exception) {
        Log.w(PebbleEmpfaenger.TAG, "$was: " + e.message)
        null
    }
}
