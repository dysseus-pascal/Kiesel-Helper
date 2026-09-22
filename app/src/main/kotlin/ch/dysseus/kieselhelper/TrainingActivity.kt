package ch.dysseus.kieselhelper

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
 * Die Trainings, mit ihrer Strecke auf der Karte.
 *
 * DIE KARTE GEHOERT AUFS TELEFON, nicht auf die Uhr. Kartenkacheln, Zoom und
 * Speicherverwaltung auf 128 KB RAM waeren ein eigenes Projekt - und auf
 * 200x228 Punkten saehe man ohnehin nichts, was man nicht besser hier sieht.
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
class TrainingActivity : ComponentActivity() {

    private lateinit var wurzel: LinearLayout
    private var karte: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Die Kennung ist Bedingung der Kachelserver, keine Formalie: anonyme
        // Abfragen weist OpenStreetMap ab.
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidBasePath = cacheDir
        Configuration.getInstance().osmdroidTileCache = java.io.File(cacheDir, "kacheln")

        setContentView(baueAnsicht())
        lade()
    }

    override fun onResume() {
        super.onResume()
        karte?.onResume()
    }

    override fun onPause() {
        karte?.onPause()
        super.onPause()
    }

    private fun baueAnsicht(): ScrollView {
        wurzel = spalte().apply { setPadding(dp(16f), dp(24f), dp(16f), dp(28f)) }
        wurzel.addView(knopfLeise(getString(R.string.zurueck)) { finish() })
        wurzel.luft(14f)
        wurzel.addView(kopf("Trainings"))
        wurzel.luft(6f)

        val roller = ScrollView(this)
        roller.addView(wurzel)
        roller.randUmSystemleisten()
        return roller
    }

    private fun lade() {
        lifecycleScope.launch {
            val sitzungen = hole()
            if (sitzungen.isEmpty()) {
                val k = karte()
                k.addView(kartentitel("Noch kein Training"))
                k.addView(zart(
                    "Sobald du auf der Uhr eines beendest, steht es hier — mit " +
                        "Strecke, wenn das Telefon dabei war."
                ))
                wurzel.addView(k)
                return@launch
            }

            // DAS JUENGSTE GROSS, die anderen als Liste. Was man sucht, wenn
            // man diesen Schirm oeffnet, ist fast immer das letzte Training.
            zeige(sitzungen.first(), gross = true)
            if (sitzungen.size > 1) {
                wurzel.addView(abschnitt("DAVOR"))
                sitzungen.drop(1).forEach { zeige(it, gross = false) }
            }
        }
    }

    private suspend fun hole(): List<ExerciseSessionRecord> = withContext(Dispatchers.IO) {
        val klient = Akte(this@TrainingActivity).bereit() ?: return@withContext emptyList()
        try {
            klient.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    TimeRangeFilter.between(
                        Instant.now().minus(Duration.ofDays(90)), Instant.now()
                    ),
                )
            ).records.sortedByDescending { it.startTime }.take(20)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun zeige(sitzung: ExerciseSessionRecord, gross: Boolean) {
        val k = karte()
        val dauer = Duration.between(sitzung.startTime, sitzung.endTime).toMinutes()
        k.addView(kartentitel((sitzung.title ?: "Training") + ", " + dauer + " min"))
        k.addView(zart(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(sitzung.startTime.toEpochMilli()))
        ))
        sitzung.notes?.let { k.addView(fliesstext(it)) }

        val beginn = sitzung.startTime.epochSecond
        val punkte = Spur.lies(this, beginn)
        if (punkte.size >= 2) {
            if (gross) {
                k.addView(streckendaten(punkte))
                k.addView(kartenbild(punkte))
            } else {
                k.addView(zart(
                    (Zahlen.eine(Spur.laenge(punkte) / 1000) ?: "") + " km aufgezeichnet"
                ))
            }
        } else if (gross) {
            k.addView(zart(
                "Keine Strecke — entweder war das Telefon nicht dabei, oder die " +
                    "Standortberechtigung fehlte."
            ))
        }
        wurzel.addView(k)
    }

    /** Die Zahlen, die erst aus der Strecke entstehen. */
    private fun streckendaten(punkte: List<Spur.Punkt>): LinearLayout {
        val meter = Spur.laenge(punkte)
        val hoehe = Spur.hoehenmeter(punkte)
        val sekunden = (punkte.last().zeit - punkte.first().zeit).coerceAtLeast(1)
        // Tempo als Minuten je Kilometer - so liest es jeder Laeufer.
        val tempo = if (meter > 100) (sekunden / (meter / 1000)) else 0.0

        val reihe = reihe()
        reihe.addView(messwert("Strecke", Zahlen.eine(meter / 1000), "km", 0f, false))
        reihe.addView(messwert(
            "Tempo",
            if (tempo > 0) String.format("%d:%02d", (tempo / 60).toInt(), (tempo % 60).toInt())
            else null,
            "/km", 0f, false,
        ))
        reihe.addView(messwert("Aufstieg", Zahlen.ganz(hoehe), "m", 0f, false))
        return spalte().apply { addView(reihe) }
    }

    /**
     * Die Karte mit der Strecke.
     *
     * MIT FESTER HOEHE, nicht mit "so viel wie da ist": eine Karte in einem
     * Roller, die sich ihre Hoehe selbst nimmt, wird entweder null Punkte
     * hoch oder unendlich.
     */
    private fun kartenbild(punkte: List<Spur.Punkt>): MapView {
        val ansicht = MapView(this)
        ansicht.setTileSource(TileSourceFactory.MAPNIK)
        ansicht.setMultiTouchControls(true)
        ansicht.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(240f)
        ).apply { topMargin = dp(10f) }

        val linie = Polyline(ansicht).apply {
            outlinePaint.color = farbe(R.color.akzent)
            outlinePaint.strokeWidth = dp(4f).toFloat()
            setPoints(punkte.map { GeoPoint(it.lat, it.lon) })
        }
        ansicht.overlays.add(linie)

        marke(ansicht, punkte.first(), "Start", Color.rgb(0x2E, 0x7D, 0x32))
        marke(ansicht, punkte.last(), "Ende", Color.rgb(0xC6, 0x28, 0x28))

        // Der Ausschnitt muss NACH dem Zeichnen gesetzt werden: vorher kennt
        // die Karte ihre eigene Groesse nicht und rechnet den Zoom auf null.
        ansicht.post {
            ansicht.zoomToBoundingBox(linie.bounds.increaseByScale(1.25f), false)
        }
        karte = ansicht
        return ansicht
    }

    private fun marke(ansicht: MapView, punkt: Spur.Punkt, text: String, ton: Int) {
        val m = Marker(ansicht)
        m.position = GeoPoint(punkt.lat, punkt.lon)
        m.title = text
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        m.icon = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(ton)
            setStroke(dp(2f), Color.WHITE)
            setSize(dp(14f), dp(14f))
        }
        ansicht.overlays.add(m)
    }
}
