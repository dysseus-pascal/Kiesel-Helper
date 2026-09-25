package ch.dysseus.kieselhelper

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Die Nacht aus den Minutendaten der Uhr: wann eingeschlafen, wann
 * aufgewacht, und was dazwischen geschah.
 *
 * KEIN ANDROID HIER, mit Absicht. Die Rechnung ist der Teil, der am
 * ehesten falsch ist - und der einzige, der sich ohne Telefon pruefen
 * laesst. Also steht sie fuer sich, und der [SchlafanalyseTest] schickt
 * ihr erfundene Naechte, deren Antwort man kennt.
 *
 * WAS DIE UHR LIEFERT, ist je Minute ein Bewegungsmass und ein Puls, dazu
 * alle halbe Stunde eine HRV-Messung. Mehr nicht. Schlafphasen im Sinne
 * eines Schlaflabors (EEG) sind daraus nicht zu gewinnen; was hier
 * herauskommt, ist eine SCHAETZUNG nach dem, was man aus Bewegung und Puls
 * vernuenftig schliessen kann:
 *
 *  1. Schlaf oder wach nach Cole-Kripke: ein gewichtetes Mittel der Bewegung
 *     um die Minute herum, mit einer Schwelle. Das ist seit dreissig Jahren
 *     die Rechnung der Aktigraphie und fuer Schlaf/Wach gut belegt.
 *  2. Webster-Regeln: nach einer Wachphase braucht der Koerper eine Weile,
 *     bevor er wieder schlaeft - die Minuten direkt danach zaehlen noch als
 *     wach, und eine kurze Ruhe zwischen zwei langen Wachphasen ist kein
 *     Schlaf, sondern Liegen.
 *  3. Phasen aus dem Puls: im Tiefschlaf ist er am tiefsten und ruhigsten,
 *     im REM hoeher und unruhiger. Das ist die schwaechste Stufe der
 *     Rechnung - deshalb ohne Puls lieber gar keine Phasen als erfundene.
 */
object Schlafanalyse {

    /**
     * Ab welcher gewichteten Aktivitaet eine Minute als wach zaehlt.
     *
     * DIESE ZAHL IST GESCHAETZT, NICHT GEMESSEN. Cole und Kripke haben ihre
     * Gewichte fuer einen Aktigraphen am Handgelenk mit eigener Zaehlweise
     * bestimmt; die Pebble liefert ihre "vmc" (vector magnitude count) in
     * einer anderen Einheit. 60 ist ein Anfang, der auf einer ruhig
     * liegenden Uhr (vmc um 0..20) sicher schlafend und bei Umhergehen
     * (vmc in den Hunderten) sicher wach ergibt. Wer echte Naechte neben
     * einem anderen Schlaftracker hat, sollte sie daran anpassen: zu hoch,
     * und unruhiges Liegen gilt als Schlaf; zu tief, und jedes Umdrehen
     * weckt einen auf.
     */
    const val SCHWELLE = 60.0

    /** Ein Bewegungsbyte 255 heisst: keine gueltigen Daten (Uhr nicht getragen). */
    const val UNGUELTIG = 255

    /** Um so viel ueber dem Ruhepuls der Nacht gilt man als wach, auch ohne Bewegung. */
    private const val PULS_WACH_UEBER = 20

    /** Kuerzer ist kein Einschlafen: ein Nickerchen vor dem Fernseher zaehlt nicht. */
    private const val MIN_SCHLAFLAUF = 10

    /** Weniger Schlaf als das ist keine Nacht - eher eine Uhr, die auf dem Tisch lag. */
    const val MIN_NACHT = 60

    /** Kuerzere Phasen werden dem Nachbarn zugeschlagen: fuenf Minuten REM sind Rauschen. */
    private const val MIN_ABSCHNITT = 5

    /**
     * Die Gewichte von Cole-Kripke fuer die Minuten i-4 .. i+2. Ihre Summe ist
     * 665 - geteilt durch sie, ist das Ergebnis ein gewichteter Mittelwert.
     */
    private val GEWICHTE = intArrayOf(106, 54, 58, 76, 230, 74, 67)
    private const val VOR = 4

    enum class Phase { WACH, LEICHT, TIEF, REM, SCHLAF }

    /** Eine HRV-Messung der Nacht; `minute` ist der Beginn des Fensters ab Nachtbeginn. */
    data class HrvFenster(val minute: Int, val rmssd: Int, val puls: Int)

    /** Ein Stueck der Nacht in Minuten ab Nachtbeginn; `bis` gehoert nicht mehr dazu. */
    data class Abschnitt(val von: Int, val bis: Int, val phase: Phase) {
        val laenge: Int get() = bis - von
    }

    data class Nacht(
        /** Erste Minute des Schlafs, ab Nachtbeginn. */
        val einschlafen: Int,
        /** Erste Minute nach dem Schlaf. */
        val aufwachen: Int,
        val phasen: List<Abschnitt>,
        /** Niedrigstes 30-Minuten-Mittel des Pulses im Schlaf; null ohne Puls. */
        val ruhepuls: Int?,
        /** Median der HRV-Fenster im Schlaf, in ms; null ohne Messung. */
        val hrv: Int?,
    ) {
        fun minuten(p: Phase): Int = phasen.filter { it.phase == p }.sumOf { it.laenge }
        val schlafMinuten: Int get() = phasen.filter { it.phase != Phase.WACH }.sumOf { it.laenge }
    }

    // --- Was die Uhr schickt ---

    /** Die Minutenbytes [bewegung, puls, bewegung, puls, ...] in zwei Reihen. */
    fun minutenAus(bytes: ByteArray): Pair<IntArray, IntArray> {
        val n = bytes.size / 2
        val bewegung = IntArray(n) { bytes[2 * it].toInt() and 0xFF }
        val puls = IntArray(n) { bytes[2 * it + 1].toInt() and 0xFF }
        return bewegung to puls
    }

    /** Je Fenster 4 Byte: Minute (u16, little endian), RMSSD (u8), Puls (u8). */
    fun hrvAus(bytes: ByteArray): List<HrvFenster> = (0 until bytes.size / 4).map { i ->
        val o = 4 * i
        val u = { k: Int -> bytes[o + k].toInt() and 0xFF }
        HrvFenster(u(0) or (u(1) shl 8), u(2), u(3))
    }

    // --- Zum Nachsehen ---

    /**
     * Die Nacht Minute fuer Minute als CSV - zum Vergleich mit einem anderen
     * Schlaftracker. Je Minute: Uhrzeit, das Byte von der Uhr, daraus vmc,
     * Puls, der gewichtete Mittelwert, an dem wach/schlaf entschieden wird,
     * und die Phase, die diese Rechnung daraus macht. Die HRV-Fenster darunter.
     *
     * @param uhrzeit Minute ab Nachtbeginn -> "HH:MM"
     */
    fun tabelle(
        bewegung: IntArray,
        puls: IntArray,
        hrv: List<HrvFenster>,
        uhrzeit: (Int) -> String,
    ): String {
        val n = minOf(bewegung.size, puls.size)
        val gueltig = BooleanArray(n) { bewegung[it] != UNGUELTIG }
        val a = DoubleArray(n) { if (gueltig[it]) bewegung[it].toDouble() * bewegung[it] / 16.0 else 0.0 }
        val w = gewichtet(a, gueltig)
        val nacht = werte(bewegung, puls, hrv)
        val phase = arrayOfNulls<Phase>(n)
        nacht?.phasen?.forEach { ab -> for (i in ab.von until minOf(ab.bis, n)) phase[i] = ab.phase }
        val b = StringBuilder()
        b.append("minute,uhrzeit,bewegung,vmc,puls,gewichtet,schwelle,phase\n")
        for (i in 0 until n) {
            b.append(i).append(',').append(uhrzeit(i)).append(',')
            b.append(if (gueltig[i]) bewegung[i].toString() else "").append(',')
            b.append(if (gueltig[i]) a[i].roundToInt().toString() else "").append(',')
            b.append(if (puls[i] > 0) puls[i].toString() else "").append(',')
            b.append("%.1f".format(java.util.Locale.ROOT, w[i])).append(',')
            b.append(SCHWELLE.roundToInt()).append(',')
            b.append(phase[i]?.name?.lowercase() ?: "").append('\n')
        }
        b.append("\nhrv_minute,uhrzeit,rmssd,puls\n")
        hrv.forEach { f -> b.append(f.minute).append(',').append(uhrzeit(f.minute)).append(',')
            .append(f.rmssd).append(',').append(f.puls).append('\n') }
        return b.toString()
    }

    // --- Die Rechnung ---

    /**
     * Die Nacht auswerten; null, wenn darin keine Nacht steckt.
     *
     * @param bewegung je Minute 0..254, 255 = ungueltig
     * @param puls je Minute in bpm, 0 = keiner
     */
    fun werte(bewegung: IntArray, puls: IntArray, hrv: List<HrvFenster> = emptyList()): Nacht? {
        val n = minOf(bewegung.size, puls.size)
        if (n == 0) return null
        val gueltig = BooleanArray(n) { bewegung[it] != UNGUELTIG }
        // DIE UHR SCHICKT DIE WURZEL, damit grosse und kleine Bewegung in ein
        // Byte passen. Zurueck in vmc: bewegung² / 16.
        val a = DoubleArray(n) { if (gueltig[it]) bewegung[it].toDouble() * bewegung[it] / 16.0 else 0.0 }
        val w = gewichtet(a, gueltig)

        val wach = BooleanArray(n) { !gueltig[it] || w[it] > SCHWELLE }

        // STILL LIEGEN UND WACH SEIN GEHT. Wer nachts gruebelt, bewegt sich
        // kaum - aber sein Puls liegt deutlich ueber dem der ruhigen Minuten.
        val ruhigePulse = (0 until n).filter { !wach[it] && puls[it] > 0 }.map { puls[it].toDouble() }
        median(ruhigePulse)?.let { m ->
            for (i in 0 until n) if (puls[i] > 0 && puls[i] > m + PULS_WACH_UEBER) wach[i] = true
        }

        webster(wach)

        // Einschlafen und Aufwachen: der erste und der letzte Schlaf, der
        // lang genug ist, um einer zu sein.
        val laeufe = laeufe(wach).filter { !it.wach && it.laenge >= MIN_SCHLAFLAUF }
        if (laeufe.isEmpty()) return null
        val ein = laeufe.first().von
        val auf = laeufe.last().bis
        if ((ein until auf).count { !wach[it] } < MIN_NACHT) return null

        val hatPuls = (ein until auf).any { !wach[it] && puls[it] > 0 }
        val jeMinute = if (hatPuls) {
            phasenMitPuls(ein, auf, wach, puls, hrv)
        } else {
            // OHNE PULS NUR SCHLAF UND WACH. Die Bewegung allein unterscheidet
            // Tief- von Leichtschlaf nicht - und eine Phase, die niemand
            // gemessen hat, gehoert nicht in die Akte.
            Array(auf - ein) { if (wach[ein + it]) Phase.WACH else Phase.SCHLAF }
        }

        return Nacht(
            einschlafen = ein,
            aufwachen = auf,
            phasen = glaette(abschnitte(jeMinute, ein)),
            ruhepuls = ruhepuls(ein, auf, wach, puls),
            hrv = median(hrvImSchlaf(hrv, ein, auf).map { it.rmssd.toDouble() })?.roundToInt(),
        )
    }

    /**
     * Cole-Kripke: W[i] = (106a[i-4] + 54a[i-3] + 58a[i-2] + 76a[i-1] + 230a[i]
     * + 74a[i+1] + 67a[i+2]) / 665.
     *
     * AM RAND UND UM LUECKEN WIRD NEU GEWICHTET: fehlt eine Nachbarminute,
     * zaehlen die vorhandenen mit ihrem Anteil. Sie als null zu nehmen,
     * machte jede Minute neben einer abgelegten Uhr schlaefriger, als sie war.
     */
    internal fun gewichtet(a: DoubleArray, gueltig: BooleanArray): DoubleArray = DoubleArray(a.size) { i ->
        var summe = 0.0
        var gewicht = 0
        for (k in GEWICHTE.indices) {
            val j = i + k - VOR
            if (j < 0 || j >= a.size || !gueltig[j]) continue
            summe += GEWICHTE[k] * a[j]
            gewicht += GEWICHTE[k]
        }
        if (gewicht > 0) summe / gewicht else Double.MAX_VALUE
    }

    /**
     * Die Regeln von Webster, auf der Stelle angewendet.
     *
     * Cole-Kripke allein sieht Schlaf, sobald die Bewegung aufhoert - aber
     * wer eben noch umherging, liegt danach eine Weile wach. Also: nach vier
     * Minuten wach die naechste noch wach, nach zehn die naechsten drei, nach
     * fuenfzehn die naechsten vier. Und ein Schlaf von hoechstens sechs
     * Minuten zwischen zwei Wachphasen von mindestens zehn ist keiner.
     *
     * BEIDES AUF DEM STAND VOR DER REGEL, nicht fortlaufend: sonst wuerde
     * jede verlaengerte Wachphase die naechste Verlaengerung ausloesen.
     */
    internal fun webster(wach: BooleanArray) {
        for (l in laeufe(wach)) {
            if (!l.wach) continue
            val dazu = when {
                l.laenge >= 15 -> 4
                l.laenge >= 10 -> 3
                l.laenge >= 4 -> 1
                else -> 0
            }
            for (j in l.bis until minOf(l.bis + dazu, wach.size)) wach[j] = true
        }
        val nachher = laeufe(wach)
        for (k in 1 until nachher.size - 1) {
            val l = nachher[k]
            if (!l.wach && l.laenge <= 6 && nachher[k - 1].laenge >= 10 && nachher[k + 1].laenge >= 10) {
                for (j in l.von until l.bis) wach[j] = true
            }
        }
    }

    /**
     * Die Phasen Minute fuer Minute.
     *
     * TIEF: der geglaettete Puls im unteren Drittel der Nacht, ringsum drei
     * Minuten Ruhe - und, wo in der Naehe eine HRV gemessen wurde, eine
     * mindestens mittlere HRV. Im Tiefschlaf ueberwiegt der Parasympathikus;
     * eine niedrige HRV dort spricht gegen ihn.
     *
     * REM: fruehestens eine Stunde nach dem Einschlafen (der erste REM kommt
     * so gut wie nie frueher), und der Puls hoch oder unruhig. Im REM ist der
     * Koerper still, das Herz aber nicht.
     *
     * Der Rest ist leicht - wie in jeder echten Nacht der groesste Teil.
     */
    private fun phasenMitPuls(
        ein: Int,
        auf: Int,
        wach: BooleanArray,
        puls: IntArray,
        hrv: List<HrvFenster>,
    ): Array<Phase> {
        val n = puls.size
        fun pulseUm(i: Int, r: Int) = (maxOf(0, i - r)..minOf(n - 1, i + r))
            .filter { puls[it] > 0 }.map { puls[it].toDouble() }

        // Geglaettet ueber fuenf Minuten: ein einzelner Ausreisser des
        // Sensors macht noch keinen Tiefschlaf.
        val glatt = arrayOfNulls<Double>(n)
        val schwankung = arrayOfNulls<Double>(n)
        for (i in ein until auf) {
            glatt[i] = median(pulseUm(i, 2))
            schwankung[i] = pulseUm(i, 5).takeIf { it.size >= 3 }?.let(::standardabweichung)
        }
        val schlaf = (ein until auf).filter { !wach[it] }
        val glattImSchlaf = schlaf.mapNotNull { glatt[it] }.sorted()
        val p30 = perzentil(glattImSchlaf, 0.30)
        val p60 = perzentil(glattImSchlaf, 0.60)
        val schwankungMedian = median(schlaf.mapNotNull { schwankung[it] })

        val fenster = hrvImSchlaf(hrv, ein, auf)
        val hrvMedian = median(fenster.map { it.rmssd.toDouble() })

        // "Ruhig" heisst: als Schlaf gewertet. Das schliesst Bewegung ueber der
        // Schwelle, einen Puls weit ueber dem der Nacht und abgelegte Minuten
        // aus - genau das, was einen Tiefschlaf unmoeglich macht.
        fun ruhig(j: Int) = j in ein until auf && !wach[j]

        fun hrvPasst(i: Int): Boolean {
            if (hrvMedian == null) return true
            val naechstes = fenster.minByOrNull { abs(mitte(it) - i) } ?: return true
            if (abs(mitte(naechstes) - i) > 15) return true
            return naechstes.rmssd >= hrvMedian
        }

        return Array(auf - ein) { k ->
            val i = ein + k
            val g = glatt[i]
            when {
                wach[i] -> Phase.WACH
                g == null || p30 == null || p60 == null -> Phase.LEICHT
                g <= p30 && (i - 3..i + 3).all(::ruhig) && hrvPasst(i) -> Phase.TIEF
                ruhig(i) && i >= ein + 60 && (g >= p60 ||
                    (schwankung[i] != null && schwankungMedian != null &&
                        schwankung[i]!! > 1.3 * schwankungMedian)) -> Phase.REM
                else -> Phase.LEICHT
            }
        }
    }

    /** Das Fenster dauert fuenf Minuten; es zaehlt seine Mitte. */
    private fun mitte(f: HrvFenster) = f.minute + 2

    private fun hrvImSchlaf(hrv: List<HrvFenster>, ein: Int, auf: Int) =
        hrv.filter { it.rmssd > 0 && mitte(it) in ein until auf }

    /**
     * Der Ruhepuls: das niedrigste Mittel ueber 30 Minuten Schlaf.
     *
     * NICHT DER TIEFSTE EINZELWERT - der ist oft ein Messfehler. Und nicht
     * das Mittel der ganzen Nacht - das zieht jeder Traum nach oben. Eine
     * halbe Stunde, in der mindestens zwei Drittel der Minuten einen Puls
     * haben, ist beides nicht.
     */
    private fun ruhepuls(ein: Int, auf: Int, wach: BooleanArray, puls: IntArray): Int? {
        var bester: Double? = null
        for (s in ein..auf - 30) {
            val werte = (s until s + 30).filter { !wach[it] && puls[it] > 0 }.map { puls[it] }
            if (werte.size < 20) continue
            val m = werte.average()
            if (bester == null || m < bester) bester = m
        }
        return bester?.roundToInt()
    }

    // --- Abschnitte ---

    private data class Lauf(val von: Int, val bis: Int, val wach: Boolean) {
        val laenge get() = bis - von
    }

    private fun laeufe(wach: BooleanArray): List<Lauf> {
        val aus = mutableListOf<Lauf>()
        var s = 0
        for (i in 1..wach.size) {
            if (i == wach.size || wach[i] != wach[s]) {
                aus += Lauf(s, i, wach[s])
                s = i
            }
        }
        return aus
    }

    private fun abschnitte(jeMinute: Array<Phase>, ab: Int): List<Abschnitt> {
        val aus = mutableListOf<Abschnitt>()
        var s = 0
        for (i in 1..jeMinute.size) {
            if (i == jeMinute.size || jeMinute[i] != jeMinute[s]) {
                aus += Abschnitt(ab + s, ab + i, jeMinute[s])
                s = i
            }
        }
        return aus
    }

    /**
     * Abschnitte unter fuenf Minuten dem laengeren Nachbarn zuschlagen.
     *
     * Die Rechnung kippt an den Grenzen gern hin und her; in der Akte saehe
     * das aus wie eine Nacht aus lauter Zwei-Minuten-Phasen. Der kuerzeste
     * zuerst, damit ein Flackern nicht einen echten kurzen Abschnitt schluckt.
     */
    internal fun glaette(eingang: List<Abschnitt>): List<Abschnitt> {
        val liste = eingang.toMutableList()
        while (liste.size > 1) {
            val k = liste.indices.filter { liste[it].laenge < MIN_ABSCHNITT }
                .minByOrNull { liste[it].laenge } ?: break
            val vorher = liste.getOrNull(k - 1)
            val nachher = liste.getOrNull(k + 1)
            val ziel = when {
                vorher == null -> k + 1
                nachher == null -> k - 1
                nachher.laenge > vorher.laenge -> k + 1
                else -> k - 1
            }
            val z = liste[ziel]
            val kurz = liste[k]
            liste[ziel] = Abschnitt(minOf(z.von, kurz.von), maxOf(z.bis, kurz.bis), z.phase)
            liste.removeAt(k)
            // Gleiche Nachbarn wieder zusammenlegen.
            var i = 1
            while (i < liste.size) {
                if (liste[i].phase == liste[i - 1].phase) {
                    liste[i - 1] = Abschnitt(liste[i - 1].von, liste[i].bis, liste[i].phase)
                    liste.removeAt(i)
                } else {
                    i++
                }
            }
        }
        return liste
    }

    // --- Statistik ---

    internal fun median(werte: List<Double>): Double? {
        if (werte.isEmpty()) return null
        val s = werte.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    /** Linear zwischen den Raengen; `sortiert` muss aufsteigend sein. */
    internal fun perzentil(sortiert: List<Double>, q: Double): Double? {
        if (sortiert.isEmpty()) return null
        val pos = q * (sortiert.size - 1)
        val u = pos.toInt()
        val o = minOf(u + 1, sortiert.lastIndex)
        return sortiert[u] + (sortiert[o] - sortiert[u]) * (pos - u)
    }

    private fun standardabweichung(werte: List<Double>): Double {
        val m = werte.average()
        return sqrt(werte.sumOf { (it - m) * (it - m) } / werte.size)
    }
}
