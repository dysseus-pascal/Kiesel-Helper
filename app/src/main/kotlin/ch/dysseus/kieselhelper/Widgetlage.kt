package ch.dysseus.kieselhelper

import java.time.Duration
import java.time.LocalDateTime

/**
 * Welche Lage gerade gilt - und was das Widget deshalb gross zeigt.
 *
 * DAS WIDGET WEISS, WAS GERADE ZAEHLT. Morgens nach dem Aufwachen gibt es noch
 * keine Schritte, aber die Nacht ist die Auskunft, die man will. Nach einem
 * Training will man das Training sehen. Am Abend die Bilanz. Statt vier
 * festen Kacheln gibt es eine Buehne, die wechselt, und drei kleine Kacheln
 * fuer den Rest.
 *
 * EINE VORRANGLISTE, KEIN GEWICHT: Training vor Morgen vor Erinnerung vor
 * Abend vor Tag. Wer im Morgen trainiert, sieht danach das Training.
 *
 * REINE LOGIK OHNE SCHIRM, damit sie sich pruefen laesst: hinein geht ein
 * Blick auf den Stand, heraus kommt, was zu zeigen ist.
 */
object Widgetlage {

    enum class Art { TRAINING, MORGEN, ERINNERUNG, ABEND, TAG }

    /** Was das Widget vom Stand braucht - gesammelt, damit die Logik ohne Akte prueft. */
    data class Blick(
        val jetzt: LocalDateTime,
        /** Wann die letzte Nacht endete, null wenn keine eingetragen ist. */
        val schlafEnde: LocalDateTime?,
        val schlafMin: Double?,
        val schlafZielMin: Double?,
        val schlafWocheMin: Double?,
        val erholsamAnteil: Double?,     //< 0..1, null wenn unbekannt
        val ruhepuls: Double?,
        val hrv: Double?,
        /** Das letzte Training, null wenn keines in Reichweite. */
        val trainingEnde: LocalDateTime?,
        val trainingName: String?,
        val trainingMin: Long?,
        val trainingPuls: Double?,
        val trainingKm: Double?,
        val trainingBeginn: Long?,       //< Epoch-Sekunden, fuer den Sprung zur Seite
        val schritte: Double?,
        val schritteZiel: Double?,
        val aktivMin: Double?,
        val aktivZiel: Double?,
        val wasserMl: Double?,
        val wasserZiel: Double?,
        val glasMl: Double,
        val offenePraeparate: List<String>,
        val koffeinMg: Double?,
        /** Von Hand gewaehlt, gilt bis dann. */
        val wunsch: Art?,
    )

    /** Eine kleine Kachel: Beschriftung, Wert, Anteil am Ziel. */
    data class Kachel(val schluessel: String, val name: String, val text: String?, val anteil: Float)

    data class Lage(
        val art: Art,
        val wort: String,          //< das Wort oben: "Nacht", "Training", "Heute"
        val name: String,          //< Beschriftung der Buehne
        val gross: String,         //< die grosse Zahl
        val einheit: String,
        val satz: String,          //< der Satz darunter
        val anteil: Float?,        //< Balken unter der grossen Zahl, oder keiner
        val kacheln: List<Kachel>, //< die drei kleinen
        val trainingBeginn: Long?, //< gesetzt, wenn ein Tipp auf die Trainingsseite fuehren soll
    )

    private val TRAINING_FRIST: Duration = Duration.ofHours(2)
    private val MORGEN_FRIST: Duration = Duration.ofHours(3)
    private const val ABEND_AB = 20

    fun ermittle(b: Blick): Lage {
        val art = b.wunsch ?: when {
            b.trainingEnde != null && Duration.between(b.trainingEnde, b.jetzt) < TRAINING_FRIST &&
                !b.trainingEnde.isAfter(b.jetzt) -> Art.TRAINING
            b.schlafEnde != null && b.schlafMin != null && Duration.between(b.schlafEnde, b.jetzt) < MORGEN_FRIST &&
                !b.schlafEnde.isAfter(b.jetzt) -> Art.MORGEN
            erinnerung(b) != null -> Art.ERINNERUNG
            b.jetzt.hour >= ABEND_AB || zielErreicht(b) -> Art.ABEND
            else -> Art.TAG
        }
        return when (art) {
            Art.TRAINING -> training(b)
            Art.MORGEN -> morgen(b)
            Art.ERINNERUNG -> erinnerungLage(b)
            Art.ABEND -> abend(b)
            Art.TAG -> tag(b)
        }
    }

    private fun zielErreicht(b: Blick): Boolean {
        val s = b.schritte ?: return false
        val z = b.schritteZiel ?: return false
        return s >= z && b.jetzt.hour >= 16
    }

    // --- Die Kacheln, aus denen die Buehne waehlt ---

    private fun kSchritte(b: Blick) = Kachel("schritte", "Schritte", Zahlen.ganz(b.schritte), anteil(b.schritte, b.schritteZiel))
    private fun kAktiv(b: Blick) = Kachel("aktiv", "Aktiv", Zahlen.ganz(b.aktivMin)?.plus(" min"), anteil(b.aktivMin, b.aktivZiel))
    private fun kSchlaf(b: Blick) = Kachel("schlaf", "Schlaf", Zahlen.dauer(b.schlafMin), anteil(b.schlafMin, b.schlafZielMin))
    private fun kWasser(b: Blick) = Kachel("wasser", "Wasser", Zahlen.ganz(b.wasserMl)?.plus(" ml"), anteil(b.wasserMl, b.wasserZiel))

    private fun anteil(zahl: Double?, ziel: Double?): Float {
        if (zahl == null || ziel == null || ziel <= 0) return 0f
        return (zahl / ziel).coerceIn(0.0, 1.0).toFloat()
    }

    // --- Die Lagen ---

    private fun morgen(b: Blick): Lage {
        val min = b.schlafMin ?: 0.0
        val teile = mutableListOf<String>()
        b.erholsamAnteil?.let { teile += Zahlen.ganz(it * 100) + " % erholsam" }
        b.ruhepuls?.let { teile += "Ruhepuls " + Zahlen.ganz(it) }
        b.hrv?.let { teile += "HRV " + Zahlen.ganz(it) + " ms" }
        val vergleich = b.schlafWocheMin?.let { woche ->
            when {
                min >= woche + 30 -> "länger als sonst"
                min <= woche - 30 -> "kürzer als sonst"
                else -> "wie sonst"
            }
        }
        val satz = buildString {
            if (teile.isNotEmpty()) append(teile.joinToString(" · "))
            if (vergleich != null) { if (isNotEmpty()) append(" — "); append(vergleich) }
            if (isEmpty()) append("Die Nacht ist eingetragen.")
        }
        return Lage(
            Art.MORGEN, "Nacht", "Geschlafen",
            Zahlen.dauer(min) ?: "—", "", satz, anteil(b.schlafMin, b.schlafZielMin),
            listOf(kSchritte(b), kAktiv(b), kWasser(b)), null,
        )
    }

    private fun training(b: Blick): Lage {
        val teile = mutableListOf<String>()
        b.trainingPuls?.let { teile += "Puls Ø " + Zahlen.ganz(it) }
        b.trainingKm?.takeIf { it >= 0.1 }?.let { teile += Zahlen.eine(it) + " km" }
        val vor = b.trainingEnde?.let { Duration.between(it, b.jetzt).toMinutes() } ?: 0
        val wann = if (vor < 60) "vor $vor min beendet" else "vor " + (vor / 60) + " h beendet"
        val satz = (teile + wann).joinToString(" · ")
        return Lage(
            Art.TRAINING, "Training", b.trainingName ?: "Training",
            Zahlen.dauer(b.trainingMin?.toDouble()) ?: "—", "", satz, null,
            listOf(kSchritte(b), kWasser(b), kSchlaf(b)), b.trainingBeginn,
        )
    }

    /** Gibt es etwas, woran zu erinnern ist? Den Satz dazu, sonst null. */
    private fun erinnerung(b: Blick): Pair<String, String>? {
        // Praeparate: ab elf Uhr, wenn noch welche offen sind.
        if (b.offenePraeparate.isNotEmpty() && b.jetzt.hour >= 11) {
            val namen = b.offenePraeparate.take(2).joinToString(", ") +
                (if (b.offenePraeparate.size > 2) " …" else "")
            return "Präparate" to "$namen noch offen"
        }
        // Wasser: was bis jetzt haette getrunken sein sollen, gleichmaessig
        // von acht bis zweiundzwanzig Uhr - zwei Glaeser dahinter ist eine
        // Erinnerung wert.
        val ziel = b.wasserZiel
        val ml = b.wasserMl
        if (ziel != null && ml != null && b.jetzt.hour in 9..21) {
            val soll = ziel * ((b.jetzt.hour - 8) + b.jetzt.minute / 60.0) / 14.0
            val fehltGlaeser = ((soll - ml) / b.glasMl).toInt()
            if (fehltGlaeser >= 2) return "Wasser" to "$fehltGlaeser Gläser hinterher"
        }
        return null
    }

    private fun erinnerungLage(b: Blick): Lage {
        val (name, satz) = erinnerung(b) ?: ("Heute" to "")
        return if (name == "Wasser") {
            Lage(
                Art.ERINNERUNG, "Erinnerung", "Wasser",
                Zahlen.ganz(b.wasserMl) ?: "0", "ml", satz, anteil(b.wasserMl, b.wasserZiel),
                listOf(kSchritte(b), kAktiv(b), kSchlaf(b)), null,
            )
        } else {
            Lage(
                Art.ERINNERUNG, "Erinnerung", "Präparate",
                b.offenePraeparate.size.toString(), "offen", satz, null,
                listOf(kSchritte(b), kWasser(b), kSchlaf(b)), null,
            )
        }
    }

    private fun abend(b: Blick): Lage {
        val teile = mutableListOf<String>()
        val s = b.schritte
        val z = b.schritteZiel
        if (s != null && z != null) {
            teile += if (s >= z) "Schrittziel erreicht" else "noch " + Zahlen.ganz(z - s) + " Schritte zum Ziel"
        }
        val ml = b.wasserMl
        val wz = b.wasserZiel
        if (ml != null && wz != null && ml < wz) {
            val fehlt = Math.ceil((wz - ml) / b.glasMl).toInt()
            teile += "$fehlt ${if (fehlt == 1) "Glas" else "Gläser"} fehlen"
        }
        b.koffeinMg?.takeIf { it > 0 }?.let { teile += "Koffein " + Zahlen.ganz(it) + " mg" }
        return Lage(
            Art.ABEND, "Bilanz", "Schritte",
            Zahlen.ganz(b.schritte) ?: "—", "", teile.ifEmpty { listOf("Der Tag ist eingetragen.") }.joinToString(" · "),
            anteil(b.schritte, b.schritteZiel),
            listOf(kAktiv(b), kWasser(b), kSchlaf(b)), null,
        )
    }

    private fun tag(b: Blick): Lage {
        val s = b.schritte
        val z = b.schritteZiel
        val satz = when {
            s == null -> "Noch keine Schritte gezählt."
            z == null -> Zahlen.ganz(s) + " Schritte bis jetzt."
            s >= z -> "Ziel erreicht."
            else -> "noch " + Zahlen.ganz(z - s) + " bis zum Ziel"
        }
        return Lage(
            Art.TAG, "Heute", "Schritte",
            Zahlen.ganz(b.schritte) ?: "—", "", satz, anteil(b.schritte, b.schritteZiel),
            listOf(kAktiv(b), kWasser(b), kSchlaf(b)), null,
        )
    }
}
