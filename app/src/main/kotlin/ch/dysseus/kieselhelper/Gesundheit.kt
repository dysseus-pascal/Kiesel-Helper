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
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

        /** Wie weit das Ziel erfuellt ist, hoechstens ganz. Fuer das Widget. */
        val anteil: Float
            get() {
                val z = zahl ?: return 0f
                val t = ziel ?: return 0f
                if (t <= 0) return 0f
                return (z / t).coerceIn(0.0, 1.0).toFloat()
            }

        /**
         * Derselbe Balken, aber mit Platz fuer das, was darueber hinausgeht.
         *
         * EIN GEDECKELTER BALKEN VERSCHWEIGT DEN UEBERSCHUSS: neun Stunden
         * Schlaf bei acht Stunden Ideal sahen aus wie genau acht. Die Spur
         * steht deshalb fuer den GROESSEREN der beiden Werte, das Ziel sitzt
         * als Marke darin, und was dahinter kommt, bekommt eine eigene Farbe.
         */
        private val groesserer: Double
            get() = maxOf(zahl ?: 0.0, ziel ?: 0.0)

        val balkenAnteil: Float
            get() {
                val z = zahl ?: return 0f
                val t = ziel ?: return 0f
                if (groesserer <= 0) return 0f
                return (minOf(z, t) / groesserer).toFloat()
            }

        val balkenUeber: Float
            get() {
                val z = zahl ?: return 0f
                val t = ziel ?: return 0f
                if (groesserer <= 0 || z <= t) return 0f
                return ((z - t) / groesserer).toFloat()
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
        /** Streuung der Nachtproben - NICHT die HRV, siehe [Nachtpuls]. */
        val nachtStreuung: Wert,
        val nachtProben: Int,
        val hrv: Wert,
        val suppFaellig: Wert,
        val suppGenommen: Wert,
        val suppListe: List<Supplemente.Eintrag>,
        /** Was kein Sensor weiss: selbst eingetragen. */
        val energie: Int?,
        val koffeinMg: Double?,
        val koffeinLetzt: Double?,
        val phasen: Phasen?,
        val nachtzeiten: Nachtzeiten?,
        val wocheSchritte: List<Tageswert>,
        val wocheSchlaf: List<Tageswert>,
        val wocheWasser: List<Tageswert>,
        val wocheSuppFaellig: List<Tageswert>,
        val wocheSuppGenommen: List<Tageswert>,
        val pulsverlauf: List<Punkt>,
        /** Uhrzeit des linken Rands im Pulsbild, als Minute des Tages. */
        val pulsBeginn: Int,
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
            HealthPermission.getReadPermission(NutritionRecord::class),
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
        /**
         * Das Schlafziel steht NICHT hier, sondern in [Einstellungen].
         *
         * Acht Stunden sind ein Mittelwert ueber Menschen, keine Vorgabe fuer
         * einen. Wer mit sieben auskommt, bekaeme jede Nacht einen Balken
         * vorgehalten, der nichts bedeutet.
         */

        /** Sieben Tage im Wochenbild - eine Woche liest man auf einen Blick. */
        const val TAGE = 7

        /**
         * Geholt wird ein Tag mehr, als ein Bild zeigt.
         *
         * WER DEN HEUTIGEN BALKEN ZEIGT, ZEIGT EINEN HALBEN TAG neben ganzen.
         * Um zehn Uhr morgens steht er auf einem Drittel, und das Bild sagt
         * "heute war schwach", wo "heute ist noch nicht vorbei" gilt. Manche
         * Bilder brauchen ihn trotzdem - Wasser und Supplemente etwa, wo man
         * genau wissen will, was heute noch fehlt. Deshalb kommt der Tag mit
         * und jedes Bild entscheidet selbst.
         */
        const val TAGE_GEHOLT = TAGE + 1

        /** So viele Pulspunkte passen auf einen Telefonschirm, ohne zu kleben. */
        const val PUNKTE_MAX = 400

        /** Punkte in der Wolke ueber mehrere Tage, und wie viele Seiten dafuer. */
        const val WOLKE_MAX = 1500
        const val SEITEN_MAX = 12

        /**
         * So viele der tiefsten Nachtmessungen bilden den geschaetzten
         * Ruhepuls.
         *
         * EIN EINZELNER TIEFSTWERT IST KEIN RUHEPULS. Ein verrutschter
         * Sensor, eine schlechte Auflage, und es steht 41 da, wo 54 waere.
         * Zehn Messungen zu mitteln kostet nichts und faengt genau das ab -
         * und es bleibt trotzdem die ruhigste Stelle der Nacht.
         */
        const val TIEFSTE = 10

        /** Die Stufe im Bewegungsprofil. Halbe Stunden: feiner waere Rauschen. */
        const val STUFE_MIN = 30

        /** So viele Naechte holt das Nachtragen den Schaetzwert nach. */
        const val NAECHTE_NACH = 3

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
     * Wann ein Tag anfaengt - und damit, welcher Tag gerade laeuft.
     *
     * NICHT MITTERNACHT, WENN JEMAND ES ANDERS WILL. Wer um zwei Uhr noch
     * unterwegs ist, hat seine Schritte am Vortag gemacht; mit einer Grenze um
     * sechs zaehlt die Nacht zu dem Tag, an dem sie begann. Steht die Grenze
     * auf null, ist alles wie vorher.
     */
    private fun tagBeginn(tag: LocalDate): LocalDateTime =
        tag.atTime(Einstellungen.tagesgrenze(context), 0)

    private fun heute(): LocalDate = Einstellungen.heute(context)

    /**
     * Alles auf einmal holen.
     *
     * Ein Aufruf je Kennzahl waere sauberer zu lesen, aber die Akte antwortet
     * traege; gebuendelt geht es in einem Durchgang. Faellt eine Abfrage aus,
     * fehlt NUR ihr Wert - die uebrigen stehen trotzdem da.
     */
    suspend fun lies(): Stand? = coroutineScope {
        val klient = Akte(context).bereit() ?: return@coroutineScope null
        val heute = heute()
        val jetzt = LocalDateTime.now(zone)
        val tag = TimeRangeFilter.between(tagBeginn(heute), jetzt)
        val nacht = nachtfenster(heute)

        // NEBENEINANDER, NICHT HINTEREINANDER. Es sind ein Dutzend Abfragen,
        // und keine braucht das Ergebnis einer anderen. Hintereinander
        // addierten sich ihre Wartezeiten zu der Sekunde, in der der Schirm
        // beim Oeffnen leer stand; nebeneinander dauert es so lange wie die
        // langsamste.
        val nachts = async { nachtpuls(klient, nacht) }

        val summen = async {
            fange("Tagessummen") {
                klient.aggregate(
                    AggregateRequest(
                        metrics = setOf(
                            StepsRecord.COUNT_TOTAL,
                            DistanceRecord.DISTANCE_TOTAL,
                            ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                            ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                            HydrationRecord.VOLUME_TOTAL,
                            // Tageshoch und Tagestief des Pulses. Sie kosten
                            // hier nichts extra - dieselbe Abfrage, zwei
                            // Kennzahlen mehr - und beantworten, was ein
                            // Mittelwert nie sagt: wie weit der Tag
                            // ausgeschlagen hat.
                            HeartRateRecord.BPM_MAX,
                            HeartRateRecord.BPM_MIN,
                            // Seit die App es selbst eintraegt, ist die Akte
                            // auch beim Koffein die Quelle - und faengt mit,
                            // was eine andere App eingetragen hat.
                            NutritionRecord.CAFFEINE_TOTAL,
                        ),
                        timeRangeFilter = tag,
                    )
                )
            }
        }

        val schlafMin = async {
            fange("Schlaf") {
                klient.aggregate(
                    AggregateRequest(
                        metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                        timeRangeFilter = nacht,
                    )
                )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toDouble()
            }
        }

        val sitzungen = async {
            fange("Schlafphasen") {
                klient.readRecords(ReadRecordsRequest(SleepSessionRecord::class, nacht)).records
            } ?: emptyList()
        }

        val ruhe = async { ruhepuls(klient, nacht) }
        val puls = async { letzterPuls(klient, tag) }
        val hrv = async { letzteHrv(klient) }
        val wocheSchritte = async { wocheSchritteWasser(klient, StepsRecord.COUNT_TOTAL) }
        val wocheWasser = async { wocheSchritteWasser(klient, HydrationRecord.VOLUME_TOTAL) }
        val wocheSchl = async { wocheSchlaf(klient, heute) }
        val verlauf = async { pulsverlauf(klient) }

        // SUPPLEMENTE KOMMEN NICHT AUS DER AKTE, sondern aus der eigenen
        // Tabelle: die Akte kennt keine Satzart fuer "genommen". SupCycle
        // schickt seinen Stand bei jeder Einnahme, [Aufgaben] schreibt ihn
        // weg, und hier wird er nur noch abgeholt.
        class Eigenes(
            val suppFaellig: Double?,
            val suppGenommen: Double?,
            val suppWocheF: List<Tageswert>,
            val suppWocheG: List<Tageswert>,
            val suppListe: List<Supplemente.Eintrag>,
            val energie: Double?,
            val koffeinMg: Double?,
            val koffeinLetzt: Double?,
        )
        val eigenes = async(Dispatchers.IO) {
            val speicher = Speicher(context)
            Eigenes(
                suppFaellig = speicher.wert(heute, "supp_faellig"),
                suppGenommen = speicher.wert(heute, "supp_genommen"),
                suppWocheF = wocheAusSpeicher(speicher, "supp_faellig", heute),
                suppWocheG = wocheAusSpeicher(speicher, "supp_genommen", heute),
                suppListe = Supplemente.lies(context)?.heute.orEmpty(),
                energie = speicher.wert(heute, "energie"),
                koffeinMg = speicher.wert(heute, "koffein_mg"),
                koffeinLetzt = speicher.wert(heute, "koffein_letzt"),
            )
        }

        val sum = summen.await()
        val eig = eigenes.await()
        val nachtpuls = nachts.await()
        val schlafsitzungen = sitzungen.await()

        val stand = Stand(
            schritte = Wert("Schritte", sum?.get(StepsRecord.COUNT_TOTAL)?.toDouble(),
                            "", ZIEL_SCHRITTE),
            distanz = Wert("Distanz",
                           sum?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers, "km"),
            kalorien = Wert("Aktive Kalorien",
                            sum?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                                ?.inKilocalories, "kcal"),
            aktiv = Wert("Aktiv",
                         sum?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
                             ?.toMinutes()?.toDouble(), "min", ZIEL_AKTIV_MIN),
            wasser = Wert("Wasser",
                          sum?.get(HydrationRecord.VOLUME_TOTAL)?.inMilliliters,
                          "ml", wasserziel()),
            schlaf = Wert("Schlaf", schlafMin.await(), "min",
                          Einstellungen.schlafziel(context).toDouble()),
            ruhepuls = ruhe.await(),
            puls = Wert("Puls", puls.await(), "bpm"),
            pulsHoch = Wert("Puls hoch", sum?.get(HeartRateRecord.BPM_MAX)?.toDouble(), "bpm"),
            pulsTief = Wert("Puls tief", sum?.get(HeartRateRecord.BPM_MIN)?.toDouble(), "bpm"),
            nachtStreuung = Wert("Nachtpuls", nachtpuls?.streuung, "bpm"),
            nachtProben = nachtpuls?.proben ?: 0,
            hrv = Wert("HRV", hrv.await(), "ms"),
            suppFaellig = Wert("Geplant", eig.suppFaellig, ""),
            suppGenommen = Wert("Supplemente", eig.suppGenommen, "", ziel = eig.suppFaellig),
            suppListe = eig.suppListe,
            energie = eig.energie?.toInt(),
            koffeinMg = sum?.get(NutritionRecord.CAFFEINE_TOTAL)
                ?.inGrams?.times(1000)
                ?: eig.koffeinMg,
            koffeinLetzt = eig.koffeinLetzt,
            phasen = phasenAus(schlafsitzungen),
            nachtzeiten = zeitenAus(schlafsitzungen, heute),
            wocheSchritte = wocheSchritte.await(),
            wocheWasser = wocheWasser.await(),
            wocheSchlaf = wocheSchl.await(),
            wocheSuppFaellig = eig.suppWocheF,
            wocheSuppGenommen = eig.suppWocheG,
            pulsverlauf = verlauf.await(),
            pulsBeginn = pulsBeginn(),
            gelesen = Instant.now(),
        )

        // JEDES LESEN IST EIN EINTRAG. Die Akte selbst vergisst; was hier
        // nicht in die eigene Tabelle faellt, ist in einem Monat als
        // Mittwoch nicht mehr nachweisbar.
        merke(stand, heute)
        stand
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
            "schlaf_von" to stand.nachtzeiten?.von,
            "schlaf_bis" to stand.nachtzeiten?.bis,
            "schlaf_mitte" to stand.nachtzeiten?.mitte,
            // GEMESSEN UND GESCHAETZT IN GETRENNTE SPALTEN. Ein aus dem
            // Nachttief hergeleiteter Ruhepuls darf spaeter nicht als
            // eingetragener durchgehen - in einem Jahresmittel sieht man
            // ihm nicht mehr an, woher er kam.
            "ruhepuls" to stand.ruhepuls.zahl.takeUnless { stand.ruhepuls.geschaetzt },
            "puls_min" to stand.ruhepuls.zahl.takeIf { stand.ruhepuls.geschaetzt },
            "puls_hoch" to stand.pulsHoch.zahl,
            "puls_tief" to stand.pulsTief.zahl,
            "puls_nacht_sd" to stand.nachtStreuung.zahl,
            "hrv" to stand.hrv.zahl,
            "koffein_mg" to stand.koffeinMg,
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
        val heute = heute()
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
                    // Die Eimer beginnen an der Tagesgrenze, nicht um
                    // Mitternacht: der Schnitt teilt die Reihe ab ihrem
                    // Anfang, und der liegt jetzt dort, wo der Tag anfaengt.
                    timeRangeFilter = TimeRangeFilter.between(tagBeginn(von), jetzt),
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
                    TimeRangeFilter.between(tagBeginn(von), jetzt),
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

        // Die letzten Naechte einzeln: nur hier laesst sich das Mittel der
        // zehn tiefsten Messungen bilden, und nur so steht in der Spalte
        // ueberall dasselbe. Drei Naechte, weil jede ein eigener Lesevorgang
        // ist - aeltere fuellen sich von selbst, sobald die App laeuft.
        val geschaetzt = HashMap<LocalDate, Double>()
        val nachtsd = HashMap<LocalDate, Double>()
        for (i in 1..NAECHTE_NACH) {
            val nacht = heute.minusDays(i.toLong())
            nachtpuls(klient, nachtfenster(nacht))?.let {
                geschaetzt[nacht] = it.ruhe
                it.streuung?.let { sd -> nachtsd[nacht] = sd }
            }
        }

        val speicher = Speicher(context)
        withContext(Dispatchers.IO) {
            eimer.forEach { e ->
                val tag = e.startTime.toLocalDate()
                val nacht = naechte[tag].orEmpty()
                val phasen = phasenAus(nacht)
                val zeiten = zeitenAus(nacht, tag)
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
                    "schlaf_von" to zeiten?.von,
                    "schlaf_bis" to zeiten?.bis,
                    "schlaf_mitte" to zeiten?.mitte,
                    "ruhepuls" to e.result[RestingHeartRateRecord.BPM_AVG]?.toDouble(),
                    // KEIN "puls_min" HIER. Der Schaetzwert ist das Mittel der
                    // zehn tiefsten Nachtmessungen; das Tagestief waere eine
                    // andere Zahl unter demselben Namen, und im Verlauf saehe
                    // man den Sprung an dem Tag, an dem die Rechnung wechselt.
                    // Die letzten Naechte kommen weiter unten einzeln.
                    "puls_tief" to e.result[HeartRateRecord.BPM_MIN]?.toDouble(),
                    "puls_hoch" to e.result[HeartRateRecord.BPM_MAX]?.toDouble(),
                    "puls_min" to geschaetzt[tag],
                    "puls_nacht_sd" to nachtsd[tag],
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
        val gestern = tagBeginn(heute().minusDays(1))

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

        nachtpuls(klient, nacht)
            ?.let { return Wert("Ruhepuls", it.ruhe, "bpm", geschaetzt = true) }

        return Wert("Ruhepuls", null, "bpm")
    }

    /**
     * Was die Nacht ueber den Puls hergibt.
     *
     * ZWEI ZAHLEN AUS EINEM LESEVORGANG: der geschaetzte Ruhepuls (Mittel der
     * [TIEFSTE] tiefsten Messungen) und die Streuung aller Nachtproben.
     *
     * DIE STREUUNG IST NICHT DIE HRV, und sie darf auch nicht so heissen.
     * RMSSD misst die Schwankung zwischen AUFEINANDERFOLGENDEN SCHLAEGEN, in
     * Millisekunden; dafuer braucht es die Zeitpunkte einzelner Schlaege, und
     * die stehen in keinem HeartRateRecord. Was hier steht, ist die Streuung
     * ganzzahliger Pulswerte ueber Stunden - sie sagt, wie ruhig eine Nacht
     * verlief (Schlafphasen, Aufwachen), nicht wie das vegetative Nervensystem
     * von Schlag zu Schlag arbeitet.
     */
    data class Nachtpuls(val ruhe: Double, val streuung: Double?, val proben: Int)

    private suspend fun nachtpuls(
        klient: HealthConnectClient,
        nacht: TimeRangeFilter,
    ): Nachtpuls? {
        val proben = fange("Nachtpuls") {
            klient.readRecords(ReadRecordsRequest(HeartRateRecord::class, nacht))
                .records.flatMap { it.samples }.map { it.beatsPerMinute.toDouble() }
        } ?: return null
        // Unter drei Messungen gibt es nichts: aus zwei Werten einen Ruhepuls
        // zu mitteln hiesse, die Nacht aus zwei Augenblicken zu beschreiben.
        if (proben.size < 3) return null
        return Nachtpuls(
            ruhe = proben.sorted().take(TIEFSTE).average(),
            streuung = Auswertung.streuung(proben),
            proben = proben.size,
        )
    }

    /**
     * Wann die Nacht anfing, aufhoerte, und wo ihre Mitte lag.
     *
     * GERECHNET WIRD IN MINUTEN SEIT ACHTZEHN UHR, nicht in Uhrzeiten. 23:10
     * und 00:30 liegen achtzig Minuten auseinander, als Tagesminuten aber
     * 1360 - jeder Mittelwert ueber Mitternacht hinweg waere sonst Unsinn, und
     * gerade die Mitte ist hier die interessante Zahl.
     *
     * DIE SCHLAFMITTE IST DER STABILERE WERT. Wer eine Nacht kurz schlaeft,
     * merkt das am naechsten Tag; wer jede Nacht zu einer anderen Zeit
     * schlaeft, merkt es dauerhaft. Die Dauer sagt das nicht.
     */
    data class Nachtzeiten(val von: Double, val bis: Double) {
        val mitte: Double get() = (von + bis) / 2
    }

    private fun zeitenAus(
        sitzungen: List<SleepSessionRecord>,
        nacht: LocalDate,
    ): Nachtzeiten? {
        if (sitzungen.isEmpty()) return null
        val null18 = nacht.minusDays(1).atTime(NACHT_AB).atZone(zone).toInstant()
        val von = sitzungen.minOf { Duration.between(null18, it.startTime).toMinutes() }
        val bis = sitzungen.maxOf { Duration.between(null18, it.endTime).toMinutes() }
        if (bis <= von) return null
        return Nachtzeiten(von.toDouble(), bis.toDouble())
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
        val heute = heute()
        val eimer = fange("Woche") {
            klient.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL, HydrationRecord.VOLUME_TOTAL
                    ),
                    timeRangeFilter = TimeRangeFilter.between(
                        tagBeginn(heute.minusDays((TAGE_GEHOLT - 1).toLong())),
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
        return (0 until TAGE_GEHOLT).map { i ->
            val t = heute.minusDays((TAGE_GEHOLT - 1 - i).toLong())
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
    ): List<Tageswert> = (0 until TAGE_GEHOLT).map { i ->
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
     * Alle Pulsmessungen des Tages - als EINZELNE Punkte.
     *
     * VORHER WAREN ES FUENFMINUTENMITTEL, und die logen durch Weglassen: eine
     * Uhr misst alle zehn Minuten und waehrend einer Anstrengung dauernd. Eine
     * geglaettete Linie machte daraus einen ruhigen Verlauf und verschwieg
     * genau die Ausschlaege, deretwegen man hinschaut.
     *
     * Die Wolke zeigt die Streuung, die Trendlinie im Bild zieht den Mittelweg
     * hindurch - beides nebeneinander, statt eines davon statt des anderen.
     *
     * Gedeckelt bei [PUNKTE_MAX]: mehr Punkte als Bildpunkte ergeben keine
     * Wolke, sondern einen Balken. Ausgeduennt wird gleichmaessig, damit die
     * Form erhalten bleibt.
     */
    private suspend fun pulsverlauf(klient: HealthConnectClient): List<Punkt> {
        val jetzt = Instant.now()
        val beginn = jetzt.minus(Duration.ofHours(24))
        val saetze = fange("Pulsverlauf") {
            klient.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class, TimeRangeFilter.between(beginn, jetzt)
                )
            ).records
        } ?: return emptyList()

        val alle = saetze.flatMap { satz ->
            satz.samples.map { probe ->
                // Minuten SEIT FENSTERBEGINN, nicht seit Mitternacht: das
                // Fenster laeuft ueber die Tagesgrenze hinweg.
                Punkt(
                    (Duration.between(beginn, probe.time).toMinutes()).toInt()
                        .coerceIn(0, 1440),
                    probe.beatsPerMinute.toDouble(),
                )
            }
        }.sortedBy { it.minute }

        if (alle.size <= PUNKTE_MAX) return alle
        val schritt = alle.size.toDouble() / PUNKTE_MAX
        return (0 until PUNKTE_MAX).map { i -> alle[(i * schritt).toInt()] }
    }



    /**
     * Wann am Tag man sich bewegt - in Halbstundenstufen.
     *
     * DIE TAGESSUMME SAGT NICHT, OB EIN TAG SCHWACH WAR oder nur spaet: 4000
     * Schritte um achtzehn Uhr sind ein anderer Tag als 4000 um zehn. Das
     * Profil beantwortet das, und uebereinandergelegt ueber zwei Wochen sagt
     * es, wie ein Tag bei einem AUSSIEHT.
     *
     * Eine einzige Abfrage je Zeitraum: die Akte kann selbst in Stufen
     * schneiden (`aggregateGroupByDuration`), und achtundvierzig Eimer von
     * Hand zu fuellen hiesse, achtundvierzig Mal dasselbe zu fragen.
     */
    suspend fun bewegungsprofil(tage: Int = 1): List<Punkt> {
        val klient = Akte(context).bereit() ?: return emptyList()
        val heute = heute()
        val beginn = tagBeginn(heute.minusDays((tage - 1).toLong()))
            .atZone(zone).toInstant()
        val jetzt = Instant.now()

        val eimer = fange("Bewegungsprofil") {
            klient.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(beginn, jetzt),
                    timeRangeSlicer = Duration.ofMinutes(STUFE_MIN.toLong()),
                )
            )
        } ?: return emptyList()

        // Nach TAGESZEIT zusammenlegen, nicht nach Eimer: bei mehreren Tagen
        // sollen sich die Stufen desselben Zeitfensters treffen. Gemittelt
        // wird ueber die Zahl der Tage, nicht ueber die gefundenen Eimer -
        // ein Fenster ganz ohne Schritte fehlt in der Antwort, und es als
        // "nicht vorhanden" zu behandeln hoebe den Schnitt kuenstlich an.
        val summe = HashMap<Int, Double>()
        eimer.forEach { e ->
            val z = LocalDateTime.ofInstant(e.startTime, zone)
            val stufe = ((z.hour * 60 + z.minute) / STUFE_MIN) * STUFE_MIN
            val schritte = e.result[StepsRecord.COUNT_TOTAL]?.toDouble() ?: 0.0
            summe[stufe] = (summe[stufe] ?: 0.0) + schritte
        }
        return summe.entries.sortedBy { it.key }
            .map { (stufe, gesamt) -> Punkt(stufe, gesamt / tage) }
    }

    /**
     * Alle Pulsmessungen der letzten Tage, nach Tageszeit uebereinandergelegt.
     *
     * DER TYPISCHE TAG. Eine einzelne Tageslinie sagt, was gestern war; diese
     * Wolke sagt, wie ein Tag bei einem AUSSIEHT - wann der Puls hochgeht,
     * wie breit die Streuung mittags ist, wie tief es nachts wird.
     *
     * VIERZEHN TAGE, NICHT DREISSIG. Die Akte gibt zwar mehr her, aber jede
     * Messung ist ein Datensatz: bei zehn Minuten Takt sind das gut 2000 am
     * Tag, und dreissig Tage waeren 60000 Saetze ueber eine
     * Prozessgrenze. Vierzehn Tage zeigen dasselbe Muster.
     *
     * Blaettern ist Pflicht: `readRecords` liefert hoechstens eine Seite, und
     * wer den Rest nicht holt, zeichnet eine Wolke aus dem ersten Drittel des
     * Zeitraums und nennt sie den typischen Tag.
     */
    suspend fun pulswolke(tage: Int = 14): List<Punkt> {
        val klient = Akte(context).bereit() ?: return emptyList()
        val bis = tagBeginn(heute())
        val von = bis.minusDays(tage.toLong())

        val gesammelt = mutableListOf<Punkt>()
        var marke: String? = null
        var seiten = 0
        do {
            val antwort = fange("Pulswolke") {
                klient.readRecords(
                    ReadRecordsRequest(
                        HeartRateRecord::class,
                        TimeRangeFilter.between(von, bis),
                        pageSize = 1000,
                        pageToken = marke,
                    )
                )
            } ?: break

            antwort.records.forEach { satz ->
                satz.samples.forEach { probe ->
                    val z = LocalDateTime.ofInstant(probe.time, zone)
                    gesammelt += Punkt(z.hour * 60 + z.minute, probe.beatsPerMinute.toDouble())
                }
            }
            marke = antwort.pageToken
            seiten++
        } while (marke != null && seiten < SEITEN_MAX && gesammelt.size < WOLKE_MAX * 4)

        if (gesammelt.size <= WOLKE_MAX) return gesammelt.sortedBy { it.minute }
        val schritt = gesammelt.size.toDouble() / WOLKE_MAX
        return (0 until WOLKE_MAX)
            .map { i -> gesammelt[(i * schritt).toInt()] }
            .sortedBy { it.minute }
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
            "Ernährung" to NutritionRecord::class,
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

    /**
     * Wo das Pulsfenster anfaengt - als Minute des Tages.
     *
     * DIE TAGESGRENZE IST NUR EINE ZAEHLGRENZE. Wer sie auf sechs Uhr setzt,
     * will trotzdem den Verlauf der letzten Nacht sehen; ein Bild, das um
     * sechs anfaengt, verschweigt ihn. Deshalb zeigt das Pulsbild die letzten
     * vierundzwanzig Stunden, egal wo der Tag beginnt.
     */
    private fun pulsBeginn(): Int {
        val z = LocalDateTime.now(zone).minusHours(24)
        return z.hour * 60 + z.minute
    }

    /** Sieben Tage einer Spalte aus dem eigenen Speicher. */
    private fun wocheAusSpeicher(
        speicher: Speicher,
        spalte: String,
        heute: LocalDate,
    ): List<Tageswert> {
        val nach = speicher.reihe(spalte).toMap()
        return (0 until TAGE_GEHOLT).map { i ->
            val t = heute.minusDays((TAGE_GEHOLT - 1 - i).toLong())
            Tageswert(t, nach[t])
        }
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
