package ch.dysseus.kieselhelper

import android.content.Context
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
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
    private const val DT_GLASS_ML = 10008
    private const val DT_DRANK_AT = 10009

    // --- Herzintervall: naechtliche RMSSD-Messung ---

    private val HERZINTERVALL: UUID =
        UUID.fromString("7e1b28b2-cd13-4b50-ac4e-187b92707707")

    private const val HZ_RMSSD = 10000
    private const val HZ_WHEN = 10005

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
     */
    private fun artAlsSatzart(art: Long): Pair<Int, String> = when (art.toInt()) {
        0 -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "Laufen"
        // ZWEIMAL DIESELBE SATZART, ZWEI NAMEN. Die Gesundheitsakte kennt
        // nur ein Radfahren und kein Mountainbike; die Unterscheidung traegt
        // deshalb der Titel. Strasse und Gravel stehen zusammen - sie
        // unterscheiden sich im Reifen, nicht in dem, was die Uhr sieht.
        1 -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "Bike Strasse/Gravel"
        2 -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "Wandern"
        3 -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING to "Kraft"
        4 -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "Bike MTB"
        5 -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA to "Yoga"
        6 -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL to "Schwimmen"
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT to "Training"
    }

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
                "$bahnen Bahnen" + (if (je > 0) " à $je m" else "")
            }
            saetze > 0 -> {
                val reps = felder[SP_REPS] ?: 0
                val liste = abschnitte.filter { it.anzahl > 0 }
                    .joinToString("/") { it.anzahl.toString() }
                val pausen = pausenSchnitt(abschnitte)
                buildString {
                    append("$saetze Sätze")
                    if (liste.isNotBlank()) append(" ($liste)")
                    if (reps > 0) append(", $reps Wdh.")
                    if (pausen > 0) append(", Pause ⌀ $pausen s")
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
    ): String? {
        val zustand = felder[SP_ZUSTAND]
        val dauer = felder[SP_DAUER]

        if (dauer == null) {
            // Eine blosse Zustandsmeldung: sie steuert nur die Aufzeichnung.
            val beginn = felder[SP_BEGINN] ?: return null
            val (_, name) = artAlsSatzart(felder[SP_ART] ?: -1)
            return when (zustand) {
                1L -> { SpurDienst.starte(context, beginn, name); "$name begonnen" }
                2L -> { SpurDienst.stoppe(context); "$name pausiert" }
                3L -> { SpurDienst.starte(context, beginn, name); "$name fortgesetzt" }
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
            return "Gesundheitsakte nicht verfügbar"
        }
        val anfang = Instant.ofEpochSecond(beginn)
        val ende = anfang.plusSeconds(dauer)

        // Traegt schon jemand anders eine Sitzung ueber dieselbe Zeit ein?
        schonDa(context, klient, ExerciseSessionRecord::class, anfang, FENSTER_TRAINING)?.let {
            Log.i(TAG, "Training steht schon da, von " + it)
            return "Übersprungen — $it hat das Training schon eingetragen"
        }

        val (satzart, name) = artAlsSatzart(felder[SP_ART] ?: -1)
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

        val satz = ExerciseSessionRecord(
            startTime = anfang,
            startZoneOffset = null,
            endTime = ende,
            endZoneOffset = null,
            exerciseType = satzart,
            title = name,
            // Was nicht als eigener Satz in die Akte geht, steht wenigstens
            // daneben: die Zahlen der Uhr, unveraendert.
            notes = buildString {
                if (puls > 0) append("Puls ⌀ $puls")
                felder[SP_PULS_MAX]?.takeIf { it > 0 }?.let { append(", max $it") }
                if (strecke > 0) {
                    append(", ")
                    append(Zahlen.eine(strecke / 1000) ?: "")
                    append(" km (GPS)")
                } else {
                    felder[SP_METER]?.takeIf { it > 0 }?.let {
                        append(", ${it / 1000},${(it % 1000) / 100} km")
                    }
                }
                if (kcal > 0) append(", $kcal kcal")
                abschnittsnotiz(satzart, felder, abschnitte)?.let {
                    if (isNotEmpty()) append(" — ")
                    append(it)
                }
            }.ifBlank { null },
            metadata = vonDerUhr("kieselsport-" + beginn),
            segments = segmente,
            laps = runden,
            exerciseRoute = route,
        )
        val meldung = schreibe(context, satz, buildString {
            append("$name, ${dauer / 60} min")
            if (strecke > 0) append(", " + (Zahlen.eine(strecke / 1000) ?: "") + " km")
            if (bahnen > 0) append(", $bahnen Bahnen")
            if (segmente.isNotEmpty()) append(", ${felder[SP_SAETZE] ?: 0} Sätze")
            append(" eingetragen")
        }, riegel = "kieselsport")
        return meldung
    }

    /** Alle Uhr-Apps, von denen diese App ueberhaupt etwas annimmt. */
    val BEKANNTE_UHREN = setOf(DRINKTERVALL, HERZINTERVALL, SUPCYCLE, KIESELSPORT)

    /** Die Berechtigungen, die dafuer noetig sind. */
    val BERECHTIGUNGEN: Set<String> = setOf(
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        // Fuer Koffein und Praeparate: die Akte fuehrt beides als Ernaehrung.
        HealthPermission.getWritePermission(NutritionRecord::class),
        // Fuer die Trainingssitzungen von Kieselsport.
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

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
    ): String? =
        when (von) {
            DRINKTERVALL -> wasser(context, felder)
            HERZINTERVALL -> herz(context, felder)
            SUPCYCLE -> supplemente(context, felder, texte)
            KIESELSPORT -> training(context, felder, texte)
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
            val name = namen.getOrNull(platz)?.ifBlank { null } ?: "Präparat ${platz + 1}"
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
        return "$davon von $wieViele Präparaten"
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
        val ml = felder[DT_GLASS_ML] ?: return null
        val wann = felder[DT_DRANK_AT] ?: return null
        if (ml <= 0 || wann <= 0) return null
        if (!Riegel.neu(context, "drinktervall", wann.toString())) return null

        val beginn = Instant.ofEpochSecond(wann)
        val klient = Akte(context).bereit()
        if (klient == null) {
            Riegel.loese(context, "drinktervall")
            return "Gesundheitsakte nicht verfügbar"
        }
        schonDa(context, klient, HydrationRecord::class, beginn, FENSTER_WASSER)?.let {
            Log.i(TAG, "Wasser steht schon da, von " + it)
            return "Übersprungen — $it hat dasselbe Glas schon eingetragen"
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
        return schreibe(context, satz, "$ml ml eingetragen", riegel = "drinktervall")
    }

    /**
     * Die Herzratenvariabilitaet eintragen.
     *
     * Auch hier ein Riegel auf dem Zeitpunkt: die Uhr schickt die Messung der
     * letzten Nacht, bis sie bestaetigt ist, und das kann mehrfach geschehen.
     */
    private suspend fun herz(context: Context, felder: Map<Int, Long>): String? {
        val ms = felder[HZ_RMSSD] ?: return null
        val wann = felder[HZ_WHEN] ?: return null
        if (ms <= 0 || wann <= 0) return null
        if (!Riegel.neu(context, "herzintervall", wann.toString())) return null

        // DIE PEBBLE-APP KOENNTE DIESELBE MESSUNG EINTRAGEN. Sie synchronisiert
        // die Gesundheitsdaten der Uhr selbst, und die Akte fuehrt nur
        // zusammen, was aus derselben App kommt.
        val klient = Akte(context).bereit()
        if (klient == null) {
            Riegel.loese(context, "herzintervall")
            return "Gesundheitsakte nicht verfügbar"
        }
        val zeitpunkt = Instant.ofEpochSecond(wann)
        schonDa(
            context, klient, HeartRateVariabilityRmssdRecord::class,
            zeitpunkt, FENSTER_HRV,
        )?.let {
            Log.i(TAG, "HRV steht schon da, von " + it)
            return "Übersprungen — $it hat die Messung schon eingetragen"
        }

        val satz = HeartRateVariabilityRmssdRecord(
            time = Instant.ofEpochSecond(wann),
            zoneOffset = null,
            heartRateVariabilityMillis = ms.toDouble(),
            metadata = vonDerUhr("herzintervall-" + wann),
        )
        return schreibe(context, satz, "$ms ms eingetragen", riegel = "herzintervall")
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
        return schreibe(context, satz, "$mg mg Koffein eingetragen")
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
     * Fuenf Minuten fuer die HRV: sie wird einmal in der Nacht gemessen, und
     * zwei Apps, die dieselbe Messung melden, tun das nicht auf die Sekunde
     * genau. Beim Wasser enger - zwei Glaeser in fuenf Minuten sind moeglich,
     * zwei Eintraege in derselben Minute nicht.
     */
    private val FENSTER_HRV: Duration = Duration.ofMinutes(5)
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
            return "Gesundheitsakte nicht verfügbar"
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
            "Nicht eingetragen — Erlaubnis in der App prüfen"
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
