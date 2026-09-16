package ch.dysseus.kieselhelper

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.Volume
import java.time.Instant
import java.time.ZoneId

/**
 * Welche Satzarten Kiesel-Helper in die Gesundheitsakte schreiben kann.
 *
 * DAS IST DER EINZIGE TEIL, DEN EIN ZETTEL NICHT ERWEITERN KANN. Jede Satzart
 * braucht eine Berechtigung, Berechtigungen stehen im Manifest, und das
 * Manifest wird beim Installieren festgeschrieben.
 *
 * Deshalb steht hier ein VORRAT und nicht nur das Gebrauchte: was hier fehlt,
 * kostet eine neue Fassung der App, alles andere nur einen Zettel. Seit die
 * Benachrichtigungen des Telefons als Quelle dazugekommen sind, ist der Vorrat
 * breit - ein Blutzuckermessgeraet, das eine Benachrichtigung schickt, ist ein
 * ebenso gueltiger Absender wie die Uhr.
 *
 * WAS BEWUSST FEHLT:
 *  - Alles zum Zyklus (Menstruation, Eisprung, Sexualitaet). Eine Bruecke
 *    zwischen Uhr und Telefon hat keinen Grund, das anzumelden, und schon der
 *    blosse Eintrag im Manifest ist eine Aussage.
 *  - Blutdruck: die Akte verlangt ZWEI Werte in einem Satz, systolisch und
 *    diastolisch. Eine Regel liefert bisher einen. Das waere eine eigene Form.
 *  - Reihenwerte wie Leistung oder Trittfrequenz: dort will die Akte eine
 *    Folge von Proben, eine Nachricht traegt aber eine Zahl.
 *  - GELESEN WIRD NICHTS. Kiesel-Helper traegt nur ein.
 */
enum class Satzart(
    /** So heisst die Art im Zettel (Feld `art`). */
    val id: String,
    /** Welche Werte der Zettel liefern muss. */
    val form: Form,
    /** In welcher Einheit der Wert erwartet wird - zur Pruefung und Anzeige. */
    val einheit: String,
    /** Klartext fuer die Oberflaeche. */
    val klartext: String,
) {
    // --- Herz und Atem ---
    HRV_RMSSD("hrv_rmssd", Form.WERT_ZEITPUNKT, "ms", "Herzratenvariabilität"),
    HERZFREQUENZ("herzfrequenz", Form.WERT_ZEITPUNKT, "bpm", "Herzfrequenz"),
    RUHEPULS("ruhepuls", Form.WERT_ZEITPUNKT, "bpm", "Ruhepuls"),
    SAUERSTOFF("sauerstoff", Form.WERT_ZEITPUNKT, "%", "Sauerstoffsättigung"),
    ATEMFREQUENZ("atemfrequenz", Form.WERT_ZEITPUNKT, "1/min", "Atemfrequenz"),

    // --- Koerper ---
    GEWICHT("gewicht", Form.WERT_ZEITPUNKT, "g", "Gewicht"),
    KOERPERFETT("koerperfett", Form.WERT_ZEITPUNKT, "%", "Körperfettanteil"),
    GROESSE("groesse", Form.WERT_ZEITPUNKT, "mm", "Körpergrösse"),
    TEMPERATUR("temperatur", Form.WERT_ZEITPUNKT, "m°C", "Körpertemperatur"),
    BLUTZUCKER("blutzucker", Form.WERT_ZEITPUNKT, "mg/dl", "Blutzucker"),
    GRUNDUMSATZ("grundumsatz", Form.WERT_ZEITPUNKT, "kcal/d", "Grundumsatz"),
    VO2MAX("vo2max", Form.WERT_ZEITPUNKT, "ml/kg/min", "VO2max"),

    // --- Bewegung ---
    SCHRITTE("schritte", Form.MENGE_SPANNE, "Schritte", "Schritte"),
    STRECKE("strecke", Form.MENGE_SPANNE, "m", "Zurückgelegte Strecke"),
    HOEHENMETER("hoehenmeter", Form.MENGE_SPANNE, "m", "Höhenmeter"),
    STOCKWERKE("stockwerke", Form.MENGE_SPANNE, "Stockwerke", "Stockwerke"),
    ROLLSTUHL("rollstuhl", Form.MENGE_SPANNE, "Stösse", "Rollstuhlstösse"),
    AKTIVE_KALORIEN("aktive_kalorien", Form.MENGE_SPANNE, "kcal", "Aktive Kalorien"),
    GESAMT_KALORIEN("gesamt_kalorien", Form.MENGE_SPANNE, "kcal", "Gesamtkalorien"),

    // --- Zufuhr ---
    WASSER("hydration", Form.MENGE_SPANNE, "ml", "Getrunkenes Wasser"),
    KOFFEIN("koffein", Form.MENGE_SPANNE, "mg", "Koffein"),

    // --- Zeitraeume ---
    SCHLAF("schlaf", Form.SPANNE, "", "Schlaf");

    /**
     * Die Berechtigung dieser Art.
     *
     * Traege ausgerechnet und nicht im Konstruktor: waere es ein Feld, liefe
     * beim Laden der Klasse ein Aufruf in die Health-Connect-Bibliothek, und
     * zwar fuer JEDE Art - auch auf einem Geraet, auf dem es Health Connect gar
     * nicht gibt.
     */
    val berechtigung: String
        get() = when (this) {
            HRV_RMSSD -> HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class)
            HERZFREQUENZ -> HealthPermission.getWritePermission(HeartRateRecord::class)
            RUHEPULS -> HealthPermission.getWritePermission(RestingHeartRateRecord::class)
            SAUERSTOFF -> HealthPermission.getWritePermission(OxygenSaturationRecord::class)
            ATEMFREQUENZ -> HealthPermission.getWritePermission(RespiratoryRateRecord::class)
            GEWICHT -> HealthPermission.getWritePermission(WeightRecord::class)
            KOERPERFETT -> HealthPermission.getWritePermission(BodyFatRecord::class)
            GROESSE -> HealthPermission.getWritePermission(HeightRecord::class)
            TEMPERATUR -> HealthPermission.getWritePermission(BodyTemperatureRecord::class)
            BLUTZUCKER -> HealthPermission.getWritePermission(BloodGlucoseRecord::class)
            GRUNDUMSATZ -> HealthPermission.getWritePermission(BasalMetabolicRateRecord::class)
            VO2MAX -> HealthPermission.getWritePermission(Vo2MaxRecord::class)
            SCHRITTE -> HealthPermission.getWritePermission(StepsRecord::class)
            STRECKE -> HealthPermission.getWritePermission(DistanceRecord::class)
            HOEHENMETER -> HealthPermission.getWritePermission(ElevationGainedRecord::class)
            STOCKWERKE -> HealthPermission.getWritePermission(FloorsClimbedRecord::class)
            ROLLSTUHL -> HealthPermission.getWritePermission(WheelchairPushesRecord::class)
            AKTIVE_KALORIEN -> HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
            GESAMT_KALORIEN -> HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class)
            WASSER -> HealthPermission.getWritePermission(HydrationRecord::class)
            KOFFEIN -> HealthPermission.getWritePermission(NutritionRecord::class)
            SCHLAF -> HealthPermission.getWritePermission(SleepSessionRecord::class)
        }

    /**
     * Aus den Werten des Zettels einen Satz fuer die Akte bauen.
     *
     * DIE EINHEITEN SIND DURCHWEG GANZZAHLIG GEWAEHLT - Gramm statt Kilogramm,
     * Millimeter statt Meter, Milligrad statt Grad. Der Zettel traegt ganze
     * Zahlen; wer Gewicht in "kg" verlangte, koennte 72,4 kg nicht ausdruecken.
     * Die Umrechnung in das, was die Akte will, steht hier.
     */
    fun baue(wert: Double, beginn: Instant, dauerSekunden: Long, metadata: Metadata): Record {
        val ende = beginn.plusSeconds(if (dauerSekunden > 0) dauerSekunden else 1)
        val zone = ZoneId.systemDefault().rules.getOffset(beginn)
        val zoneEnde = ZoneId.systemDefault().rules.getOffset(ende)
        return when (this) {
            HRV_RMSSD -> HeartRateVariabilityRmssdRecord(
                time = beginn, zoneOffset = zone,
                heartRateVariabilityMillis = wert, metadata = metadata,
            )
            // Die Akte fuehrt Herzfrequenz als Reihe von Proben, nicht als
            // Einzelwert. Eine Uhr-Nachricht traegt aber genau eine Zahl -
            // also eine Reihe aus einer Probe.
            HERZFREQUENZ -> HeartRateRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                samples = listOf(HeartRateRecord.Sample(beginn, wert.toLong())),
                metadata = metadata,
            )
            RUHEPULS -> RestingHeartRateRecord(
                time = beginn, zoneOffset = zone,
                beatsPerMinute = wert.toLong(), metadata = metadata,
            )
            SAUERSTOFF -> OxygenSaturationRecord(
                time = beginn, zoneOffset = zone,
                percentage = Percentage(wert), metadata = metadata,
            )
            ATEMFREQUENZ -> RespiratoryRateRecord(
                time = beginn, zoneOffset = zone,
                rate = wert, metadata = metadata,
            )
            GEWICHT -> WeightRecord(
                time = beginn, zoneOffset = zone,
                weight = Mass.grams(wert), metadata = metadata,
            )
            KOERPERFETT -> BodyFatRecord(
                time = beginn, zoneOffset = zone,
                percentage = Percentage(wert), metadata = metadata,
            )
            GROESSE -> HeightRecord(
                time = beginn, zoneOffset = zone,
                height = Length.meters(wert / 1000.0), metadata = metadata,
            )
            TEMPERATUR -> BodyTemperatureRecord(
                time = beginn, zoneOffset = zone,
                temperature = Temperature.celsius(wert / 1000.0), metadata = metadata,
            )
            BLUTZUCKER -> BloodGlucoseRecord(
                time = beginn, zoneOffset = zone,
                level = BloodGlucose.milligramsPerDeciliter(wert), metadata = metadata,
            )
            GRUNDUMSATZ -> BasalMetabolicRateRecord(
                time = beginn, zoneOffset = zone,
                basalMetabolicRate = Power.kilocaloriesPerDay(wert), metadata = metadata,
            )
            VO2MAX -> Vo2MaxRecord(
                time = beginn, zoneOffset = zone,
                vo2MillilitersPerMinuteKilogram = wert, metadata = metadata,
            )
            SCHRITTE -> StepsRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                count = wert.toLong(), metadata = metadata,
            )
            STRECKE -> DistanceRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                distance = Length.meters(wert), metadata = metadata,
            )
            HOEHENMETER -> ElevationGainedRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                elevation = Length.meters(wert), metadata = metadata,
            )
            STOCKWERKE -> FloorsClimbedRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                floors = wert, metadata = metadata,
            )
            ROLLSTUHL -> WheelchairPushesRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                count = wert.toLong(), metadata = metadata,
            )
            AKTIVE_KALORIEN -> ActiveCaloriesBurnedRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                energy = Energy.kilocalories(wert), metadata = metadata,
            )
            GESAMT_KALORIEN -> TotalCaloriesBurnedRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                energy = Energy.kilocalories(wert), metadata = metadata,
            )
            WASSER -> HydrationRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                volume = Volume.milliliters(wert), metadata = metadata,
            )
            // Koffein fuehrt die Akte in Gramm, der Zettel meldet Milligramm.
            KOFFEIN -> NutritionRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                caffeine = Mass.grams(wert / 1000.0), metadata = metadata,
            )
            SCHLAF -> SleepSessionRecord(
                startTime = beginn, startZoneOffset = zone,
                endTime = ende, endZoneOffset = zoneEnde,
                metadata = metadata,
            )
        }
    }

    companion object {
        fun nachId(id: String): Satzart? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Welche Angaben ein Zettel fuer eine Satzart machen muss.
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
