package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.DateFormat
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * Der Trainings-Schirm: was mit der Uhr aufgezeichnet wurde, mit Strecke.
 *
 * EIN EIGENER REITER, WEIL ES EINE EIGENE FRAGE IST. Schritte, Schlaf und
 * Puls sind der Tag, der einem passiert; ein Training ist das, was man tut.
 * Beides auf einem Schirm hiesse, in einer Liste von Tageszahlen nach der
 * letzten Ausfahrt zu suchen.
 *
 * DIE KARTE GEHOERT AUFS TELEFON, nicht auf die Uhr. Kartenkacheln, Zoom und
 * Speicherverwaltung auf 128 KB RAM waeren ein eigenes Projekt - und auf
 * 200x228 Punkten saehe man ohnehin nichts, was man nicht hier besser sieht.
 * Die Uhr misst, das Telefon zeigt.
 *
 * DIE LISTE KOMMT AUS DER GESUNDHEITSAKTE, nicht aus einer eigenen Tabelle.
 * Dort stehen die Sitzungen ohnehin, seit Kiesel-Helper sie eintraegt; eine
 * zweite Liste daneben waere eine zweite Wahrheit. Die Strecke liegt daneben
 * als Datei, weil eine Stunde Laufen tausend Punkte hat und in keine Zeile
 * passt.
 *
 * OpenStreetMap statt Google Maps: kein Schluessel, keine Play-Dienste - und
 * dieselbe Datengrundlage, aus der OsmAnd seine Karten baut.
 */
object TrainingTab {

    /** Wie weit zurueck gelesen wird. Ein Vierteljahr ist ein Trainingsstand. */
    private const val TAGE = 90L

    /** Die gezeigten Karten - damit der Schirm ihnen seinen Lebenslauf weitergibt. */
    private val karten = mutableListOf<MapView>()

    /**
     * Eine Sitzung mit dem, was daneben liegt.
     *
     * DIE PUNKTE NUR BEIM JUENGSTEN. Gezeichnet wird ohnehin nur die oberste
     * Strecke; zwanzig Spuren zu je tausend Punkten im Speicher zu halten,
     * um darunter zwanzigmal "8,2 km" zu schreiben, waere Verschwendung.
     * Die Laenge bleibt, die Punkte nicht.
     */
    data class Eintrag(
        val sitzung: ExerciseSessionRecord,
        val punkte: List<Spur.Punkt>,
        val meter: Double,
    )

    /**
     * Alles holen, was der Schirm braucht - und zwar hier, nicht beim Bauen.
     *
     * DIE SPUREN SIND DATEIEN. Eine Stunde Laufen sind tausend Zeilen JSON;
     * sie beim Zusammensetzen der Ansicht zu lesen hiesse, den Bildschirm
     * fuer die Dauer von zwanzig Dateien anzuhalten. Der Fehler faellt erst
     * auf, wenn jemand ein halbes Jahr lang trainiert hat.
     */
    suspend fun hole(ctx: Context): List<Eintrag> = withContext(Dispatchers.IO) {
        val klient = Akte(ctx).bereit() ?: return@withContext emptyList()
        val sitzungen = try {
            klient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    TimeRangeFilter.between(
                        Instant.now().minus(Duration.ofDays(TAGE)), Instant.now()
                    ),
                )
            ).records.sortedByDescending { it.startTime }.take(20)
        } catch (e: Exception) {
            emptyList()
        }
        sitzungen.mapIndexed { i, sitzung ->
            val punkte = Spur.lies(ctx, sitzung.startTime.epochSecond)
            Eintrag(sitzung, if (i == 0) punkte else emptyList(), Spur.laenge(punkte))
        }
    }

    fun baue(ctx: Context, eintraege: List<Eintrag>): LinearLayout {
        karten.clear()
        val s = ctx.spalte()

        if (eintraege.isEmpty()) {
            val k = ctx.karte()
            k.addView(ctx.kartentitel("Noch kein Training"))
            k.addView(ctx.zart(
                "Sobald du auf der Uhr eines beendest, steht es hier — mit " +
                    "Strecke, wenn das Telefon dabei war. Ohne Kieselsport auf " +
                    "der Uhr bleibt dieser Schirm leer; Trainings anderer Apps " +
                    "erscheinen, sobald sie in der Gesundheitsakte stehen."
            ))
            s.addView(k)
            return s
        }

        s.addView(ctx.abschnitt("DIE LETZTEN SIEBEN TAGE"))
        s.addView(wochenkarte(ctx, eintraege))

        // DAS JUENGSTE GROSS, die anderen als Liste. Was man sucht, wenn man
        // diesen Schirm oeffnet, ist fast immer das letzte Training.
        s.addView(ctx.abschnitt("ZULETZT"))
        s.addView(sitzungskarte(ctx, eintraege.first(), gross = true))
        if (eintraege.size > 1) {
            s.addView(ctx.abschnitt("DAVOR"))
            eintraege.drop(1).forEach { s.addView(sitzungskarte(ctx, it, gross = false)) }
        }
        return s
    }

    /** Die Karten anhalten und weiterlaufen lassen - osmdroid will das wissen. */
    fun anhalten() = karten.forEach { it.onPause() }
    fun weiter() = karten.forEach { it.onResume() }

    /**
     * Beim Schliessen loslassen.
     *
     * EINE KARTE HAELT DEN SCHIRM FEST, auf dem sie liegt. Bliebe sie in
     * dieser Liste stehen, haenge der ganze geschlossene Schirm mit daran -
     * das ist die uebliche Art, wie ein Telefon langsam wird.
     */
    fun vergiss() {
        karten.forEach { it.onDetach() }
        karten.clear()
    }

    /**
     * Die Woche in drei Zahlen.
     *
     * NICHT DER MONAT UND NICHT DAS JAHR. Eine Trainingswoche ist die
     * Einheit, in der man plant; was im August war, sagt heute nichts mehr.
     */
    private fun wochenkarte(ctx: Context, alle: List<Eintrag>): LinearLayout {
        val grenze = Instant.now().minus(Duration.ofDays(7))
        val woche = alle.filter { it.sitzung.startTime.isAfter(grenze) }
        val minuten = woche.sumOf {
            Duration.between(it.sitzung.startTime, it.sitzung.endTime).toMinutes()
        }
        val meter = woche.sumOf { it.meter }

        val k = ctx.karte()
        val reihe = ctx.reihe()
        reihe.addView(ctx.messwert(
            "Trainings", if (woche.isEmpty()) null else woche.size.toString(), "", 0f, false
        ))
        reihe.addView(ctx.messwert(
            "Zeit", if (minuten > 0) Zahlen.dauer(minuten.toDouble()) else null, "", 0f, false
        ))
        reihe.addView(ctx.messwert(
            "Strecke", if (meter > 100) Zahlen.eine(meter / 1000) else null, "km", 0f, false
        ))
        k.addView(reihe)
        if (woche.isEmpty()) {
            k.addView(ctx.zart("In den letzten sieben Tagen keines."))
        } else if (meter <= 100) {
            // KEINE NULL KILOMETER. Ohne aufgezeichnete Strecke ist die
            // Zahl nicht null, sondern nicht vorhanden - und ein Strich
            // sagt das, eine Null luegt.
            k.addView(ctx.zart(
                "Keine Strecke aufgezeichnet — Kraft und Yoga haben keine, " +
                    "sonst war das Telefon nicht dabei."
            ))
        }
        return k
    }

    private fun sitzungskarte(
        ctx: Context,
        eintrag: Eintrag,
        gross: Boolean,
    ): LinearLayout {
        val sitzung = eintrag.sitzung
        val k = ctx.karte()
        val dauer = Duration.between(sitzung.startTime, sitzung.endTime).toMinutes()
        k.addView(ctx.kartentitel((sitzung.title ?: "Training") + ", " + dauer + " min"))
        k.addView(ctx.zart(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(sitzung.startTime.toEpochMilli()))
        ))
        sitzung.notes?.let { k.addView(ctx.fliesstext(it)) }

        if (gross) {
            saetzeUndBahnen(ctx, sitzung)?.let { k.addView(it) }
        }

        val punkte = eintrag.punkte
        if (gross && punkte.size >= 2) {
            k.addView(streckendaten(ctx, punkte))
            k.addView(kartenbild(ctx, punkte))
        } else if (!gross && eintrag.meter > 100) {
            k.addView(ctx.zart(
                (Zahlen.eine(eintrag.meter / 1000) ?: "") + " km aufgezeichnet"
            ))
        } else if (gross) {
            k.addView(ctx.zart(
                "Keine Strecke — entweder war das Telefon nicht dabei, oder die " +
                    "Standortberechtigung fehlte."
            ))
        }
        return k
    }

    /**
     * Die Saetze eines Krafttrainings oder die Bahnen einer Schwimmeinheit.
     *
     * SIE STEHEN EINZELN IN DER AKTE, seit die Uhr sie mitschickt - aber
     * kein Schirm zeigt sie so. "12 Wdh, 90 s Pause" ist die Zeile, wegen
     * der man das Training ueberhaupt nachschlaegt.
     *
     * GELESEN, NICHT GERECHNET: was hier steht, kommt aus der
     * Gesundheitsakte zurueck. Steht es dort nicht, steht es auch hier
     * nicht - und dann hat die Uhr es nicht geschickt.
     */
    private fun saetzeUndBahnen(ctx: Context, sitzung: ExerciseSessionRecord): LinearLayout? {
        val saetze = sitzung.segments.filter { it.repetitions > 0 }
        val pausen = sitzung.segments.filter {
            it.segmentType == ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST
        }
        val bahnen = sitzung.laps
        if (saetze.isEmpty() && bahnen.isEmpty()) return null

        val s = ctx.spalte()
        if (saetze.isNotEmpty()) {
            saetze.forEachIndexed { i, satz ->
                val dauer = Duration.between(satz.startTime, satz.endTime).seconds
                val pause = pausen.getOrNull(i)?.let {
                    Duration.between(it.startTime, it.endTime).seconds
                }
                s.addView(ctx.zart(
                    "Satz " + (i + 1) + ": " + satz.repetitions + " Wdh., " + dauer + " s" +
                        (if (pause != null) "  ·  Pause " + pause + " s" else "")
                ))
            }
        } else {
            // BEI DEN BAHNEN ZAEHLT DIE ZEIT JE BAHN, nicht jede einzeln als
            // Zeile: zwanzig Zeilen liest niemand. Die schnellste und die
            // langsamste sagen, wie gleichmaessig es war.
            val zeiten = bahnen.map { Duration.between(it.startTime, it.endTime).seconds }
            val schnitt = if (zeiten.isNotEmpty()) zeiten.sum() / zeiten.size else 0
            s.addView(ctx.zart(
                bahnen.size.toString() + " Bahnen, je " + schnitt + " s im Schnitt " +
                    "(" + (zeiten.minOrNull() ?: 0) + " bis " + (zeiten.maxOrNull() ?: 0) + " s)"
            ))
        }
        return s
    }

    /** Die Zahlen, die erst aus der Strecke entstehen. */
    private fun streckendaten(ctx: Context, punkte: List<Spur.Punkt>): LinearLayout {
        val meter = Spur.laenge(punkte)
        val hoehe = Spur.hoehenmeter(punkte)
        val sekunden = (punkte.last().zeit - punkte.first().zeit).coerceAtLeast(1)
        // Tempo als Minuten je Kilometer - so liest es jeder Laeufer.
        val tempo = if (meter > 100) (sekunden / (meter / 1000)) else 0.0

        val reihe = ctx.reihe()
        reihe.addView(ctx.messwert("Strecke", Zahlen.eine(meter / 1000), "km", 0f, false))
        reihe.addView(ctx.messwert(
            "Tempo",
            if (tempo > 0) String.format("%d:%02d", (tempo / 60).toInt(), (tempo % 60).toInt())
            else null,
            "/km", 0f, false,
        ))
        reihe.addView(ctx.messwert("Aufstieg", Zahlen.ganz(hoehe), "m", 0f, false))
        return ctx.spalte().apply { addView(reihe) }
    }

    /**
     * Die Karte mit der Strecke.
     *
     * MIT FESTER HOEHE, nicht mit "so viel wie da ist": eine Karte in einem
     * Roller, die sich ihre Hoehe selbst nimmt, wird entweder null Punkte
     * hoch oder unendlich.
     */
    private fun kartenbild(ctx: Context, punkte: List<Spur.Punkt>): MapView {
        // Die Kennung ist Bedingung der Kachelserver, keine Formalie: anonyme
        // Abfragen weist OpenStreetMap ab.
        Configuration.getInstance().userAgentValue = ctx.packageName
        Configuration.getInstance().osmdroidBasePath = ctx.cacheDir
        Configuration.getInstance().osmdroidTileCache = java.io.File(ctx.cacheDir, "kacheln")

        val ansicht = MapView(ctx)
        ansicht.setTileSource(TileSourceFactory.MAPNIK)
        ansicht.setMultiTouchControls(true)
        ansicht.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(240f)
        ).apply { topMargin = ctx.dp(10f) }

        val linie = Polyline(ansicht).apply {
            outlinePaint.color = ctx.farbe(R.color.akzent)
            outlinePaint.strokeWidth = ctx.dp(4f).toFloat()
            setPoints(punkte.map { GeoPoint(it.lat, it.lon) })
        }
        ansicht.overlays.add(linie)

        marke(ctx, ansicht, punkte.first(), "Start", Color.rgb(0x2E, 0x7D, 0x32))
        marke(ctx, ansicht, punkte.last(), "Ende", Color.rgb(0xC6, 0x28, 0x28))

        // Der Ausschnitt muss NACH dem Zeichnen gesetzt werden: vorher kennt
        // die Karte ihre eigene Groesse nicht und rechnet den Zoom auf null.
        ansicht.post {
            ansicht.zoomToBoundingBox(linie.bounds.increaseByScale(1.25f), false)
        }
        karten.add(ansicht)
        return ansicht
    }

    private fun marke(
        ctx: Context,
        ansicht: MapView,
        punkt: Spur.Punkt,
        text: String,
        ton: Int,
    ) {
        val m = Marker(ansicht)
        m.position = GeoPoint(punkt.lat, punkt.lon)
        m.title = text
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        m.icon = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(ton)
            setStroke(ctx.dp(2f), Color.WHITE)
            setSize(ctx.dp(14f), ctx.dp(14f))
        }
        ansicht.overlays.add(m)
    }
}
