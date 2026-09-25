package ch.dysseus.kieselhelper

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Die Minutendaten einer Nacht, wie Kieselsport sie stueckweise schickt.
 *
 * DIE STUECKE KOMMEN NICHT SICHER EINMAL. Die Uhr schickt das naechste erst,
 * wenn das vorige bestaetigt ist - und bleibt die Bestaetigung aus, schickt
 * sie es noch einmal, womoeglich erst am naechsten Tag. Also wird jede
 * Minute an ihren Platz GESCHRIEBEN, nicht angehaengt: ein Stueck, das
 * zweimal kommt, steht danach genau einmal da. Wie bei [Pulskurve] liegt
 * alles in einer Datei je Nacht (nacht/<beginn>.bin), bis es vollstaendig
 * ist.
 *
 * Je Minute drei Byte: [da, bewegung, puls]. Das "da" braucht es, weil
 * jeder Wert von bewegung und puls auch ein echter sein kann - 255/0 heisst
 * "Uhr nicht getragen", nicht "noch nicht angekommen".
 */
object Nachtdaten {

    private const val ORDNER = "nacht"
    /** Mehr als zwoelf Stunden misst die Uhr nicht; mehr ist ein Lesefehler. */
    const val HOECHSTENS = 720
    /** Eine Nacht, die so lange unvollstaendig bleibt, kommt nicht mehr. */
    private const val AUFHEBEN_S = 14L * 86400

    /** Eingetragene Naechte bleiben so lange im Archiv - zum Exportieren und Neu-Auswerten. */
    private const val ARCHIV_S = 30L * 86400

    private fun ordner(context: Context): File = File(context.filesDir, ORDNER).apply { mkdirs() }
    private fun archivOrdner(context: Context): File = File(ordner(context), "archiv").apply { mkdirs() }
    private fun datei(context: Context, beginn: Long) = File(ordner(context), "$beginn.bin")
    private fun hrvDatei(context: Context, beginn: Long) = File(ordner(context), "$beginn.hrv")

    /**
     * Ein Stueck einsetzen; Rueckgabe true, wenn die Nacht danach vollstaendig ist.
     *
     * Die HRV-Fenster kommen nur mit dem ersten Stueck und werden als Ganzes
     * ersetzt.
     */
    @Synchronized
    fun einsetzen(context: Context, beginn: Long, anzahl: Int, ab: Int, minuten: ByteArray, hrv: ByteArray?): Boolean {
        raeumeAuf(context, System.currentTimeMillis() / 1000)
        return try {
            val f = datei(context, beginn)
            val alt = if (f.exists()) f.readBytes() else null
            val neu = einfuegen(alt, anzahl, ab, minuten)
            f.writeBytes(neu)
            hrv?.let { hrvDatei(context, beginn).writeBytes(it) }
            vollstaendig(neu)
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Nacht: " + e.message)
            false
        }
    }

    /** Bewegung, Puls und HRV-Bytes einer Nacht - offen oder im Archiv; null, wenn nichts da ist. */
    @Synchronized
    fun lies(context: Context, beginn: Long): Triple<IntArray, IntArray, ByteArray>? {
        val offen = datei(context, beginn)
        val ort = if (offen.exists()) ordner(context) else archivOrdner(context)
        val f = File(ort, "$beginn.bin")
        if (!f.exists()) return null
        val (bewegung, puls) = zerlege(f.readBytes())
        val hrv = File(ort, "$beginn.hrv").takeIf { it.exists() }?.readBytes() ?: ByteArray(0)
        return Triple(bewegung, puls, hrv)
    }

    /**
     * Eine eingetragene Nacht ins Archiv, statt sie zu loeschen.
     *
     * DIE ERSTE ECHTE NACHT LAG GANZ DANEBEN - 2 h 38 statt gut sieben
     * Stunden -, und ihre Minuten waren da schon weg: geloescht nach dem
     * Eintragen, auf der Uhr wie hier. Ohne sie laesst sich keine Schwelle
     * einstellen. Jetzt bleiben sie dreissig Tage: zum Exportieren und, nach
     * einer neuen Rechnung, zum Neu-Auswerten.
     */
    @Synchronized
    fun archiviere(context: Context, beginn: Long) {
        val ziel = archivOrdner(context)
        datei(context, beginn).takeIf { it.exists() }?.let { it.copyTo(File(ziel, it.name), true); it.delete() }
        hrvDatei(context, beginn).takeIf { it.exists() }?.let { it.copyTo(File(ziel, it.name), true); it.delete() }
    }

    /** Die archivierten Naechte, die juengste zuerst. */
    @Synchronized
    fun archiv(context: Context): List<Long> =
        archivOrdner(context).listFiles { f -> f.name.endsWith(".bin") }.orEmpty()
            .mapNotNull { it.nameWithoutExtension.toLongOrNull() }
            .sortedDescending()

    /** Die Naechte, deren Daten ganz da sind - auch solche, deren Eintragen scheiterte. */
    @Synchronized
    fun vollstaendige(context: Context): List<Long> =
        ordner(context).listFiles { f -> f.isFile && f.name.endsWith(".bin") }.orEmpty()
            .filter { f -> try { vollstaendig(f.readBytes()) } catch (e: Exception) { false } }
            .mapNotNull { it.nameWithoutExtension.toLongOrNull() }

    @Synchronized
    fun loesche(context: Context, beginn: Long) {
        datei(context, beginn).delete()
        hrvDatei(context, beginn).delete()
    }

    /**
     * Naechte, die nie vollstaendig wurden, nach zwei Wochen wegwerfen.
     *
     * Die Uhr wiederholt ein Stueck, bis es bestaetigt ist; war die Nacht hier
     * schon eingetragen, beginnt dieses Nachzuegler-Stueck eine neue Datei,
     * die nie voll wird. Ohne Aufraeumen lagen sie ewig da.
     */
    private fun raeumeAuf(context: Context, jetzt: Long) {
        ordner(context).listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val b = f.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (b < jetzt - AUFHEBEN_S) f.delete()
        }
        archivOrdner(context).listFiles()?.forEach { f ->
            val b = f.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (b < jetzt - ARCHIV_S) f.delete()
        }
    }

    // --- Ohne Android, fuer den Test ---

    /** Die Minuten ab [ab] an ihren Platz schreiben - idempotent. */
    internal fun einfuegen(alt: ByteArray?, anzahl: Int, ab: Int, minuten: ByteArray): ByteArray {
        val n = anzahl.coerceIn(0, HOECHSTENS)
        val neu = ByteArray(3 * n)
        alt?.copyInto(neu, 0, 0, minOf(alt.size, neu.size))
        for (k in 0 until minuten.size / 2) {
            val i = ab + k
            if (i < 0 || i >= n) continue
            neu[3 * i] = 1
            neu[3 * i + 1] = minuten[2 * k]
            neu[3 * i + 2] = minuten[2 * k + 1]
        }
        return neu
    }

    internal fun vollstaendig(bestand: ByteArray): Boolean =
        bestand.isNotEmpty() && (0 until bestand.size / 3).all { bestand[3 * it] != 0.toByte() }

    internal fun zerlege(bestand: ByteArray): Pair<IntArray, IntArray> {
        val n = bestand.size / 3
        // Eine Minute, die nie ankam, ist wie eine ungueltige: nichts gewusst.
        val bewegung = IntArray(n) {
            if (bestand[3 * it] == 0.toByte()) Schlafanalyse.UNGUELTIG else bestand[3 * it + 1].toInt() and 0xFF
        }
        val puls = IntArray(n) {
            if (bestand[3 * it] == 0.toByte()) 0 else bestand[3 * it + 2].toInt() and 0xFF
        }
        return bewegung to puls
    }
}
