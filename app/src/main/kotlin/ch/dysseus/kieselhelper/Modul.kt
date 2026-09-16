package ch.dysseus.kieselhelper

import org.json.JSONObject
import java.util.UUID

/**
 * Eine eingelesene Beschreibung: welche Uhr-App, welche Felder, was daraus
 * werden soll.
 *
 * Hier steht kein Programmcode aus dem Netz, sondern ein Zettel. Was der Zettel
 * anordnen kann, ist durch [Satzart] begrenzt — und was dort nicht steht, kann
 * er nicht verlangen.
 */
data class Modul(
    val name: String,
    val uuid: UUID,
    val quelle: String,
    val beschreibung: String,
    /** Feldname -> Nummer, wie sie in der Uhr-Nachricht ankommt. */
    val schluessel: Map<String, Int>,
    val regeln: List<Regel>,
) {
    /** Alle Berechtigungen, die dieses Modul braucht. */
    fun berechtigungen(): Set<String> = regeln.map { it.art.berechtigung }.toSet()

    companion object {
        /**
         * Beschreibung einlesen.
         *
         * Gibt ENTWEDER ein Modul ODER eine Liste von Fehlern zurueck, nie
         * beides und nie ein halbes Modul. Eine Beschreibung, die nur teilweise
         * verstanden wurde, schriebe teilweise falsche Werte in eine
         * Gesundheitsakte — und das faellt niemandem auf.
         */
        fun lies(text: String): Ergebnis {
            val fehler = mutableListOf<String>()
            val o = try {
                JSONObject(text)
            } catch (e: Exception) {
                return Ergebnis(null, listOf("Die Datei ist kein gueltiges JSON: " + e.message))
            }

            val format = o.optInt("format", 0)
            if (format != 1) {
                return Ergebnis(
                    null,
                    listOf("Format $format wird nicht verstanden; diese Fassung kennt nur 1.")
                )
            }

            val name = o.optString("name", "").trim()
            if (name.isEmpty()) fehler.add("Feld \"name\" fehlt.")

            val uuid = try {
                UUID.fromString(o.optString("uuid", ""))
            } catch (e: Exception) {
                fehler.add("Feld \"uuid\" fehlt oder ist keine UUID.")
                null
            }

            // --- Schluessel ---
            val schluessel = mutableMapOf<String, Int>()
            val so = o.optJSONObject("schluessel")
            if (so == null || so.length() == 0) {
                fehler.add("Feld \"schluessel\" fehlt oder ist leer.")
            } else {
                for (k in so.keys()) {
                    val n = so.optInt(k, -1)
                    if (n < 0) fehler.add("Schluessel \"$k\" hat keine gueltige Nummer.")
                    else schluessel[k] = n
                }
            }

            // --- Regeln ---
            val regeln = mutableListOf<Regel>()
            val ra = o.optJSONArray("regeln")
            if (ra == null || ra.length() == 0) {
                fehler.add("Feld \"regeln\" fehlt oder ist leer.")
            } else {
                for (i in 0 until ra.length()) {
                    val ro = ra.optJSONObject(i)
                    if (ro == null) {
                        fehler.add("Regel ${i + 1} ist kein Objekt.")
                        continue
                    }
                    val regel = liesRegel(ro, i + 1, schluessel, fehler)
                    if (regel != null) regeln.add(regel)
                }
            }

            if (fehler.isNotEmpty() || uuid == null) return Ergebnis(null, fehler)
            return Ergebnis(
                Modul(
                    name = name,
                    uuid = uuid,
                    quelle = o.optString("quelle", ""),
                    beschreibung = o.optString("beschreibung", ""),
                    schluessel = schluessel,
                    regeln = regeln,
                ),
                emptyList(),
            )
        }

        private fun liesRegel(
            ro: JSONObject,
            nr: Int,
            schluessel: Map<String, Int>,
            fehler: MutableList<String>,
        ): Regel? {
            val vorher = fehler.size

            // Welche Felder vorhanden sein muessen, damit die Regel greift.
            val wenn = mutableListOf<String>()
            val wa = ro.optJSONArray("wenn")
            if (wa == null || wa.length() == 0) {
                fehler.add("Regel $nr: \"wenn\" fehlt.")
            } else {
                for (i in 0 until wa.length()) wenn.add(wa.optString(i, ""))
            }

            val eo = ro.optJSONObject("eintrag")
            if (eo == null) {
                fehler.add("Regel $nr: \"eintrag\" fehlt.")
                return null
            }

            val artId = eo.optString("art", "")
            val art = Satzart.nachId(artId)
            if (art == null) {
                // Der wichtigste Fehler ueberhaupt. Er sagt nicht nur, dass es
                // nicht geht, sondern auch warum — sonst sucht jemand stundenlang
                // nach einem Tippfehler, wo in Wahrheit die App nicht mitspielt.
                fehler.add(
                    "Regel $nr: Satzart \"$artId\" kennt diese Fassung nicht. " +
                        "Moeglich sind: " + Satzart.entries.joinToString(", ") { it.id } +
                        ". Eine neue Art braucht eine neue Fassung der App — " +
                        "Berechtigungen lassen sich nicht nachreichen."
                )
                return null
            }

            // Je nach Form andere Pflichtfelder.
            var wertAus: String? = null
            var wertEinheit = ""
            val zeitFeld: String
            var dauer = 0L

            when (art.form) {
                Form.WERT_ZEITPUNKT -> {
                    val w = eo.optJSONObject("wert")
                    if (w == null) fehler.add("Regel $nr: \"wert\" fehlt (noetig fuer ${art.id}).")
                    wertAus = w?.optString("aus", "")
                    wertEinheit = w?.optString("einheit", "") ?: ""
                    zeitFeld = "zeitpunkt"
                }
                Form.MENGE_SPANNE -> {
                    val m = eo.optJSONObject("menge")
                    if (m == null) fehler.add("Regel $nr: \"menge\" fehlt (noetig fuer ${art.id}).")
                    wertAus = m?.optString("aus", "")
                    wertEinheit = m?.optString("einheit", "") ?: ""
                    zeitFeld = "beginn"
                    dauer = eo.optLong("dauer_s", 0L)
                    if (dauer <= 0L) {
                        fehler.add(
                            "Regel $nr: \"dauer_s\" fehlt oder ist null. Die Akte lehnt eine " +
                                "Spanne der Laenge null ab."
                        )
                    }
                }
                Form.SPANNE -> {
                    zeitFeld = "beginn"
                    dauer = eo.optLong("dauer_s", 0L)
                    if (dauer <= 0L) fehler.add("Regel $nr: \"dauer_s\" fehlt oder ist null.")
                }
            }

            // Einheit pruefen. Ein "g" statt "mg" verschoebe jeden Wert um das
            // Tausendfache, und niemand saehe es dem Eintrag an.
            if (wertAus != null && art.einheit.isNotEmpty() && wertEinheit != art.einheit) {
                fehler.add(
                    "Regel $nr: ${art.id} erwartet die Einheit \"${art.einheit}\", " +
                        "die Beschreibung sagt \"$wertEinheit\"."
                )
            }

            val zo = eo.optJSONObject(zeitFeld)
            if (zo == null) {
                fehler.add("Regel $nr: \"$zeitFeld\" fehlt.")
            }
            val zeitAus = zo?.optString("aus", "") ?: ""
            val zeitEinheit = zo?.optString("einheit", "s") ?: "s"
            if (zeitEinheit != "s" && zeitEinheit != "ms") {
                fehler.add("Regel $nr: Zeiteinheit \"$zeitEinheit\" — moeglich sind \"s\" und \"ms\".")
            }

            val nichtZweimal = ro.optString("nicht_zweimal_fuer", "").ifEmpty { null }

            // Jeder genannte Schluessel muss oben deklariert sein — sonst stuende
            // hier ein Name, den niemand einer Nummer zuordnet.
            val benutzt = mutableSetOf<String>()
            benutzt.addAll(wenn)
            wertAus?.let { if (it.isNotEmpty()) benutzt.add(it) }
            if (zeitAus.isNotEmpty()) benutzt.add(zeitAus)
            nichtZweimal?.let { benutzt.add(it) }
            for (k in benutzt) {
                if (k.isEmpty()) fehler.add("Regel $nr: leerer Schluesselname.")
                else if (!schluessel.containsKey(k)) {
                    fehler.add("Regel $nr: Schluessel \"$k\" ist nicht deklariert.")
                }
            }

            if (fehler.size != vorher) return null
            return Regel(
                wenn = wenn,
                nichtZweimalFuer = nichtZweimal,
                art = art,
                wertAus = wertAus,
                zeitAus = zeitAus,
                zeitInMillisekunden = (zeitEinheit == "ms"),
                dauerSekunden = dauer,
                meldung = ro.optString("meldung", ""),
            )
        }
    }

    /** Entweder ein Modul oder Fehler — nie beides. */
    data class Ergebnis(val modul: Modul?, val fehler: List<String>)
}

/** Eine Anweisung: unter dieser Bedingung diesen Satz eintragen. */
data class Regel(
    /** Diese Felder muessen in der Nachricht stehen, damit die Regel greift. */
    val wenn: List<String>,
    /** Zweimal derselbe Wert hier heisst: schon eingetragen, nichts tun. */
    val nichtZweimalFuer: String?,
    val art: Satzart,
    /** Feld, aus dem der Wert kommt; null bei Arten ohne Wert. */
    val wertAus: String?,
    /** Feld, aus dem der Zeitpunkt kommt. */
    val zeitAus: String,
    val zeitInMillisekunden: Boolean,
    val dauerSekunden: Long,
    /** Text fuer die Statusanzeige; {FELD} wird ersetzt. */
    val meldung: String,
)
