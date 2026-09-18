package ch.dysseus.kieselhelper

import android.content.Context
import android.util.Log
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Was die Gesundheitsakte weiss - gelesen, nicht geschrieben.
 *
 * WARUM DIESE APP DAS ZEIGT. Die Akte selbst ist ueberladen: sie kann alles und
 * zeigt darum nichts zuerst. Hier stehen die acht Zahlen, die taeglich zaehlen,
 * auf einem Schirm - und dieselben im Widget, ohne dass man eine App oeffnen
 * muss.
 *
 * KEIN WERT WIRD ERFUNDEN. Steht nichts in der Akte, bleibt das Feld leer und
 * sagt das auch. Eine Null waere eine Behauptung: "du bist heute keinen Schritt
 * gegangen" ist etwas anderes als "niemand hat Schritte eingetragen".
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

    data class Stand(
        val schritte: Wert,
        val distanz: Wert,
        val kalorien: Wert,
        val aktiv: Wert,
        val wasser: Wert,
        val schlaf: Wert,
        val ruhepuls: Wert,
        val puls: Wert,
        val hrv: Wert,
        val gelesen: Instant,
    )

    /** Die Berechtigungen zum LESEN. Getrennt von denen zum Schreiben. */
    companion object {
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
    }

    /**
     * Alles auf einmal holen.
     *
     * Ein Aufruf je Kennzahl waere sauberer zu lesen, aber die Akte antwortet
     * traege; gebuendelt geht es in einem Durchgang. Faellt eine Abfrage aus,
     * fehlt NUR ihr Wert - die uebrigen stehen trotzdem da.
     */
    suspend fun lies(): Stand? {
        val klient = Akte(context).bereit() ?: return null
        val zone = ZoneId.systemDefault()
        val heute = LocalDate.now(zone)
        val tagBeginn = heute.atStartOfDay()
        val jetzt = LocalDateTime.now(zone)
        val tag = TimeRangeFilter.between(tagBeginn, jetzt)

        // Die Nacht: 18 Uhr gestern bis jetzt. Wer um 23 Uhr ins Bett geht,
        // hat seinen Schlaf am Vortag begonnen - ein Fenster ab Mitternacht
        // schnitte ihn in zwei Haelften.
        val nacht = TimeRangeFilter.between(
            heute.minusDays(1).atTime(LocalTime.of(18, 0)), jetzt
        )

        var summen: AggregationResult? = null
        try {
            summen = klient.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                        HydrationRecord.VOLUME_TOTAL,
                    ),
                    timeRangeFilter = tag,
                )
            )
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Tagessummen: " + e.message)
        }

        val schlafMin = try {
            klient.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = nacht,
                )
            )[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes()?.toDouble()
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Schlaf: " + e.message); null
        }

        val ruhe = try {
            klient.aggregate(
                AggregateRequest(
                    metrics = setOf(RestingHeartRateRecord.BPM_AVG),
                    timeRangeFilter = TimeRangeFilter.between(
                        heute.minusDays(1).atStartOfDay(), jetzt
                    ),
                )
            )[RestingHeartRateRecord.BPM_AVG]
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Ruhepuls: " + e.message); null
        }

        return Stand(
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
            ruhepuls = Wert("Ruhepuls", ruhe?.toDouble(), "bpm"),
            puls = Wert("Puls", letzterPuls(klient, tag), "bpm"),
            hrv = Wert("HRV", letzteHrv(klient), "ms"),
            gelesen = Instant.now(),
        )
    }

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
        klient: androidx.health.connect.client.HealthConnectClient,
        tag: TimeRangeFilter,
    ): Double? = try {
        klient.readRecords(ReadRecordsRequest(HeartRateRecord::class, tag))
            .records.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute?.toDouble()
    } catch (e: Exception) {
        Log.w(PebbleEmpfaenger.TAG, "Puls: " + e.message); null
    }

    /**
     * Die letzte RMSSD-Messung - egal wie alt.
     *
     * Sie kommt nachts von der Uhr und liegt tagsueber unveraendert da; ein
     * Fenster von heute liesse sie am Nachmittag verschwinden, obwohl sie
     * gilt. Darum sieben Tage zurueck und die juengste nehmen.
     */
    private suspend fun letzteHrv(
        klient: androidx.health.connect.client.HealthConnectClient,
    ): Double? = try {
        klient.readRecords(
            ReadRecordsRequest(
                HeartRateVariabilityRmssdRecord::class,
                TimeRangeFilter.between(
                    Instant.now().minus(Duration.ofDays(7)), Instant.now()
                ),
            )
        ).records.maxByOrNull { it.time }?.heartRateVariabilityMillis
    } catch (e: Exception) {
        Log.w(PebbleEmpfaenger.TAG, "HRV: " + e.message); null
    }
}
