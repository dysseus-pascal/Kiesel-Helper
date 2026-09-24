package ch.dysseus.kieselhelper

import android.content.Context
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Was diese App tut - fest eingebaut, nicht nachgeladen.
 *
 * VORHER STAND HIER EIN APPARAT. Zettel aus GitHub, ein Leser dafuer, ein
 * Regelwerk, ein Katalog von 22 Satzarten, ein Bildschirm zum Einbinden: rund
 * 1660 Zeilen, damit die App Dinge tun konnte, die ihr niemand beigebracht
 * hatte. Das war richtig gedacht fuer eine App, die andere benutzen. Diese
 * benutzt nur einer, und fuer ihn sind es vier Aufgaben - die stehen jetzt hier
 * und sind in einer Minute zu lesen.
 *
 * Drei kommen VON der Uhr: zwei davon gehen in die Gesundheitsakte, die dritte
 * (SupCycle) in den eigenen Speicher, weil die Akte fuer "genommen" keine
 * Satzart hat. Die vierte geht in die andere Richtung und steht in
 * [OsmandNavigation]: sie hat keine AppMessage als Anlass, sondern OsmAnds
 * Schnittstelle.
 */
object Aufgaben {

    private const val TAG = PebbleEmpfaenger.TAG

    // --- Drinktervall: getrunkenes Wasser ---

    private val DRINKTERVALL: UUID =
        UUID.fromString("5b0f7a3e-2c8d-4b61-9e4f-7d2a1c9b8e50")

    /**
     * Die Feldnummern ergeben sich aus der Reihenfolge der `messageKeys` in der
     * package.json der Uhr-App, beginnend bei 10000. Wer dort eine Zeile
     * dazwischenschiebt, verschiebt alle folgenden - und diese App traegt
     * danach still den falschen Wert ein.
     */
    private const val DT_GLASSES = 10001   //< das heutige Ziel in Glaesern, bei jeder Meldung
    private const val DT_GLASS_ML = 10008
    private const val DT_DRANK_AT = 10009

    // --- SupCycle: was heute ansteht und was davon genommen ist ---

    private val SUPCYCLE: UUID =
        UUID.fromString("33ce868a-2beb-4335-9421-4d741ed16eb3")

    /**
     * SupCycle schickt das ohnehin - fuer seine eigenen Timeline-Pins.
     *
     * Die Uhr meldet nach jeder Einnahme den Kalendertag und zwei Bitmasken:
     * was heute faellig ist und was davon abgehakt wurde. Namen kommen nicht
     * mit; die kennt nur die Konfigseite von SupCycle. Gezaehlt wird also,
     * nicht aufgelistet - und das ist genau die Zahl, die man taeglich wissen
     * will.
     */
    private const val SC_TODAY = 10039
    private const val SC_DUE = 10040
    private const val SC_TAKEN = 10041

    /**
     * Die Namen, seit SupCycle 0.10.0. Der Schluessel steht am ENDE der
     * messageKeys - haette er irgendwo dazwischen gestanden, waeren alle
     * folgenden Nummern verrutscht, und diese App traege still Unsinn ein.
     */
    private const val SC_NAMES = 10044


    // --- Kieselsport: ein beendetes Training ---

    private val KIESELSPORT: UUID =
        UUID.fromString("6c386250-a6d7-4a19-a485-52541b5d7c0d")

    private const val SP_ART = 10000
    private const val SP_BEGINN = 10001
    private const val SP_DAUER = 10002
    private const val SP_SCHRITTE = 10003
    private const val SP_METER = 10004
    private const val SP_KCAL = 10005
    private const val SP_PULS_MITTEL = 10006
    private const val SP_PULS_MAX = 10007

    /**
     * Die Sportarten in der Reihenfolge, in der Kieselsport sie zaehlt.
     *
     * Wer sie dort umsortiert, macht hier aus jedem Wandern ein
     * Krafttraining - rueckwirkend und lautlos.
     *
     * Der Titel geht in der Sprache des Telefons in die Akte - er ist das,
     * was man dort liest. Was schon drinsteht, bleibt, wie es war.
     */
    private fun artAlsSatzart(context: Context, art: Long): Pair<Int, String> = when (art.toInt()) {
        0 -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to context.getString(R.string.a_titel_laufen)
        // ZWEIMAL DIESELBE SATZART, ZWEI NAMEN. Die Gesundheitsakte kennt
        // nur ein Radfahren und kein Mountainbike; die Unterscheidung traegt
        // deshalb der Titel. Strasse und Gravel stehen zusammen - sie
        // unterscheiden sich im Reifen, nicht in dem, was die Uhr sieht.
        // "MTB" MUSS IN JEDER SPRACHE IM TITEL STEHEN: [Sportart.von] erkennt
        // das Mountainbike nur daran. Der SprachenTest haelt das fest.
        1 -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING to context.getString(R.string.a_titel_strasse)
        2 -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING to context.getString(R.string.a_titel_wandern)
        3 -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to context.getString(R.string.a_titel_kraft)
        4 -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING to context.getString(R.string.a_titel_mtb)
        5 -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA to context.getString(R.string.a_titel_yoga)
        6 -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to context.getString(R.string.a_titel_schwimmen)
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT to context.getString(R.string.training)
    }

    private const val SP_HRV = 10017
    // Die Pulskurve, stueckweise: ab welchem Wert, wie viele insgesamt, und
    // die Werte selbst - ein Byte je zehn Sekunden.
    private const val SP_KURVE_AB = 10020
    private const val SP_KURVE_ANZAHL = 10021
    private const val SP_KURVE = 10022
    private const val KURVE_TAKT_S = 10

    // DIE NACHT, seit Kieselsport 0.14.0 - wie die Pulskurve in Stuecken:
    // der Beginn der Nacht als Schluessel, ab welcher Minute, wie viele
    // insgesamt, und je Minute zwei Byte [bewegung, puls]. Die HRV-Fenster
    // fahren nur im ersten Stueck mit.
    private const val SP_NACHT_BEGINN = 10026
    private const val SP_NACHT_AB = 10027
    private const val SP_NACHT_ANZAHL = 10028
    private const val SP_NACHT_MINUTEN = 10029
    private const val SP_NACHT_HRV = 10030
    // Eine einzelne HRV-Messung aus dem Menue der Uhr: ihr Zeitpunkt, der
    // Wert steht in SP_HRV.
    private const val SP_HRV_ZEIT = 10031
    private const val SP_SAETZE = 10013
    private const val SP_REPS = 10014
    private const val SP_BAHNEN = 10015
    private const val SP_ABSCHNITTE = 10016

    /**
     * Ein Abschnitt eines Trainings: ein Satz beim Kraft, eine Bahn beim
     * Schwimmen.
     */
    internal data class Abschnitt(val ab: Long, val anzahl: Int, val dauer: Long)

    /**
     * Die Abschnittsliste der Uhr zerlegen: "beginn:anzahl:dauer;...".
     *
     * SIE KOMMT ALS TEXT, weil eine Liste in kein Zahlenfeld passt. Zerlegt
     * wird streng: was nicht aus drei Zahlen besteht, faellt weg. Eine halb
     * angekommene Zeile - der Postausgang der Uhr bricht ab, statt zu kuerzen -
     * waere sonst ein erfundener Satz.
     */
    internal fun abschnitteAus(text: String?): List<Abschnitt> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(";").mapNotNull { stueck ->
            if (stueck.isBlank()) return@mapNotNull null
            val teile = stueck.split(":")
            if (teile.size != 3) return@mapNotNull null
            val ab = teile[0].toLongOrNull() ?: return@mapNotNull null
            val anzahl = teile[1].toIntOrNull() ?: return@mapNotNull null
            val dauer = teile[2].toLongOrNull() ?: return@mapNotNull null
            if (ab < 0 || anzahl < 0 || dauer < 0) return@mapNotNull null
            Abschnitt(ab, anzahl, dauer)
        }
    }

    /**
     * Aus den Abschnitten Saetze fuer die Gesundheitsakte machen - samt Pausen.
     *
     * DIE PAUSEN STEHEN NICHT IN DER LISTE, sie ergeben sich aus den Luecken
     * dazwischen. Das ist der ganze Grund, die Zeiten mitzuschicken: "vier
     * Saetze" sagt wenig, "vier Saetze mit anderthalb Minuten dazwischen" ist
     * die Aussage.
     *
     * ALLES ODER NICHTS. Die Akte weist einen Satz zurueck, der ausserhalb der
     * Sitzung liegt oder sich mit dem naechsten ueberschneidet - und zwar den
     * GANZEN Eintrag. Lieber ohne Abschnitte eintragen als das Training
     * verlieren; deshalb wird am Ende geprueft und im Zweifel geleert.
     */
    private fun alsSegmente(
        abschnitte: List<Abschnitt>,
        anfang: Instant,
        ende: Instant,
    ): List<ExerciseSegment> {
        val aus = mutableListOf<ExerciseSegment>()
        var vorheriges: Instant? = null
        abschnitte.forEach { a ->
            val von = anfang.plusSeconds(a.ab)
            val bis = von.plusSeconds(a.dauer)
            if (von.isBefore(anfang) || bis.isAfter(ende) || !bis.isAfter(von)) return emptyList()
            vorheriges?.let { letztes ->
                if (von.isBefore(letztes)) return emptyList()
                // Die Pause dazwischen, aber nur wenn sie eine ist.
                if (von.isAfter(letztes)) {
                    aus += ExerciseSegment(
                        startTime = letztes,
                        endTime = von,
                        segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST,
                    )
                }
            }
            aus += ExerciseSegment(
                startTime = von,
                endTime = bis,
                // WELCHE UEBUNG ES WAR, WEISS DIE UHR NICHT. Sie sieht eine
                // Bewegung, keine Hantelbank; hier "Bankdruecken" hinzuschreiben
                // waere geraten.
                segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
                repetitions = a.anzahl,
            )
            vorheriges = bis
        }
        return aus
    }

    /** Aus den Abschnitten Bahnen machen - jede mit ihrer Laenge. */
    private fun alsBahnen(
        abschnitte: List<Abschnitt>,
        anfang: Instant,
        ende: Instant,
        beckenMeter: Double,
    ): List<ExerciseLap> {
        val aus = mutableListOf<ExerciseLap>()
        var vorheriges: Instant? = null
        abschnitte.forEach { a ->
            val von = anfang.plusSeconds(a.ab)
            val bis = von.plusSeconds(a.dauer)
            if (von.isBefore(anfang) || bis.isAfter(ende) || !bis.isAfter(von)) return emptyList()
            vorheriges?.let { if (von.isBefore(it)) return emptyList() }
            aus += ExerciseLap(
                startTime = von,
                endTime = bis,
                length = if (beckenMeter > 0) Length.meters(beckenMeter) else null,
            )
            vorheriges = bis
        }
        return aus
    }

    /**
     * Was in der Notiz steht, wenn die Uhr mehr wusste als Zeit und Puls.
     *
     * DIE LISTE GEHOERT DAZU. In der Akte stehen die Saetze einzeln, aber kein
     * Schirm zeigt sie so; "12/10/8/8" in einer Zeile liest jeder.
     */
    private fun abschnittsnotiz(
        context: Context,
        satzart: Int,
        felder: Map<Int, Long>,
        abschnitte: List<Abschnitt>,
    ): String? {
        val saetze = felder[SP_SAETZE] ?: 0
        val bahnen = felder[SP_BAHNEN] ?: 0
        return when {
            bahnen > 0 -> {
                val meter = felder[SP_METER] ?: 0
                val je = if (bahnen > 0) meter / bahnen else 0
                context.getString(R.string.a_bahnen, bahnen.toInt()) +
                    (if (je > 0) " " + context.getString(R.string.a_a_m, je.toInt()) else "")
            }
            saetze > 0 -> {
                val reps = felder[SP_REPS] ?: 0
                val liste = abschnitte.filter { it.anzahl > 0 }
                    .joinToString("/") { it.anzahl.toString() }
                val pausen = pausenSchnitt(abschnitte)
                buildString {
                    append(context.getString(R.string.a_saetze, saetze.toInt()))
                    if (liste.isNotBlank()) append(" ($liste)")
                    if (reps > 0) append(", " + context.getString(R.string.a_wdh, reps.toInt()))
                    if (pausen > 0) append(", " + context.getString(R.string.a_pause, pausen.toInt()))
                }
            }
            else -> null
        }
    }

    /** Der Schnitt der Luecken zwischen den Saetzen, in Sekunden. */
    internal fun pausenSchnitt(abschnitte: List<Abschnitt>): Long {
        if (abschnitte.size < 2) return 0
        var summe = 0L
        var zahl = 0
        for (i in 1 until abschnitte.size) {
            val ende = abschnitte[i - 1].ab + abschnitte[i - 1].dauer
            val luecke = abschnitte[i].ab - ende
            if (luecke in 1..1800) {
                summe += luecke
                zahl++
            }
        }
        return if (zahl > 0) summe / zahl else 0
    }

    private const val SP_ZUSTAND = 10009

    /**
     * Ein Training - Zustandsmeldung oder Zusammenfassung.
     *
     * ZWEI ARTEN VON NACHRICHT IN EINER AUFGABE. Beim Start, bei Pause und
     * beim Weiter kommt nur ein Zustand; am Ende die ganze Zusammenfassung.
     * Zu unterscheiden sind sie an der Dauer: die gibt es nur am Ende.
     */
    private suspend fun training(
        context: Context,
        felder: Map<Int, Long>,
        texte: Map<Int, String>,
        rohdaten: Map<Int, ByteArray>,
    ): String? {
        // EIN STUECK PULSKURVE - kommt nach der Zusammenfassung, in Stuecken
        // von bis zu 300 Werten. Gesammelt wird je Training; ist das letzte
        // Stueck da, geht die Kurve in die Akte.
        rohdaten[SP_KURVE]?.let { werte ->
            val beginn = felder[SP_BEGINN] ?: return null
            val ab = (felder[SP_KURVE_AB] ?: 0).toInt()
            val anzahl = (felder[SP_KURVE_ANZAHL] ?: 0).toInt()
            werte.forEachIndexed { i, b ->
                Pulskurve.anhaengen(context, beginn, (ab + i) * KURVE_TAKT_S, b.toInt() and 0xFF)
            }
            return if (ab + werte.size >= anzahl) {
                if (Pulskurve.eintragen(context, beginn)) null
                else context.getString(R.string.a_pulskurve_nicht)
            } else {
                null
            }
        }

        // EIN STUECK NACHT - morgens, in Stuecken von bis zu 150 Minuten.
        rohdaten[SP_NACHT_MINUTEN]?.let { minuten ->
            return nachtstueck(context, felder, minuten, rohdaten[SP_NACHT_HRV])
        }

        // EINE HRV VON HAND GEMESSEN - ohne Art, mit Zeitpunkt.
        if (felder[SP_ART] == null) felder[SP_HRV_ZEIT]?.let { wann -> return hrvEinzeln(context, felder, wann) }

        // DIE EINSTELLUNGEN DER UHR - beim Start der App und nach jeder
        // Aenderung, ohne Art. Nur merken; ins Protokoll gehoeren sie nicht.
        if (felder[SP_ART] == null && Uhreinstellungen.vonKieselsport(context, felder, texte)) return null

        val zustand = felder[SP_ZUSTAND]
        val dauer = felder[SP_DAUER]

        if (dauer == null) {
            // Eine blosse Zustandsmeldung: sie steuert nur die Aufzeichnung.
            val beginn = felder[SP_BEGINN] ?: return null
            val artNummer = felder[SP_ART] ?: -1
            val (_, name) = artAlsSatzart(context, artNummer)
            // NUR WO ES EINE STRECKE GIBT, laeuft das GPS: Laufen, Bike,
            // Wandern, MTB. Beim Kraft in der Halle, beim Yoga und im Becken
            // zeichnete es nur Rauschen auf - und kostete den Akku.
            val mitStrecke = artNummer in setOf(0L, 1L, 2L, 4L)
            return when (zustand) {
                1L -> {
                    if (mitStrecke) SpurDienst.starte(context, beginn, name)
                    context.getString(R.string.a_begonnen, name)
                }
                2L -> { SpurDienst.stoppe(context); context.getString(R.string.a_pausiert, name) }
                3L -> {
                    if (mitStrecke) SpurDienst.starte(context, beginn, name)
                    context.getString(R.string.a_fortgesetzt, name)
                }
                0L -> { SpurDienst.stoppe(context); null }
                else -> null
            }
        }

        SpurDienst.stoppe(context)
        return trage_ein(context, felder, texte, dauer)
    }

    private suspend fun trage_ein(
        context: Context,
        felder: Map<Int, Long>,
        texte: Map<Int, String>,
        dauer: Long,
    ): String? {
        val beginn = felder[SP_BEGINN] ?: return null
        if (beginn <= 0 || dauer < 60) return null
        if (!Riegel.neu(context, "kieselsport", beginn.toString())) return null

        val klient = Akte(context).bereit()
        if (klient == null) {
            Riegel.loese(context, "kieselsport")
            return context.getString(R.string.g_akte_fehlt)
        }
        val anfang = Instant.ofEpochSecond(beginn)
        val ende = anfang.plusSeconds(dauer)

        // Traegt schon jemand anders eine Sitzung ueber dieselbe Zeit ein?
        schonDa(context, klient, ExerciseSessionRecord::class, anfang, FENSTER_TRAINING)?.let {
            Log.i(TAG, "Training steht schon da, von " + it)
            return context.getString(R.string.a_uebersprungen_training, it)
        }

        val (satzart, name) = artAlsSatzart(context, felder[SP_ART] ?: -1)
        val puls = felder[SP_PULS_MITTEL] ?: 0
        val kcal = felder[SP_KCAL] ?: 0

        // DIE STRECKE KOMMT VOM TELEFON, nicht von der Uhr. Sie geht als Route
        // an die Sitzung - dort gehoert sie hin, und von dort liest sie jede
        // App, die Routen zeigt.
        val punkte = withContext(Dispatchers.IO) { Spur.lies(context, beginn) }
        val strecke = if (punkte.size >= 2) Spur.laenge(punkte) else 0.0
        val route = if (punkte.size >= 2) {
            ExerciseRoute(punkte.map { p ->
                ExerciseRoute.Location(
                    time = Instant.ofEpochSecond(p.zeit),
                    latitude = p.lat,
                    longitude = p.lon,
                    horizontalAccuracy = Length.meters(p.genauigkeit.toDouble()),
                    altitude = p.hoehe?.let { Length.meters(it) },
                )
            })
        } else {
            null
        }

        // WAS DIE UHR GEZAEHLT HAT, jeder Abschnitt einzeln: ein Satz beim
        // Krafttraining, eine Bahn beim Schwimmen. Die Akte hat fuer beides
        // ein Feld - Wiederholungen am Satz, Laenge an der Bahn.
        val abschnitte = abschnitteAus(texte[SP_ABSCHNITTE])
        val bahnen = felder[SP_BAHNEN] ?: 0
        val becken = if (bahnen > 0) (felder[SP_METER] ?: 0).toDouble() / bahnen else 0.0
        val segmente =
            if (bahnen > 0) emptyList() else alsSegmente(abschnitte, anfang, ende)
        val runden =
            if (bahnen > 0) alsBahnen(abschnitte, anfang, ende, becken) else emptyList()

        val notiz = buildString {
            if (puls > 0) append(context.getString(R.string.a_puls_schnitt, puls.toInt()))
            felder[SP_PULS_MAX]?.takeIf { it > 0 }?.let { append(", " + context.getString(R.string.a_max, it.toInt())) }
            if (strecke > 0) {
                append(", ")
                append(Zahlen.eine(strecke / 1000) ?: "")
                append(" km (GPS)")
            } else {
                felder[SP_METER]?.takeIf { it > 0 }?.let {
                    append(", " + (Zahlen.eine(it / 100 / 10.0) ?: "") + " km")
                }
            }
            if (kcal > 0) append(", $kcal kcal")
            abschnittsnotiz(context, satzart, felder, abschnitte)?.let {
                if (isNotEmpty()) append(" — ")
                append(it)
            }
        }.ifBlank { null }

        // ERST MIT ALLEM, DANN OHNE DIE ABSCHNITTE. Die Akte prueft Saetze
        // und Bahnen streng - Ueberschneidung, Reihenfolge, ob die Satzart
        // zur Sportart passt -, und weist im Zweifel den GANZEN Eintrag ab.
        // Ein Krafttraining, das deshalb nie in der Akte steht, ist
        // schlimmer als eines ohne seine Saetze: die stehen wenigstens noch
        // in der Notiz. Also zweiter Anlauf ohne sie.
        val satz = try {
            ExerciseSessionRecord(
                startTime = anfang,
                startZoneOffset = null,
                endTime = ende,
                endZoneOffset = null,
                exerciseType = satzart,
                title = name,
                // Was nicht als eigener Satz in die Akte geht, steht wenigstens
                // daneben: die Zahlen der Uhr, unveraendert.
                notes = notiz,
                metadata = vonDerUhr("kieselsport-" + beginn),
                segments = segmente,
                laps = runden,
                exerciseRoute = route,
            )
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Abschnitte abgewiesen, Training geht ohne sie: " + e.message)
            null
        }
        val ohne = ExerciseSessionRecord(
            startTime = anfang,
            startZoneOffset = null,
            endTime = ende,
            endZoneOffset = null,
            exerciseType = satzart,
            title = name,
            notes = notiz,
            metadata = vonDerUhr("kieselsport-" + beginn),
            exerciseRoute = route,
        )
        val hatAbschnitte = satz != null && (segmente.isNotEmpty() || runden.isNotEmpty())
        val kurz = buildString {
            // Name und Dauer vorn, das Weitere als Liste dahinter - der Platzhalter
            // nimmt sie mitsamt ihren Kommas.
            val dazu = buildString {
                if (strecke > 0) append(", " + (Zahlen.eine(strecke / 1000) ?: "") + " km")
                if (bahnen > 0) append(", " + context.getString(R.string.a_bahnen, bahnen.toInt()))
                if (segmente.isNotEmpty()) {
                    append(", " + context.getString(R.string.a_saetze, (felder[SP_SAETZE] ?: 0).toInt()))
                }
            }
            append(context.getString(R.string.a_eingetragen, name, (dauer / 60).toInt(), dazu))
        }
        var meldung = schreibe(context, satz ?: ohne, kurz, riegel = "kieselsport")
        if (hatAbschnitte && meldung == context.getString(R.string.a_nicht_eingetragen)) {
            Log.w(TAG, "Eintrag mit Abschnitten abgewiesen, zweiter Anlauf ohne")
            meldung = schreibe(context, ohne, context.getString(R.string.a_ohne_abschnitte, kurz),
                riegel = "kieselsport")
        }

        // YOGA BRINGT EINE HRV MIT. Sie gehoert als eigener Satz in die Akte,
        // zum Ende des Trainings - so wie die naechtliche.
        felder[SP_HRV]?.takeIf { it in 5..300 }?.let { hrv ->
            if (Riegel.neu(context, "kieselsport-hrv", beginn.toString())) {
                schreibe(context, HeartRateVariabilityRmssdRecord(
                    time = ende,
                    zoneOffset = null,
                    heartRateVariabilityMillis = hrv.toDouble(),
                    metadata = vonDerUhr("kieselsport-hrv-" + beginn),
                ), context.getString(R.string.a_hrv_eingetragen, hrv.toInt()), riegel = "kieselsport-hrv")
                meldung += ", " + context.getString(R.string.a_hrv, hrv.toInt())
            }
        }

        // Die Pulskurve, falls die Uhr sie schon geliefert hat. Kommt sie
        // spaeter, traegt der Datenlog-Empfaenger sie selbst ein.
        if (Pulskurve.eintragen(context, beginn)) meldung += ", " + context.getString(R.string.a_pulskurve)
        return meldung
    }

    /**
     * Ein Stueck der Nacht einsetzen - und ist sie damit vollstaendig, sie
     * auswerten und eintragen.
     *
     * NACHZUEGLER NACH DEM EINTRAGEN werden verworfen: bleibt die Bestaetigung
     * des letzten Stuecks aus, schickt die Uhr es noch einmal, und es begaenne
     * sonst eine neue, nie vollstaendige Nacht.
     */
    private suspend fun nachtstueck(
        context: Context,
        felder: Map<Int, Long>,
        minuten: ByteArray,
        hrv: ByteArray?,
    ): String? {
        val beginn = felder[SP_NACHT_BEGINN] ?: return null
        val anzahl = (felder[SP_NACHT_ANZAHL] ?: 0).toInt()
        val ab = (felder[SP_NACHT_AB] ?: 0).toInt()
        if (beginn <= 0 || anzahl <= 0) return null
        if (Riegel.schon(context, "schlaf", beginn.toString())) return null
        val voll = withContext(Dispatchers.IO) {
            Nachtdaten.einsetzen(context, beginn, anzahl, ab, minuten, if (ab == 0) hrv else null)
        }
        if (!voll) return null
        // MIT IHR AUCH DIE, DIE FRUEHER SCHEITERTEN - Akte nicht da, Erlaubnis
        // fehlte. Ihre Dateien liegen noch; die aelteste zuerst, damit der
        // Riegel am Ende auf der neuesten steht.
        val offen = withContext(Dispatchers.IO) { Nachtdaten.vollstaendige(context) }
        return (offen - beginn).sorted().plus(beginn)
            .mapNotNull { nachtEintragen(context, it) }
            .joinToString(" · ")
            .ifBlank { null }
    }

    /**
     * Die Nacht auswerten und in die Akte: Schlaf mit Phasen, Ruhepuls, HRV.
     *
     * DIE EIGENE AUSWERTUNG GILT, auch wenn die Pebble-App schon einen Schlaf
     * eingetragen hat. Hier wird darum NICHT nachgesehen wie beim Wasser;
     * doppelt gezaehlt wird trotzdem nichts, weil [Gesundheit] beim Lesen
     * fuer jede Nacht nur die eigenen Sitzungen nimmt, wo es welche gibt.
     *
     * Ruhepuls und HRV stehen zum Aufwachen: dort sucht jede App den Wert
     * "dieser Nacht", und dort stand er auch bei Herzintervall.
     */
    private suspend fun nachtEintragen(context: Context, beginn: Long): String? {
        if (!Riegel.neu(context, "schlaf", beginn.toString())) return null
        val daten = withContext(Dispatchers.IO) { Nachtdaten.lies(context, beginn) } ?: return null
        val (bewegung, puls, hrvBytes) = daten
        val nacht = Schlafanalyse.werte(bewegung, puls, Schlafanalyse.hrvAus(hrvBytes))
        if (nacht == null) {
            // Keine Nacht darin - die Uhr lag auf dem Tisch. Das ist eine
            // Antwort, kein Fehler: die Daten sind fertig ausgewertet.
            withContext(Dispatchers.IO) { Nachtdaten.loesche(context, beginn) }
            return context.getString(R.string.a_nacht_keine)
        }

        val null0 = Instant.ofEpochSecond(beginn)
        fun zeit(minute: Int): Instant = null0.plusSeconds(minute * 60L)
        val aufgewacht = zeit(nacht.aufwachen)
        val satz = SleepSessionRecord(
            startTime = zeit(nacht.einschlafen),
            startZoneOffset = null,
            endTime = aufgewacht,
            endZoneOffset = null,
            title = context.getString(R.string.a_schlaf_titel),
            metadata = vonDerUhr("kieselsport-schlaf-" + beginn),
            stages = nacht.phasen.map {
                SleepSessionRecord.Stage(zeit(it.von), zeit(it.bis), stufe(it.phase))
            },
        )
        val kurz = nachtMeldung(context, nacht)
        val meldung = schreibe(context, satz, kurz, riegel = "schlaf")
        // Gescheitert: die Datei bleibt liegen, der Riegel ist wieder offen -
        // mit dem naechsten Stueck einer Nacht kommt sie noch einmal dran.
        if (meldung != kurz) return meldung
        val teile = mutableListOf(meldung)

        nacht.ruhepuls?.takeIf { it in 30..120 }?.let { ruhe ->
            if (Riegel.neu(context, "ruhepuls", beginn.toString())) {
                teile += schreibe(context, RestingHeartRateRecord(
                    time = aufgewacht,
                    zoneOffset = null,
                    beatsPerMinute = ruhe.toLong(),
                    metadata = vonDerUhr("kieselsport-ruhepuls-" + beginn),
                ), context.getString(R.string.a_ruhepuls_eingetragen, ruhe), riegel = "ruhepuls")
            }
        }
        nacht.hrv?.takeIf { it in 5..300 }?.let { ms ->
            if (Riegel.neu(context, "nacht-hrv", beginn.toString())) {
                teile += schreibe(context, HeartRateVariabilityRmssdRecord(
                    time = aufgewacht,
                    zoneOffset = null,
                    heartRateVariabilityMillis = ms.toDouble(),
                    metadata = vonDerUhr("kieselsport-nacht-hrv-" + beginn),
                ), context.getString(R.string.a_hrv_eingetragen, ms), riegel = "nacht-hrv")
            }
        }
        withContext(Dispatchers.IO) { Nachtdaten.loesche(context, beginn) }
        return teile.joinToString(", ")
    }

    /** Die Phasen der Auswertung in die Satzarten der Akte. */
    private fun stufe(p: Schlafanalyse.Phase): Int = when (p) {
        Schlafanalyse.Phase.WACH -> SleepSessionRecord.STAGE_TYPE_AWAKE
        Schlafanalyse.Phase.LEICHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
        Schlafanalyse.Phase.TIEF -> SleepSessionRecord.STAGE_TYPE_DEEP
        Schlafanalyse.Phase.REM -> SleepSessionRecord.STAGE_TYPE_REM
        Schlafanalyse.Phase.SCHLAF -> SleepSessionRecord.STAGE_TYPE_SLEEPING
    }

    /** "Schlaf 7 h 12, Tief 1 h 20, REM 1 h 35" - oder ohne Puls nur die Dauer. */
    private fun nachtMeldung(context: Context, n: Schlafanalyse.Nacht): String {
        val schlaf = n.schlafMinuten
        if (n.phasen.none { it.phase == Schlafanalyse.Phase.TIEF || it.phase == Schlafanalyse.Phase.REM ||
                it.phase == Schlafanalyse.Phase.LEICHT }) {
            return context.getString(R.string.a_schlaf_eingetragen, schlaf / 60, schlaf % 60)
        }
        fun hm(min: Int) = "%d h %02d".format(min / 60, min % 60)
        return context.getString(
            R.string.a_nacht_phasen,
            hm(schlaf), hm(n.minuten(Schlafanalyse.Phase.TIEF)), hm(n.minuten(Schlafanalyse.Phase.REM)),
        )
    }

    /**
     * Eine HRV, von Hand auf der Uhr gemessen (Menuepunkt "HRV" in
     * Kieselsport): ein Satz zu ihrem Zeitpunkt, und je Zeitpunkt einmal.
     */
    private suspend fun hrvEinzeln(context: Context, felder: Map<Int, Long>, wann: Long): String? {
        val ms = felder[SP_HRV] ?: return null
        if (wann <= 0 || ms !in 5..300) return null
        if (!Riegel.neu(context, "kieselsport-hrv-einzeln", wann.toString())) return null
        return schreibe(context, HeartRateVariabilityRmssdRecord(
            time = Instant.ofEpochSecond(wann),
            zoneOffset = null,
            heartRateVariabilityMillis = ms.toDouble(),
            metadata = vonDerUhr("kieselsport-hrv-zeit-" + wann),
        ), context.getString(R.string.a_hrv_eingetragen, ms.toInt()), riegel = "kieselsport-hrv-einzeln")
    }

    /** Alle Uhr-Apps, von denen diese App ueberhaupt etwas annimmt. */
    //
    // HERZINTERVALL NICHT MEHR: seit Kieselsport 0.14.0 misst Kieselsport die
    // Nacht selbst. Eine zweite App, die dieselbe HRV eintraegt, gaebe nur
    // doppelte Werte - und wer Herzintervall noch installiert hat, soll
    // nicht still zwei Naechte in der Akte finden.
    val BEKANNTE_UHREN = setOf(DRINKTERVALL, SUPCYCLE, KIESELSPORT)

    /** Die Berechtigungen, die dafuer noetig sind. */
    val BERECHTIGUNGEN: Set<String> = setOf(
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        // Fuer Koffein und Praeparate: die Akte fuehrt beides als Ernaehrung.
        HealthPermission.getWritePermission(NutritionRecord::class),
        // Fuer die Trainingssitzungen von Kieselsport.
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        // Die Pulskurve zum Training, die Nacht und der Ruhepuls von der Uhr.
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(RestingHeartRateRecord::class),
    )

    /** Fuer den Datenlog-Empfaenger: nur Kieselsport schreibt Logs. */
    val KIESELSPORT_UUID: UUID get() = KIESELSPORT
    /** Fuer die Einstellungen, die von hier an die Uhr gehen. */
    val DRINKTERVALL_UUID: UUID get() = DRINKTERVALL
    val SUPCYCLE_UUID: UUID get() = SUPCYCLE

    /**
     * Eine Nachricht von der Uhr verarbeiten.
     *
     * Rueckgabe ist ein Satz fuer den Verlauf, oder null wenn nichts geschah.
     * Kein Fehlercode: die App besteht im Kern aus einem Empfaenger, und ohne
     * diese Zeile saehe man ihr von aussen nie an, ob sie etwas tut.
     */
    suspend fun verarbeite(
        context: Context,
        von: UUID,
        felder: Map<Int, Long>,
        texte: Map<Int, String> = emptyMap(),
        rohdaten: Map<Int, ByteArray> = emptyMap(),
    ): String? =
        when (von) {
            DRINKTERVALL -> wasser(context, felder)
            SUPCYCLE -> {
                // Plan und Animation, wie sie auf der Uhr gelten - mit der
                // Tagesmeldung und beim Start der App.
                Uhreinstellungen.vonSupCycle(context, felder, rohdaten)
                supplemente(context, felder, texte)
            }
            KIESELSPORT -> training(context, felder, texte, rohdaten)
            else -> null
        }

    /**
     * Supplemente festhalten - die Quote im EIGENEN Speicher, jedes genommene
     * als Ernaehrungssatz mit Namen in der Akte.
     *
     * DIE AKTE KENNT KEIN "GENOMMEN". Was ihr am naechsten kommt, ist ein
     * Ernaehrungssatz - der bekommt den Namen, aber keine Naehrstoffmassen,
     * denn die weiss SupCycle nicht: ein Plan dort besteht aus Namen und
     * Zyklen, nicht aus Milligramm. Eine Zahl zu erfinden, damit sie in eine
     * fremde Tabelle passt, waere der schlechteste aller Wege.
     *
     * Gezaehlt wird, was FAELLIG war und davon genommen wurde. Ein Praeparat,
     * das heute pausiert, gehoert in keine Quote.
     */
    private suspend fun supplemente(
        context: Context,
        felder: Map<Int, Long>,
        texte: Map<Int, String>,
    ): String? {
        val ymd = felder[SC_TODAY] ?: return null
        val faellig = felder[SC_DUE] ?: return null
        val genommen = felder[SC_TAKEN] ?: 0L

        val tag = try {
            LocalDate.of(
                (ymd / 10000).toInt(), ((ymd / 100) % 100).toInt(), (ymd % 100).toInt()
            )
        } catch (e: Exception) {
            Log.w(TAG, "SupCycle: unbrauchbares Datum " + ymd); return null
        }

        val wieViele = java.lang.Long.bitCount(faellig)
        // UND, nicht blosses Zaehlen: abgehakt bleibt abgehakt, auch wenn ein
        // Praeparat heute gar nicht dran waere. In der Quote hat es dann
        // nichts verloren.
        val davon = java.lang.Long.bitCount(genommen and faellig)

        if (!Riegel.neu(context, "supcycle", "$ymd:$faellig:$genommen")) return null

        // WAS NEU ABGEHAKT WURDE, geht in die Akte - je Praeparat ein
        // Ernaehrungssatz mit Namen. Nur die neuen: der Zeitpunkt soll der
        // des Hakens sein, nicht der des naechsten Berichts.
        val namen = texte[SC_NAMES]?.split("\n").orEmpty()
        val vorher = Supplemente.lies(context)?.takeIf { it.tag == tag }?.genommen ?: 0L
        val neuGenommen = genommen and faellig and vorher.inv()
        for (platz in 0 until 64) {
            if (((neuGenommen shr platz) and 1L) == 0L) continue
            val name = namen.getOrNull(platz)?.ifBlank { null } ?: context.getString(R.string.a_praeparat_n, platz + 1)
            praeparat(context, name, tag, platz, Instant.now())
        }

        // Die Namen samt Bitmasken fuer die Liste von HEUTE. Sie stehen
        // nicht im Tagesspeicher: dort gehoert je Tag eine Zahl hin, und
        // eine Liste abgehakter Praeparate von vorletztem Dienstag hat
        // niemand je gebraucht.
        Supplemente.merke(context, tag, namen, faellig, genommen)

        withContext(Dispatchers.IO) {
            Speicher(context).merke(tag, mapOf(
                "supp_faellig" to wieViele.toDouble(),
                "supp_genommen" to davon.toDouble(),
            ))
        }
        GesundheitWidget.stosseAn(context)
        return context.getString(R.string.a_davon_von, davon, wieViele)
    }

    /**
     * Getrunkenes Wasser eintragen.
     *
     * NICHT ZWEIMAL FUER DENSELBEN AUGENBLICK. Die Uhr schickt ihren Stand bei
     * jeder Gelegenheit mit, nicht nur beim Trinken; ohne diesen Riegel stuende
     * ein Glas mehrfach in der Akte. Der Zeitpunkt des Trinkens ist der
     * natuerliche Schluessel dafuer.
     */
    private suspend fun wasser(context: Context, felder: Map<Int, Long>): String? {
        // DAS ZIEL KOMMT MIT JEDER MELDUNG, auch ohne Glas: so zieht ein
        // "Ziel+" auf der Uhr sofort in Reiter und Widget nach.
        felder[DT_GLASSES]?.let { neu ->
            val vorher = Einstellungen.wasserGlaeser(context)
            Einstellungen.merkeWasserziel(context, neu.toInt())
            if (Einstellungen.wasserGlaeser(context) != vorher) GesundheitWidget.stosseAn(context)
        }
        // DIE GLASGROESSE NUR OHNE GLAS: traegt die Meldung ein Glas, steht
        // in GLASS_ML dessen Menge - und die kann eine andere sein.
        val mitGlas = felder[DT_DRANK_AT] != null
        if (!mitGlas) felder[DT_GLASS_ML]?.let { Einstellungen.merkeGlasgroesse(context, it.toInt()) }
        Uhreinstellungen.vonDrinktervall(context, felder, mitGlas)

        val ml = felder[DT_GLASS_ML] ?: return null
        val wann = felder[DT_DRANK_AT] ?: return null
        if (ml <= 0 || wann <= 0) return null
        if (!Riegel.neu(context, "drinktervall", wann.toString())) return null

        val beginn = Instant.ofEpochSecond(wann)
        val klient = Akte(context).bereit()
        if (klient == null) {
            Riegel.loese(context, "drinktervall")
            return context.getString(R.string.g_akte_fehlt)
        }
        schonDa(context, klient, HydrationRecord::class, beginn, FENSTER_WASSER)?.let {
            Log.i(TAG, "Wasser steht schon da, von " + it)
            return context.getString(R.string.a_uebersprungen_glas, it)
        }
        val satz = HydrationRecord(
            startTime = beginn,
            startZoneOffset = null,
            // Eine Minute Dauer: die Akte will eine Spanne, und ein Glas
            // trinkt sich nicht in einem Augenblick.
            endTime = beginn.plusSeconds(60),
            endZoneOffset = null,
            volume = Volume.milliliters(ml.toDouble()),
            metadata = vonDerUhr("drinktervall-" + wann),
        )
        return schreibe(context, satz, context.getString(R.string.a_ml_eingetragen, ml.toInt()), riegel = "drinktervall")
    }

    /**
     * Die Herkunft des Satzes: automatisch erfasst, von einer Uhr.
     *
     * Ohne das steht die Messung in der Akte da, als haette man sie von Hand
     * eingetippt - und man kann sie spaeter nicht mehr von einer echten
     * Eingabe unterscheiden.
     */

    // --- Was von Hand kommt, aber trotzdem in die Akte gehoert ---

    /**
     * Ein koffeinhaltiges Getraenk eintragen.
     *
     * DIE AKTE HAT DAFUER EINEN PLATZ: ein Ernaehrungssatz traegt ein Feld
     * `caffeine`. Lange stand das Koffein nur in der eigenen Tabelle, mit dem
     * Hinweis, ein Schreibrecht mehr sei es nicht wert. Das war die falsche
     * Abwaegung - eingetragen nuetzt es auch jeder anderen App und ueberlebt
     * eine Neuinstallation.
     *
     * MANUELL und nicht automatisch aufgezeichnet: hier hat jemand einen Knopf
     * gedrueckt. Die Akte unterscheidet das, und ein Kaffee, der sich als
     * Messung ausgibt, waere eine kleine Luege.
     *
     * Die Kennung traegt den Augenblick - wer zweimal tippt, hat zweimal
     * getrunken, und das sollen auch zwei Saetze sein.
     */
    suspend fun koffein(context: Context, mg: Int, zeitpunkt: Instant): String? {
        val satz = NutritionRecord(
            startTime = zeitpunkt,
            startZoneOffset = null,
            endTime = zeitpunkt.plusSeconds(60),
            endZoneOffset = null,
            caffeine = Mass.grams(mg / 1000.0),
            metadata = vonHand("koffein-" + zeitpunkt.epochSecond),
        )
        return schreibe(context, satz, context.getString(R.string.a_koffein_eingetragen, mg))
    }

    /**
     * Ein genommenes Praeparat eintragen.
     *
     * OHNE NAEHRSTOFFMENGEN, nur mit Namen. Ein Ernaehrungssatz ist der
     * einzige Platz, den die Akte dafuer hat, und SupCycle kennt Namen und
     * Zyklen, keine Milligramm. Eine Menge zu erfinden, damit das Feld
     * gefuellt ist, waere schlimmer als ein leeres Feld: sie taeuchte
     * Genauigkeit vor, die es nirgends gibt.
     *
     * Die Kennung ist TAG PLUS PLATZ, nicht die Uhrzeit. SupCycle meldet
     * seinen Stand nach jedem Haken neu; mit dieser Kennung ersetzt der
     * zweite Bericht den ersten, statt dasselbe Magnesium ein zweites Mal
     * einzutragen.
     */
    private suspend fun praeparat(
        context: Context,
        name: String,
        tag: LocalDate,
        platz: Int,
        zeitpunkt: Instant,
    ) {
        val satz = NutritionRecord(
            startTime = zeitpunkt,
            startZoneOffset = null,
            endTime = zeitpunkt.plusSeconds(60),
            endZoneOffset = null,
            name = name,
            metadata = vonHand("supcycle-" + tag + "-" + platz),
        )
        try {
            Akte(context).bereit()?.insertRecords(listOf(satz))
        } catch (e: Exception) {
            Log.w(TAG, "Praeparat nicht eingetragen: " + e.message)
        }
    }

    /**
     * Die Kennung, an der die Akte einen Eintrag WIEDERERKENNT.
     *
     * DAS IST DER EIGENTLICHE SCHUTZ GEGEN DOPPELTE. Health Connect fuehrt
     * Eintraege mit derselben `clientRecordId` derselben App zusammen: wird
     * einer zweimal geschrieben, ERSETZT der zweite den ersten, statt
     * danebenzustehen. Der [Riegel] daneben spart nur die Abfrage - er liegt
     * in den Einstellungen der App und ist weg, sobald jemand deren Daten
     * loescht. Die Kennung ueberlebt das.
     *
     * Sie muss deshalb aus dem EREIGNIS kommen und nicht aus der Uhrzeit des
     * Schreibens: derselbe Schluck Wasser ergibt dieselbe Kennung, auch wenn
     * die Uhr ihn eine Stunde spaeter noch einmal meldet.
     */
    private fun vonDerUhr(kennung: String): Metadata =
        Metadata.autoRecorded(Device(type = Device.TYPE_WATCH), kennung)

    private fun vonHand(kennung: String): Metadata =
        Metadata.manualEntryWithId(kennung, Device(type = Device.TYPE_PHONE))

    /**
     * Schreibt schon jemand anderes dasselbe?
     *
     * DIE PEBBLE-APP TRAEGT SELBST EIN - Schritte, Schlaf, Puls, womoeglich
     * auch die Herzratenvariabilitaet. Zwei Apps, die dieselbe Messung
     * eintragen, ergeben ZWEI Saetze: die Akte fuehrt nur zusammen, was aus
     * DERSELBEN App mit derselben Kennung kommt. Dagegen hilft keine Kennung,
     * nur Nachsehen.
     *
     * Gefunden wird der Paketname des Fremden, sonst null. Ein eigener
     * frueherer Satz zaehlt nicht als fremd - den ersetzt die Kennung.
     */
    private suspend fun <T : Record> schonDa(
        context: Context,
        klient: HealthConnectClient,
        klasse: kotlin.reflect.KClass<T>,
        zeit: Instant,
        fenster: Duration,
    ): String? = try {
        klient.readRecords(
            ReadRecordsRequest(
                klasse,
                TimeRangeFilter.between(zeit.minus(fenster), zeit.plus(fenster)),
            )
        ).records
            .map { it.metadata.dataOrigin.packageName }
            .firstOrNull { it.isNotEmpty() && it != context.packageName }
    } catch (e: Exception) {
        Log.w(TAG, "Nachsehen fehlgeschlagen: " + e.message)
        null
    }

    /**
     * Wie weit zwei Eintraege auseinanderliegen duerfen und trotzdem
     * derselbe sind.
     *
     * Beim Wasser eng - zwei Glaeser in fuenf Minuten sind moeglich, zwei
     * Eintraege in derselben Minute nicht.
     */
    private val FENSTER_WASSER: Duration = Duration.ofMinutes(1)
    // Ein Training beginnt man nicht zweimal in derselben Viertelstunde.
    private val FENSTER_TRAINING: Duration = Duration.ofMinutes(15)

    /**
     * In die Akte schreiben - und bei einem Fehlschlag den Riegel wieder
     * oeffnen.
     *
     * DER RIEGEL FAELLT VOR DEM SCHREIBEN, damit ein zweiter Bericht derselben
     * Messung nicht dazwischenkommt. Scheitert das Schreiben aber - Akte
     * nicht da, Erlaubnis fehlt -, bliebe er zu, und der naechste Versuch der
     * Uhr liefe ins Leere: die Messung waere fuer immer weg. Deshalb wird er
     * hier wieder geoeffnet.
     */
    private suspend fun schreibe(
        context: Context,
        satz: Record,
        meldung: String,
        riegel: String? = null,
    ): String {
        val klient = Akte(context).bereit()
        if (klient == null) {
            riegel?.let { Riegel.loese(context, it) }
            return context.getString(R.string.g_akte_fehlt)
        }
        return try {
            klient.insertRecords(listOf(satz))
            // Das Widget zeigt Wasser; ein Glas, das erst in einer halben
            // Stunde dort ankommt, sieht aus wie ein verschlucktes.
            GesundheitWidget.stosseAn(context)
            meldung
        } catch (e: Exception) {
            // Die haeufigste Ursache ist eine fehlende Erlaubnis. Sie zu
            // nennen ist nuetzlicher als "Fehler".
            Log.w(TAG, "Eintragen fehlgeschlagen: " + e.message)
            riegel?.let { Riegel.loese(context, it) }
            context.getString(R.string.a_nicht_eingetragen)
        }
    }
}

/**
 * Der Riegel gegen Doppeleintraege.
 *
 * Die Uhr schickt ihren Stand bei jeder Gelegenheit mit, nicht nur beim
 * Ereignis. Ohne diesen Riegel stuende dasselbe Glas mehrfach in der
 * Gesundheitsakte - und ein Eintrag, den niemand gemacht hat, ist schlimmer
 * als ein fehlender: er sieht aus wie eine Messung.
 *
 * Gemerkt wird je Aufgabe EIN Merkmal, naemlich das zuletzt verarbeitete.
 * Mehr braucht es nicht: die Werte kommen in der Zeit vorwaerts, und ein
 * Nachzuegler von vorgestern waere ohnehin keiner.
 */
object Riegel {
    private const val DATEI = "kiesel-riegel"

    /** Schon dagewesen? Nur nachsehen, nicht vormerken. */
    fun schon(context: Context, aufgabe: String, merkmal: String): Boolean =
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).getString(aufgabe, null) == merkmal

    /** true heisst: noch nicht dagewesen, jetzt vormerken. */
    fun neu(context: Context, aufgabe: String, merkmal: String): Boolean {
        val p = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
        if (p.getString(aufgabe, null) == merkmal) return false
        p.edit().putString(aufgabe, merkmal).apply()
        return true
    }

    /** Den Vormerk zuruecknehmen - wenn das Eintragen danach scheiterte. */
    fun loese(context: Context, aufgabe: String) {
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .edit().remove(aufgabe).apply()
    }
}
