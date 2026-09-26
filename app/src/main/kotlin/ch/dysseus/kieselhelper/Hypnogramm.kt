package ch.dysseus.kieselhelper

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Instant

/**
 * Der Verlauf einer Nacht: welche Phase wann.
 *
 * AUS DER AKTE, NICHT AUS DEN ROHDATEN. So hat jede Nacht einen Verlauf -
 * auch eine, die eine andere App eingetragen hat, und auch eine, deren
 * Rohdaten laengst geloescht sind. Die Rohdaten kommen auf der Seite
 * "Nacht" nur als Spuren darunter dazu, wo es sie gibt.
 *
 * Vier Bahnen, von oben nach unten: wach, REM, leicht, tief - wie bei jedem
 * Schlaftracker, damit das Bild ohne Legende lesbar ist.
 */
data class Hypnogramm(
    val von: Instant,
    val bis: Instant,
    val stufen: List<Stufe>,
) {
    data class Stufe(val von: Instant, val bis: Instant, val bahn: Int) {
        val minuten: Long get() = (bis.epochSecond - von.epochSecond) / 60
    }

    /** Welche Bahn um diese Zeit - oder keine. */
    fun bahnBei(t: Instant): Int? = stufen.firstOrNull { !t.isBefore(it.von) && t.isBefore(it.bis) }?.bahn

    /** Die Wachphasen ab [mindestens] Minuten, in der Reihenfolge der Nacht. */
    fun wachphasen(mindestens: Long = 5): List<Stufe> =
        stufen.filter { it.bahn == WACH && it.minuten >= mindestens }

    companion object {
        const val WACH = 0
        const val REM = 1
        const val LEICHT = 2
        const val TIEF = 3

        /**
         * Aus den Schlafsitzungen einer Nacht.
         *
         * Eine Sitzung ohne Phasen (manche Apps tragen nur "geschlafen" ein)
         * wird als Ganzes leicht - ein Bild, das eine Nacht in Phasen teilt,
         * die niemand gemessen hat, waere huebsch und erfunden.
         */
        fun aus(sitzungen: List<SleepSessionRecord>): Hypnogramm? {
            if (sitzungen.isEmpty()) return null
            val stufen = sitzungen.sortedBy { it.startTime }.flatMap { s ->
                if (s.stages.isEmpty()) {
                    listOf(Stufe(s.startTime, s.endTime, LEICHT))
                } else {
                    s.stages.sortedBy { it.startTime }.mapNotNull { a ->
                        val bahn = when (a.stage) {
                            SleepSessionRecord.STAGE_TYPE_DEEP -> TIEF
                            SleepSessionRecord.STAGE_TYPE_REM -> REM
                            SleepSessionRecord.STAGE_TYPE_LIGHT,
                            SleepSessionRecord.STAGE_TYPE_SLEEPING -> LEICHT
                            SleepSessionRecord.STAGE_TYPE_AWAKE,
                            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> WACH
                            else -> null
                        }
                        bahn?.let { Stufe(a.startTime, a.endTime, it) }
                    }
                }
            }
            if (stufen.isEmpty()) return null
            return Hypnogramm(
                sitzungen.minOf { it.startTime },
                sitzungen.maxOf { it.endTime },
                stufen,
            )
        }
    }
}
