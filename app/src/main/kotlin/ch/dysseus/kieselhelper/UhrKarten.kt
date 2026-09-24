package ch.dysseus.kieselhelper

import android.app.Activity
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/**
 * Die Einstellungen der Uhr-Apps, zum Aendern - Drinktervall, Kieselsport,
 * SupCycle.
 *
 * WAS HIER STEHT, IST DER STAND DER UHR. Geaendert wird ein Entwurf; "An die
 * Uhr" schickt ihn, und erst wenn die Uhr den neuen Stand meldet, gilt er.
 * Bis dahin sagt die Zeile unter den Reglern, wo er ist: geaendert,
 * unterwegs, angekommen - oder nicht angekommen.
 *
 * DIESELBEN WERTE WIE AUF DER KONFIGSEITE DER PEBBLE-APP. Die uebernimmt die
 * Meldung der Uhr ebenfalls; wer hier aendert und dort nachsieht, sieht
 * dasselbe.
 */
class UhrKarten(private val a: Activity) {

    private class Zustand<T>(val dienst: String, val app: String, val vorgabe: T) {
        var entwurf: T? = null
        var gesendet: T? = null
        var karte: LinearLayout? = null
        var zeile: TextView? = null
    }

    private val dt = Zustand("dt", "Drinktervall", Uhreinstellungen.DT_VORGABE)
    private val sp = Zustand("sp", "Kieselsport", Uhreinstellungen.SP_VORGABE)
    private val sc = Zustand("sc", "SupCycle", Uhreinstellungen.SC_VORGABE)
    // Die Nacht von Kieselsport: dieselbe Meldung, eigene Karte auf der Seite
    // Gesundheit (siehe nacht()).
    private val sn = Zustand("sp", "Kieselsport", Uhreinstellungen.SP_VORGABE)

    /** Welche SupCycle-Plaetze aufgeklappt sind - bleibt beim Neufuellen. */
    private val scOffen = mutableSetOf<Int>()

    private fun standDt() = Uhreinstellungen.drinktervall(a)
    private fun standSp() = Uhreinstellungen.kieselsport(a)
    private fun standSc() = Uhreinstellungen.supCycle(a)

    /** Leere Plaetze sind leer, gleich ob null oder ohne Namen. */
    private fun normal(s: Uhreinstellungen.SupCycle) =
        Uhreinstellungen.SupCycle(s.plaetze.map { p -> p?.takeIf { it.name.isNotBlank() } }, s.animation)

    // --- Nach einer Meldung der Uhr ---

    /**
     * Die Uhr hat gemeldet (oder das Nachfassen ist vorbei). Ohne eigene
     * Aenderung wird die Karte neu gefuellt; mit einer nur die Zeile - wer
     * gerade tippt, soll seine Eingabe nicht verlieren.
     */
    fun auffrischen() {
        pruefe(dt, standDt()) { fuelleDt() }
        pruefe(sp, standSp()) { fuelleSp() }
        pruefe(sn, standSp()) { fuelleSn() }
        pruefe(sc, standSc()?.let(this::normal)) { fuelleSc() }
    }

    private fun <T> pruefe(z: Zustand<T>, stand: T?, fuelle: () -> Unit) {
        if (z.karte == null) return
        if (stand != null && z.entwurf != null && z.entwurf == stand) {
            z.entwurf = null
            z.gesendet = null
        }
        if (geaendert(z, stand)) zeigeZeile(z, stand) else fuelle()
    }

    private fun <T> geaendert(z: Zustand<T>, stand: T?): Boolean {
        val e = z.entwurf ?: return false
        return e != (stand ?: z.vorgabe) && e != z.gesendet
    }

    private fun <T> zeigeZeile(z: Zustand<T>, stand: T?) {
        val zeile = z.zeile ?: return
        val jetzt = System.currentTimeMillis()
        val gemeldet = Uhreinstellungen.gemeldet(a, z.dienst)
        zeile.text = when {
            geaendert(z, stand) -> a.getString(R.string.uk_geaendert)
            z.gesendet != null && stand != z.gesendet ->
                if (jetzt - Uhreinstellungen.gesendet(a, z.dienst) < 12_000L) {
                    a.getString(R.string.uk_unterwegs, z.app)
                } else {
                    a.getString(R.string.uk_nicht_bestaetigt)
                }
            stand == null ->
                a.getString(R.string.uk_nie_gemeldet, z.app)
            else -> a.getString(
                R.string.uk_stand,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(gemeldet)),
            )
        }
    }

    private fun kopf(k: LinearLayout) {
        k.removeAllViews()
        k.addView(a.kartentitel(a.getString(R.string.uk_auf_der_uhr)))
        k.addView(a.zart(a.getString(R.string.uk_kopf_text)))
    }

    private fun <T> fuss(z: Zustand<T>, k: LinearLayout, stand: T?, schicke: () -> Unit) {
        k.luft(12f)
        k.addView(a.knopfHaupt(a.getString(R.string.uk_schicken), breit = true) { schicke() })
        val zeile = a.zart("").apply { setPadding(0, a.dp(8f), 0, 0) }
        z.zeile = zeile
        k.addView(zeile)
        zeigeZeile(z, stand)
    }

    private fun <T> merke(z: Zustand<T>, stand: T?, neu: T) {
        z.entwurf = neu
        zeigeZeile(z, stand)
    }

    // --- Drinktervall ---

    fun drinktervall(): LinearLayout = a.karte().also { dt.karte = it; fuelleDt() }

    private fun fuelleDt() {
        val k = dt.karte ?: return
        val stand = standDt()
        val d = dt.entwurf ?: stand ?: dt.vorgabe
        kopf(k)
        k.luft(6f)
        k.addView(stufer(a.getString(R.string.uk_glaeser_tag), (4..16).toList(), d.soll, { "$it" }) {
            merke(dt, standDt(), (dt.entwurf ?: d).copy(soll = it))
        })
        k.addView(stufer(a.getString(R.string.uk_glasgroesse), Uhreinstellungen.GLASGROESSEN, d.glasMl, this::glas) {
            merke(dt, standDt(), (dt.entwurf ?: d).copy(glasMl = it))
        })
        k.addView(schalter(a.getString(R.string.uk_trink_animation), d.animation) {
            merke(dt, standDt(), (dt.entwurf ?: d).copy(animation = it))
        })
        k.addView(a.zart(a.getString(R.string.uk_dt_hinweis)).apply { setPadding(0, a.dp(8f), 0, 0) })
        fuss(dt, k, stand) {
            val neu = dt.entwurf ?: d
            Uhreinstellungen.sendeDrinktervall(a, neu)
            dt.entwurf = neu
            dt.gesendet = neu
            zeigeZeile(dt, standDt())
        }
    }

    private fun glas(ml: Int): String = when {
        ml == 1000 -> "1 l"
        ml % 100 == 0 -> "${ml / 100} dl"
        else -> "$ml ml"
    }

    // --- Kieselsport ---

    fun kieselsport(): LinearLayout = a.karte().also { sp.karte = it; fuelleSp() }

    private fun fuelleSp() {
        val k = sp.karte ?: return
        val stand = standSp()
        val s = sp.entwurf ?: stand ?: sp.vorgabe
        val jetzt = { sp.entwurf ?: s }
        kopf(k)
        k.luft(6f)
        k.addView(unter(a.getString(R.string.uk_puls)))
        k.addView(stufer(a.getString(R.string.uk_maxpuls), (120..220).toList(), s.maxpuls, { "$it" }, grob = 5) {
            merke(sp, standSp(), jetzt().copy(maxpuls = it))
        })
        k.addView(a.zart(a.getString(R.string.uk_maxpuls_hinweis)))
        k.addView(unter(a.getString(R.string.uk_kraft)))
        k.addView(stufer(a.getString(R.string.uk_pause), (0..300 step 15).toList(), s.pause,
            { if (it == 0) a.getString(R.string.uk_nie) else "$it s" }) {
            merke(sp, standSp(), jetzt().copy(pause = it))
        })
        k.addView(stufer(a.getString(R.string.uk_empfind), listOf(1, 2, 3), s.empfind,
            { a.getString(listOf(R.string.uk_traege, R.string.uk_normal, R.string.uk_fein)[it - 1]) }) {
            merke(sp, standSp(), jetzt().copy(empfind = it))
        })
        k.addView(unter(a.getString(R.string.uk_schwimmen)))
        k.addView(stufer(a.getString(R.string.uk_becken), (10..50 step 5).toList(), s.becken, { "$it m" }) {
            merke(sp, standSp(), jetzt().copy(becken = it))
        })
        k.addView(unter(a.getString(R.string.uk_pin)))
        // "HH:MM" wie die Konfigseite - sonst meldete die Uhr "08:00" zurueck,
        // wo "8:00" geschickt wurde, und der Stand saehe unbestaetigt aus.
        val zeitKnopf = zeitknopf(a.getString(R.string.uk_uhrzeit), s.pinZeit, zweistellig = true) { neu ->
            merke(sp, standSp(), jetzt().copy(pinZeit = neu))
        }.apply { visibility = if (s.pinArt > 0) View.VISIBLE else View.GONE }
        k.addView(stufer(a.getString(R.string.uk_sportart), (0..7).toList(), s.pinArt, { a.getString(Uhreinstellungen.SPORTARTEN[it]) }) {
            merke(sp, standSp(), jetzt().copy(pinArt = it))
            zeitKnopf.visibility = if (it > 0) View.VISIBLE else View.GONE
        })
        k.addView(zeitKnopf)
        fuss(sp, k, stand) {
            // DIE NACHT KOMMT AUS DEM STAND, nicht aus diesem Entwurf: sie
            // wird auf der Seite Gesundheit eingestellt (nacht()), und ein
            // alter Entwurf hier soll sie nicht ueberschreiben.
            val basis = standSp() ?: sp.vorgabe
            val neu = jetzt().copy(nachtAn = basis.nachtAn, nachtVon = basis.nachtVon, nachtBis = basis.nachtBis)
            Uhreinstellungen.sendeKieselsport(a, neu)
            sp.entwurf = neu
            sp.gesendet = neu
            zeigeZeile(sp, standSp())
        }
    }

    // --- Kieselsport: die Nacht ---
    //
    // DIESELBE UHR-APP, ABER EIN ANDERES THEMA. Das Zeitfenster der Nacht
    // gehoert zu Gesundheit, nicht zum Sport - also eine eigene Karte dort.
    // Sie schickt dieselbe Nachricht wie die Sportkarte, mit deren Werten
    // aus dem Stand der Uhr, und nur die Nacht aus dem eigenen Entwurf.

    fun nacht(): LinearLayout = a.karte().also { sn.karte = it; fuelleSn() }

    private fun fuelleSn() {
        val k = sn.karte ?: return
        val stand = standSp()
        val s = sn.entwurf ?: stand ?: sn.vorgabe
        val jetzt = { sn.entwurf ?: s }
        kopf(k)
        k.luft(6f)
        // Ein Fenster, kein Wecker. Die Uhr misst darin von selbst und
        // schickt morgens die Minuten; ausgewertet wird hier.
        val fenster = a.spalte().apply { visibility = if (s.nachtAn) View.VISIBLE else View.GONE }
        k.addView(schalter(a.getString(R.string.uk_nacht_messen), s.nachtAn) {
            merke(sn, standSp(), jetzt().copy(nachtAn = it))
            fenster.visibility = if (it) View.VISIBLE else View.GONE
        })
        fenster.addView(zeitknopf(a.getString(R.string.uk_nacht_von), hhmm(s.nachtVon), zweistellig = true) { neu ->
            merke(sn, standSp(), jetzt().copy(nachtVon = minuten(neu)))
        })
        fenster.addView(zeitknopf(a.getString(R.string.uk_nacht_bis), hhmm(s.nachtBis), zweistellig = true) { neu ->
            merke(sn, standSp(), jetzt().copy(nachtBis = minuten(neu)))
        })
        k.addView(fenster)
        k.addView(a.zart(a.getString(R.string.uk_nacht_hinweis)).apply { setPadding(0, a.dp(8f), 0, 0) })
        fuss(sn, k, stand) {
            val e = jetzt()
            val neu = (standSp() ?: sn.vorgabe).copy(nachtAn = e.nachtAn, nachtVon = e.nachtVon, nachtBis = e.nachtBis)
            Uhreinstellungen.sendeKieselsport(a, neu)
            sn.entwurf = neu
            sn.gesendet = neu
            zeigeZeile(sn, standSp())
        }
    }

    // --- SupCycle ---

    fun supCycle(): LinearLayout = a.karte().also { sc.karte = it; fuelleSc() }

    private fun fuelleSc() {
        val k = sc.karte ?: return
        val stand = standSc()?.let(this::normal)
        val s = sc.entwurf ?: stand ?: sc.vorgabe
        // Der Entwurf traegt auch leere Plaetze mit Namen "" - verglichen wird
        // er immer in der Normalform.
        val plaetze = s.plaetze.toMutableList()
        var animation = s.animation
        val aendere = {
            merke(sc, standSc()?.let(this::normal), normal(Uhreinstellungen.SupCycle(plaetze.toList(), animation)))
        }
        kopf(k)
        val heute = Uhreinstellungen.heute()
        val leer = Uhreinstellungen.Praeparat("", 8, 0, 1, 0, 0, heute)
        // EINGEKLAPPT, BIS MAN HINEINWILL. Sechs Plaetze mit je fuenf Reglern
        // waeren eine Rolle von dreissig Zeilen; so steht je Praeparat eine
        // Zeile mit dem Wichtigsten, und leere Plaetze stehen gar nicht da -
        // dafuer gibt es "Präparat hinzufügen".
        val oeffner = mutableListOf<() -> Unit>()
        lateinit var hinzu: Button
        fun zeigeHinzu() {
            val frei = (0 until Uhreinstellungen.SC_PLAETZE).firstOrNull { plaetze[it]?.name.isNullOrBlank() && it !in scOffen }
            hinzu.visibility = if (frei == null) View.GONE else View.VISIBLE
        }
        for (i in 0 until Uhreinstellungen.SC_PLAETZE) {
            val p0 = plaetze[i]
            val p = p0 ?: leer
            val block = a.spalte()
            val inhalt = a.spalte().apply { visibility = if (i in scOffen) View.VISIBLE else View.GONE }
            val details = a.spalte().apply { visibility = if (p0 == null) View.GONE else View.VISIBLE }
            val titel = beschriftung("").apply { setTypeface(typeface, Typeface.BOLD) }
            val zusammen = a.zart("")
            val pfeil = TextView(a).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(a.farbe(R.color.schrift_zart))
            }
            fun beschrifte() {
                val q = plaetze[i]
                val name = q?.name.orEmpty()
                titel.text = name.ifBlank { a.getString(R.string.uk_platz_leer, i + 1) }
                zusammen.text = if (q == null || name.isBlank()) "" else zusammenfassung(q)
                zusammen.visibility = if (zusammen.text.isEmpty()) View.GONE else View.VISIBLE
                pfeil.text = if (inhalt.visibility == View.VISIBLE) "▾" else "▸"
            }
            val kopfzeile = a.reihe().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, a.dp(12f), 0, a.dp(8f))
                isClickable = true
                addView(a.spalte().apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(titel.apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) })
                    addView(zusammen)
                })
                addView(pfeil)
                setOnClickListener {
                    val auf = inhalt.visibility != View.VISIBLE
                    inhalt.visibility = if (auf) View.VISIBLE else View.GONE
                    if (auf) scOffen += i else scOffen -= i
                    // Ein leerer Platz, der zugeklappt wird, verschwindet wieder.
                    if (!auf && plaetze[i]?.name.isNullOrBlank()) block.visibility = View.GONE
                    beschrifte()
                    zeigeHinzu()
                }
            }
            val nachAenderung = { aendere(); beschrifte() }
            val feld = a.eingabefeld(a.getString(R.string.uk_name_hinweis)).apply {
                setText(p0?.name.orEmpty())
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(t: CharSequence?, s: Int, c: Int, n: Int) {}
                    override fun onTextChanged(t: CharSequence?, s: Int, v: Int, n: Int) {}
                    override fun afterTextChanged(t: Editable?) {
                        val name = t?.toString().orEmpty()
                        plaetze[i] = (plaetze[i] ?: leer).copy(name = name)
                        details.visibility = if (name.isBlank()) View.GONE else View.VISIBLE
                        nachAenderung()
                    }
                })
            }
            inhalt.addView(feld)
            val pauseZeile = stufer(a.getString(R.string.uk_wochen_pause), (0..52).toList(), p.wochenAus,
                { if (it == 0) a.getString(R.string.uk_keine) else "$it" }) {
                plaetze[i] = (plaetze[i] ?: leer).copy(wochenAus = it); nachAenderung()
            }.apply { visibility = if (p.wochenAn > 0) View.VISIBLE else View.GONE }
            val seitZeile = stufer(a.getString(R.string.uk_zyklus_seit), (0..25).toList(),
                Math.floorDiv(heute - p.anker, 7).coerceIn(0, 25), { a.getString(R.string.uk_n_wo, it) }) { neu ->
                // DER ANKER RUECKT UM GANZE WOCHEN: so bleibt der Wochentag,
                // an dem der Zyklus wechselt, derselbe wie auf der Uhr.
                val alt = plaetze[i] ?: leer
                val bisher = Math.floorDiv(heute - alt.anker, 7).coerceIn(0, 25)
                plaetze[i] = alt.copy(anker = alt.anker - (neu - bisher) * 7); nachAenderung()
            }.apply { visibility = if (p.wochenAn > 0) View.VISIBLE else View.GONE }
            details.addView(zeitknopf(a.getString(R.string.uk_uhrzeit), "%d:%02d".format(p.stunde, p.minute)) { hhmm ->
                val (h, m) = hhmm.split(":").map { it.toInt() }
                plaetze[i] = (plaetze[i] ?: leer).copy(stunde = h, minute = m); nachAenderung()
            })
            details.addView(stufer(a.getString(R.string.uk_alle_tage), (1..30).toList(), p.alleTage,
                { if (it == 1) a.getString(R.string.uk_taeglich) else "$it" }) {
                plaetze[i] = (plaetze[i] ?: leer).copy(alleTage = it); nachAenderung()
            })
            details.addView(stufer(a.getString(R.string.uk_wochen_einnahme), (0..52).toList(), p.wochenAn,
                { if (it == 0) a.getString(R.string.uk_immer) else "$it" }) {
                plaetze[i] = (plaetze[i] ?: leer).copy(wochenAn = it); nachAenderung()
                val zyklus = if (it > 0) View.VISIBLE else View.GONE
                pauseZeile.visibility = zyklus
                seitZeile.visibility = zyklus
            })
            details.addView(pauseZeile)
            details.addView(seitZeile)
            inhalt.addView(details)
            inhalt.luft(8f)
            if (i > 0) block.addView(a.strich().apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(1f))
            })
            block.addView(kopfzeile)
            block.addView(inhalt)
            block.visibility = if (p0 == null && i !in scOffen) View.GONE else View.VISIBLE
            k.addView(block)
            beschrifte()
            oeffner += {
                scOffen += i
                block.visibility = View.VISIBLE
                inhalt.visibility = View.VISIBLE
                beschrifte()
                feld.requestFocus()
            }
        }
        hinzu = a.knopfLeise(a.getString(R.string.uk_hinzu)) {
            val frei = (0 until Uhreinstellungen.SC_PLAETZE)
                .firstOrNull { plaetze[it]?.name.isNullOrBlank() && it !in scOffen } ?: return@knopfLeise
            oeffner[frei]()
            zeigeHinzu()
        }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = a.dp(8f) }
        k.addView(hinzu)
        zeigeHinzu()
        k.addView(unter(a.getString(R.string.uk_uhr)))
        k.addView(schalter(a.getString(R.string.uk_anim_abhaken), animation) { animation = it; aendere() })
        k.addView(a.zart(a.getString(R.string.uk_sc_hinweis)).apply { setPadding(0, a.dp(8f), 0, 0) })
        fuss(sc, k, stand) {
            val soll = Uhreinstellungen.sendeSupCycle(a, Uhreinstellungen.SupCycle(plaetze.toList(), animation))
            sc.entwurf = normal(soll)
            sc.gesendet = normal(soll)
            // Neu fuellen: gekuerzte Namen stehen dann so da, wie die Uhr sie bekommt.
            fuelleSc()
        }
    }

    /** "8:00 · täglich · 8 Wo. an, 4 Pause" - was ein eingeklappter Platz zeigt. */
    private fun zusammenfassung(p: Uhreinstellungen.Praeparat): String = buildString {
        append("%d:%02d".format(p.stunde, p.minute))
        append(" · ")
        append(if (p.alleTage <= 1) a.getString(R.string.uk_taeglich) else a.getString(R.string.uk_alle_n_tage, p.alleTage))
        if (p.wochenAn > 0) {
            append(" " + a.getString(R.string.uk_wo_an, p.wochenAn))
            if (p.wochenAus > 0) append(a.getString(R.string.uk_pause_n, p.wochenAus))
        }
    }

    // --- Bausteine ---

    /** Minuten seit Mitternacht als "HH:MM" und zurueck - die Uhr rechnet in Minuten. */
    private fun hhmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

    private fun minuten(hhmm: String): Int {
        val (h, m) = hhmm.split(":").map { it.toIntOrNull() ?: 0 }
        return h * 60 + m
    }

    private fun unter(text: String): TextView = TextView(a).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(a.farbe(R.color.schrift_zart))
        letterSpacing = 0.08f
        setPadding(0, a.dp(16f), 0, a.dp(2f))
    }

    private fun beschriftung(text: String): TextView = TextView(a).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(a.farbe(R.color.schrift))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun klein(text: String, tue: () -> Unit): Button = a.knopfLeise(text, tue = tue).apply {
        minWidth = 0
        minimumWidth = 0
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(a.dp(46f), a.dp(46f))
    }

    /**
     * Minus, Wert, Plus. KEINE TASTATUR fuer Zahlen aus einer kurzen Liste:
     * die Uhr nimmt ohnehin nur diese Werte, und so laesst sich keiner tippen,
     * den sie verwuerfe.
     */
    private fun stufer(
        titel: String,
        werte: List<Int>,
        wert: Int,
        zeige: (Int) -> String,
        grob: Int = 1,
        neu: (Int) -> Unit,
    ): LinearLayout {
        var i = werte.indexOf(wert).takeIf { it >= 0 }
            ?: werte.indices.minByOrNull { abs(werte[it] - wert) } ?: 0
        val anzeige = TextView(a).apply {
            text = zeige(werte[i])
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(a.farbe(R.color.schrift))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(a.dp(104f), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        fun schritt(d: Int) {
            val n = (i + d).coerceIn(0, werte.lastIndex)
            if (n == i) return
            i = n
            anzeige.text = zeige(werte[i])
            neu(werte[i])
        }
        return a.reihe().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, a.dp(6f), 0, 0)
            addView(beschriftung(titel))
            addView(klein("−") { schritt(-1) }.apply { setOnLongClickListener { schritt(-grob); true } })
            addView(anzeige)
            addView(klein("+") { schritt(1) }.apply { setOnLongClickListener { schritt(grob); true } })
        }
    }

    private fun schalter(titel: String, an: Boolean, neu: (Boolean) -> Unit): LinearLayout {
        var zustand = an
        lateinit var knopf: Button
        knopf = a.knopfLeise(a.getString(if (an) R.string.an else R.string.aus)) {
            zustand = !zustand
            knopf.text = a.getString(if (zustand) R.string.an else R.string.aus)
            neu(zustand)
        }.apply { layoutParams = LinearLayout.LayoutParams(a.dp(104f + 92f), ViewGroup.LayoutParams.WRAP_CONTENT) }
        return a.reihe().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, a.dp(6f), 0, 0)
            addView(beschriftung(titel))
            addView(knopf)
        }
    }

    /** Eine Uhrzeit "H:MM" - gewaehlt, nicht getippt. */
    private fun zeitknopf(titel: String, zeit: String, zweistellig: Boolean = false, neu: (String) -> Unit): LinearLayout {
        var jetzt = zeit
        lateinit var knopf: Button
        knopf = a.knopfLeise(zeit) {
            val (h, m) = jetzt.split(":").map { it.toIntOrNull() ?: 0 }
            TimePickerDialog(a, { _, stunde, minute ->
                jetzt = (if (zweistellig) "%02d:%02d" else "%d:%02d").format(stunde, minute)
                knopf.text = jetzt
                neu(jetzt)
            }, h, m, true).show()
        }.apply { layoutParams = LinearLayout.LayoutParams(a.dp(104f + 92f), ViewGroup.LayoutParams.WRAP_CONTENT) }
        return a.reihe().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, a.dp(6f), 0, 0)
            addView(beschriftung(titel))
            addView(knopf)
        }
    }
}
