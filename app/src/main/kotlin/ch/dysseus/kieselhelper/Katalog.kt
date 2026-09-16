package ch.dysseus.kieselhelper

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.time.ZoneId

/**
 * Welche Satzarten Kiesel-Helper in die Gesundheitsakte schreiben kann.
 *
 * DAS IST DER EINZIGE TEIL, DEN EINE BESCHREIBUNG NICHT ERWEITERN KANN.
 * Jede Satzart braucht eine Berechtigung, Berechtigungen stehen im Manifest,
 * und das Manifest wird beim Installieren festgeschrieben. Eine nachgeladene
 * Beschreibung kann dort nichts hinzufuegen — sie kann nur waehlen, was schon
 * da ist.
 *
 * Wer eine Art ergaenzen will, braucht drei Dinge: eine Zeile hier, eine
 * <uses-permission>-Zeile im Manifest, und eine neue Fassung der App auf dem
 * Telefon. Das ist der Preis, und er ist von Android gesetzt, nicht von mir.
 *
 * WAS AUFGENOMMEN IST: was eine Pebble-Uhr wirklich hergibt.
 * WAS NICHT: wofuer eine Uhr ein schlechtes Eingabegeraet waere — Gewicht,
 * Blutdruck, Koerpertemperatur tippt man am Telefon, nicht mit drei Tasten.
 */
enum class Satzart(
    /** So heisst die Art in der Beschreibung (Feld `art`). */
    val id: String,
    /** Welche Werte die Beschreibung liefern muss. */
    val form: Form,
    /** In welcher Einheit der Wert erwartet wird — nur zur Pruefung und Anzeige. */
    val einheit: String,
    /** Klartext fuer die Oberflaeche. */
    val klartext: String,
) {
    HRV_RMSSD("hrv_rmssd", Form.WERT_ZEITPUNKT, "ms", "Herzratenvariabilität"),
    HERZFREQUENZ("herzfrequenz", Form.WERT_ZEITPUNKT, "bpm", "Herzfrequenz"),
    WASSER("hydration", Form.MENGE_SPANNE, "ml", "Getrunkenes Wasser"),
    KOFFEIN("koffein", Form.MENGE_SPANNE, "mg", "Koffein"),
    SCHRITTE("schritte", Form.MENGE_SPANNE, "Schritte", "Schritte"),
    SCHLAF("schlaf", Form.SPANNE, "", "Schlaf");

    /**
     * Die Berechtigung dieser Art.
     *
     * Traege ausgerechnet und nicht im Konstruktor: waere es ein Feld, liefe
     * beim Laden der Klasse ein Aufruf in die Health-Connect-Bibliothek, und
     * zwar fuer JEDE Art — auch auf einem Geraet, auf dem es Health Connect gar
     * nicht gibt.
     */
    val berechtigung: String
        get() = when (this) {
            HRV_RMSSD -> HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class)
            HERZFREQUENZ -> HealthPermission.getWritePermission(HeartRateRecord::class)
            WASSER -> HealthPermission.getWritePermission(HydrationRecord::class)
            KOFFEIN -> HealthPermission.getWritePermission(NutritionRecord::class)
            SCHRITTE -> HealthPermission.getWritePermission(StepsRecord::class)
            SCHLAF -> HealthPermission.getWritePermission(SleepSessionRecord::class)
        }

    /**
     * Aus den Werten der Beschreibung einen Satz fuer die Akte bauen.
     *
     * `wert` ist das, was die Beschreibung aus der Uhr-Nachricht gezogen hat;
     * `beginn` der Zeitpunkt und `dauerSekunden` die Spanne. Arten der Form
     * WERT_ZEITPUNKT ignorieren die Dauer.
     */
    fun baue(wert: Double, beginn: Instant, dauerSekunden: Long, metadata: Metadata): Record {
        val ende = beginn.plusSeconds(if (dauerSekunden > 0) dauerSekunden else 1)
        val zone = ZoneId.systemDefault().rules.getOffset(beginn)
        val zoneEnde = ZoneId.systemDefault().rules.getOffset(ende)
        return when (this) {
            HRV_RMSSD -> HeartRateVariabilityRmssdRecord(
                time = beginn,
                zoneOffset = zone,
                heartRateVariabilityMillis = wert,
                metadata = metadata,
            )
            // Die Akte fuehrt Herzfrequenz als Reihe von Proben, nicht als
            // Einzelwert. Eine Uhr-Nachricht traegt aber genau eine Zahl —
            // also eine Reihe aus einer Probe.
            HERZFREQUENZ -> HeartRateRecord(
                startTime = beginn,
                startZoneOffset = zone,
                endTime = ende,
                endZoneOffset = zoneEnde,
                samples = listOf(
                    HeartRateRecord.Sample(time = beginn, beatsPerMinute = wert.toLong())
                ),
                metadata = metadata,
            )
            WASSER -> HydrationRecord(
                startTime = beginn,
                startZoneOffset = zone,
                endTime = ende,
                endZoneOffset = zoneEnde,
                volume = Volume.milliliters(wert),
                metadata = metadata,
            )
            // Koffein fuehrt die Akte in Gramm, die Uhr meldet Milligramm.
            KOFFEIN -> NutritionRecord(
                startTime = beginn,
                startZoneOffset = zone,
                endTime = ende,
                endZoneOffset = zoneEnde,
                caffeine = Mass.grams(wert / 1000.0),
                metadata = metadata,
            )
            SCHRITTE -> StepsRecord(
                startTime = beginn,
                startZoneOffset = zone,
                endTime = ende,
                endZoneOffset = zoneEnde,
                count = wert.toLong(),
                metadata = metadata,
            )
            SCHLAF -> SleepSessionRecord(
                startTime = beginn,
                startZoneOffset = zone,
                endTime = ende,
                endZoneOffset = zoneEnde,
                metadata = metadata,
            )
        }
    }

    companion object {
        fun nachId(id: String): Satzart? = entries.firstOrNull { it.id == id }

        /** Alle Berechtigungen des Katalogs — auf einmal angefragt. */
        fun alleBerechtigungen(): Set<String> = entries.map { it.berechtigung }.toSet()
    }
}

/**
 * Welche Angaben eine Beschreibung fuer eine Satzart machen muss.
 *
 * Die Akte ist darin nicht einheitlich: manche Arten wollen einen Zeitpunkt
 * (eine Messung geschieht in einem Augenblick), andere eine Spanne (getrunken,
 * gegangen, geschlafen wird ueber eine Zeit hinweg). Eine Spanne der Laenge
 * null lehnt sie ab.
 */
enum class Form {
    /** `wert` + `zeitpunkt`. */
    WERT_ZEITPUNKT,

    /** `menge` + `beginn` + `dauer_s`. */
    MENGE_SPANNE,

    /** nur `beginn` + `dauer_s`. */
    SPANNE,
}
