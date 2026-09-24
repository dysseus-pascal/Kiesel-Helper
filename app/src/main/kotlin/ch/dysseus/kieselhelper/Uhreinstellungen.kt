package ch.dysseus.kieselhelper

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Die Einstellungen der Uhr-Apps - gelesen und geaendert von hier.
 *
 * DIE UHR IST DIE EINE STELLE, AN DER SIE GELTEN. Geaendert werden sie auf
 * der Konfigseite in der Pebble-App oder hier; beide schicken an die Uhr, und
 * die Uhr meldet danach, was gilt. Hier steht nur, was sie zuletzt gemeldet
 * hat - nie ein eigener Stand, der mit dem der Konfigseite auseinanderlaufen
 * koennte.
 *
 * Gemeldet wird: von Drinktervall mit jeder Standmeldung, von SupCycle mit
 * jeder Tagesmeldung und beim Start, von Kieselsport beim Start und nach
 * jeder Aenderung.
 */
object Uhreinstellungen {

    private const val DATEI = "uhr-einstellungen"

    // --- Die Feldnummern, wie in Aufgaben: Reihenfolge der messageKeys ---

    private const val DT_TARGET = 10007
    private const val DT_GLASS_ML = 10008
    private const val DT_ANIMATION = 10010

    private const val SC_PLAN = 10001
    private const val SC_FX = 10043

    private const val SP_MAXPULS = 10008
    private const val SP_BECKEN = 10010
    private const val SP_PAUSENZIEL = 10011
    private const val SP_EMPFIND = 10012
    private const val SP_PIN_ART = 10018
    private const val SP_PIN_ZEIT = 10019

    // --- Was es gibt ---

    data class Drinktervall(val soll: Int, val glasMl: Int, val animation: Boolean)

    data class Kieselsport(
        val maxpuls: Int,
        val pause: Int,
        val empfind: Int,
        val becken: Int,
        val pinArt: Int,
        val pinZeit: String,
    )

    /** Ein Platz im Plan von SupCycle. `anker` in Tagen, wie auf der Uhr. */
    data class Praeparat(
        val name: String,
        val stunde: Int,
        val minute: Int,
        val alleTage: Int,
        val wochenAn: Int,
        val wochenAus: Int,
        val anker: Int,
    )

    /** Sechs Plaetze; null ist ein leerer. */
    data class SupCycle(val plaetze: List<Praeparat?>, val animation: Boolean)

    val DT_VORGABE = Drinktervall(8, 300, true)
    val SP_VORGABE = Kieselsport(190, 90, 2, 25, 0, "18:00")
    val SC_VORGABE = SupCycle(List(SC_PLAETZE) { null }, true)

    const val SC_PLAETZE = 6
    private const val SC_NAME = 16
    private const val SC_EINTRAG = 26

    val GLASGROESSEN = listOf(100, 150, 200, 250, 300, 400, 500, 750, 1000)
    /** Die Arten fuer den Pin, in der Reihenfolge der Uhr - als Ressourcen, die Namen sind Sprache. */
    val SPORTARTEN = listOf(
        R.string.sa_kein_pin, R.string.sport_laufen, R.string.sa_strasse_gravel, R.string.sport_wandern,
        R.string.sport_kraft, R.string.sa_mtb, R.string.sport_yoga, R.string.sport_schwimmen,
    )

    /**
     * Wer zusieht: der Einstellungsschirm, solange er offen ist. Gerufen wird
     * auf dem Hauptfaden, nach jeder Meldung und wenn das Nachfassen endet.
     */
    @Volatile
    var beobachter: (() -> Unit)? = null

    private val hand by lazy { Handler(Looper.getMainLooper()) }

    private fun stosseAn() { hand.post { beobachter?.invoke() } }

    private fun prefs(context: Context) = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)

    /** Wann die Uhr sich zuletzt gemeldet hat, Epoch-Millisekunden; 0 = nie. */
    fun gemeldet(context: Context, dienst: String): Long = prefs(context).getLong("${dienst}_um", 0L)

    /** Wann von hier zuletzt geschickt wurde. */
    fun gesendet(context: Context, dienst: String): Long = prefs(context).getLong("${dienst}_gesendet", 0L)

    // --- Drinktervall ---

    fun drinktervall(context: Context): Drinktervall? {
        val p = prefs(context)
        if (!p.contains("dt_soll")) return null
        return Drinktervall(p.getInt("dt_soll", 8), p.getInt("dt_glas", 300), p.getBoolean("dt_anim", true))
    }

    /**
     * Aus einer Standmeldung. Die Glasgroesse nur ohne Glas darin: mit einem
     * Glas steht dort dessen Menge, und die kann eine andere sein.
     */
    fun vonDrinktervall(context: Context, felder: Map<Int, Long>, mitGlas: Boolean) {
        val soll = felder[DT_TARGET] ?: return
        val alt = drinktervall(context)
        val neu = Drinktervall(
            soll = soll.toInt(),
            glasMl = if (mitGlas) (alt?.glasMl ?: felder[DT_GLASS_ML]?.toInt() ?: 300)
                     else (felder[DT_GLASS_ML]?.toInt() ?: alt?.glasMl ?: 300),
            animation = (felder[DT_ANIMATION] ?: 1L) != 0L,
        )
        prefs(context).edit()
            .putInt("dt_soll", neu.soll).putInt("dt_glas", neu.glasMl).putBoolean("dt_anim", neu.animation)
            .putLong("dt_um", System.currentTimeMillis())
            .apply()
        stosseAn()
    }

    fun sendeDrinktervall(context: Context, d: Drinktervall) {
        schicke(context, "dt", Aufgaben.DRINKTERVALL_UUID, mapOf(
            DT_TARGET to Wert.Zahl(d.soll.toLong()),
            DT_GLASS_ML to Wert.Zahl(d.glasMl.toLong()),
            DT_ANIMATION to Wert.Zahl(if (d.animation) 1L else 0L),
        )) { drinktervall(context) == d }
    }

    // --- Kieselsport ---

    fun kieselsport(context: Context): Kieselsport? {
        val p = prefs(context)
        if (!p.contains("sp_maxpuls")) return null
        return Kieselsport(
            p.getInt("sp_maxpuls", 190), p.getInt("sp_pause", 90), p.getInt("sp_empfind", 2),
            p.getInt("sp_becken", 25), p.getInt("sp_pin_art", 0), p.getString("sp_pin_zeit", "18:00") ?: "18:00",
        )
    }

    /** Ist das eine Einstellungsmeldung von Kieselsport? Dann merken. */
    fun vonKieselsport(context: Context, felder: Map<Int, Long>, texte: Map<Int, String>): Boolean {
        val maxpuls = felder[SP_MAXPULS] ?: return false
        val neu = Kieselsport(
            maxpuls = maxpuls.toInt(),
            pause = (felder[SP_PAUSENZIEL] ?: 90).toInt(),
            empfind = (felder[SP_EMPFIND] ?: 2).toInt(),
            becken = (felder[SP_BECKEN] ?: 25).toInt(),
            pinArt = (felder[SP_PIN_ART] ?: 0).toInt(),
            pinZeit = texte[SP_PIN_ZEIT] ?: "18:00",
        )
        prefs(context).edit()
            .putInt("sp_maxpuls", neu.maxpuls).putInt("sp_pause", neu.pause).putInt("sp_empfind", neu.empfind)
            .putInt("sp_becken", neu.becken).putInt("sp_pin_art", neu.pinArt).putString("sp_pin_zeit", neu.pinZeit)
            .putLong("sp_um", System.currentTimeMillis())
            .apply()
        // Die Pulszonen der Auswertung sind die der Uhr.
        Einstellungen.setzeMaxpuls(context, neu.maxpuls)
        stosseAn()
        return true
    }

    fun sendeKieselsport(context: Context, k: Kieselsport) {
        schicke(context, "sp", Aufgaben.KIESELSPORT_UUID, mapOf(
            SP_MAXPULS to Wert.Zahl(k.maxpuls.toLong()),
            SP_PAUSENZIEL to Wert.Zahl(k.pause.toLong()),
            SP_EMPFIND to Wert.Zahl(k.empfind.toLong()),
            SP_BECKEN to Wert.Zahl(k.becken.toLong()),
            SP_PIN_ART to Wert.Zahl(k.pinArt.toLong()),
            SP_PIN_ZEIT to Wert.Text(k.pinZeit),
        )) { kieselsport(context) == k }
    }

    // --- SupCycle ---

    fun supCycle(context: Context): SupCycle? {
        val roh = prefs(context).getString("sc_plan", null) ?: return null
        return try {
            val a = JSONArray(roh)
            val plaetze = List(SC_PLAETZE) { i ->
                val o = a.optJSONObject(i) ?: return@List null
                Praeparat(
                    o.getString("name"), o.getInt("h"), o.getInt("m"), o.getInt("alle"),
                    o.getInt("an"), o.getInt("aus"), o.getInt("anker"),
                )
            }
            SupCycle(plaetze, prefs(context).getBoolean("sc_fx", true))
        } catch (e: Exception) {
            null
        }
    }

    fun vonSupCycle(context: Context, felder: Map<Int, Long>, rohdaten: Map<Int, ByteArray>) {
        val bytes = rohdaten[SC_PLAN] ?: return
        val plaetze = planAus(bytes) ?: return
        val a = JSONArray()
        plaetze.forEach { p ->
            a.put(if (p == null) JSONObject.NULL else JSONObject()
                .put("name", p.name).put("h", p.stunde).put("m", p.minute).put("alle", p.alleTage)
                .put("an", p.wochenAn).put("aus", p.wochenAus).put("anker", p.anker))
        }
        val e = prefs(context).edit().putString("sc_plan", a.toString()).putLong("sc_um", System.currentTimeMillis())
        felder[SC_FX]?.let { e.putBoolean("sc_fx", it != 0L) }
        e.apply()
        stosseAn()
    }

    /**
     * Rueckgabe: der Plan, wie ihn die Uhr bekommt - Namen gekuerzt, Pause
     * ohne Einnahmewochen auf null. So vergleicht der Schirm mit dem, was
     * die Uhr zurueckmelden wird, und nicht mit dem Getippten.
     */
    fun sendeSupCycle(context: Context, s: SupCycle): SupCycle {
        val bytes = planAlsBytes(s.plaetze)
        val soll = SupCycle(planAus(bytes) ?: s.plaetze, s.animation)
        schicke(context, "sc", Aufgaben.SUPCYCLE_UUID, mapOf(
            SC_PLAN to Wert.Bytes(bytes),
            SC_FX to Wert.Zahl(if (s.animation) 1L else 0L),
        )) { supCycle(context) == soll }
        return soll
    }

    /** Das 26-Byte-Format aus plan.h von SupCycle, sechs Mal. */
    internal fun planAus(bytes: ByteArray): List<Praeparat?>? {
        if (bytes.size < SC_PLAETZE * SC_EINTRAG) return null
        return List(SC_PLAETZE) { i ->
            val o = i * SC_EINTRAG
            val u = { k: Int -> bytes[o + k].toInt() and 0xFF }
            var ende = 0
            while (ende < SC_NAME && bytes[o + ende] != 0.toByte()) ende++
            val name = String(bytes, o, ende, Charsets.UTF_8).trim()
            if (u(18) == 0 || name.isEmpty()) return@List null
            Praeparat(
                name = name, stunde = u(16), minute = u(17), alleTage = maxOf(1, u(19)),
                wochenAn = u(20), wochenAus = u(21),
                anker = u(22) or (u(23) shl 8) or (u(24) shl 16) or (u(25) shl 24),
            )
        }
    }

    internal fun planAlsBytes(plaetze: List<Praeparat?>): ByteArray {
        val out = ByteArray(SC_PLAETZE * SC_EINTRAG)
        for (i in 0 until SC_PLAETZE) {
            val p = plaetze.getOrNull(i) ?: continue
            val name = nameBytes(p.name)
            if (name.isEmpty()) continue
            val o = i * SC_EINTRAG
            name.copyInto(out, o)
            out[o + 16] = p.stunde.coerceIn(0, 23).toByte()
            out[o + 17] = p.minute.coerceIn(0, 59).toByte()
            out[o + 18] = 1
            out[o + 19] = p.alleTage.coerceIn(1, 30).toByte()
            val an = p.wochenAn.coerceIn(0, 52)
            out[o + 20] = an.toByte()
            out[o + 21] = (if (an == 0) 0 else p.wochenAus.coerceIn(0, 52)).toByte()
            for (k in 0 until 4) out[o + 22 + k] = (p.anker shr (8 * k)).toByte()
        }
        return out
    }

    /**
     * Der Name als UTF-8, hoechstens 15 Byte - das sechzehnte ist die Null.
     * Geschnitten wird nur zwischen Zeichen: ein halber Umlaut auf der Uhr
     * waere schlimmer als ein fehlender Buchstabe.
     */
    internal fun nameBytes(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        val s = text.trim()
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val b = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
            if (out.size() + b.size > SC_NAME - 1) break
            out.write(b)
            i += Character.charCount(cp)
        }
        return out.toByteArray()
    }

    /**
     * Heute in Tagen, genau wie SupCycle rechnet: Mitternacht der Ortszeit als
     * Unix-Sekunden, durch 86400. Nicht LocalDate.toEpochDay - das wiche
     * oestlich von Greenwich um einen Tag ab, und jeder Zyklus verschoebe sich.
     */
    fun heute(): Int =
        Math.floorDiv(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toEpochSecond(), 86400L).toInt()

    // --- Das Schicken ---

    /**
     * An die Uhr, mit Nachfassen.
     *
     * EINE APPMESSAGE ERREICHT NUR DIE LAUFENDE APP. Also erst starten, dann
     * schicken - und weil die App dafuer eine Weile braucht, nach 2,5 und
     * nach 6 Sekunden noch einmal, es sei denn, die Uhr hat den neuen Stand
     * inzwischen gemeldet. Doppelt ankommen schadet nicht: derselbe Wert
     * zweimal gesetzt ist derselbe Wert.
     */
    private fun schicke(
        context: Context,
        dienst: String,
        an: UUID,
        felder: Map<Int, Wert>,
        angekommen: () -> Boolean,
    ) {
        val app = context.applicationContext
        prefs(app).edit().putLong("${dienst}_gesendet", System.currentTimeMillis()).apply()
        UhrSender.sende(app, an, starten = true, felder = felder)
        for (spaeter in listOf(2500L, 6000L)) {
            hand.postDelayed({ if (!angekommen()) UhrSender.sende(app, an, starten = true, felder = felder) }, spaeter)
        }
        // Danach sagt der Schirm, ob es ankam.
        hand.postDelayed({ beobachter?.invoke() }, 12000L)
        stosseAn()
    }
}
