package ch.dysseus.kieselhelper

import org.json.JSONObject
import java.util.UUID

/**
 * Woher die Werte kommen.
 *
 * Bis Fassung 1 gab es nur eine Quelle, und sie war nicht benannt - jeder
 * Zettel meinte eine Uhr-App. Mit den Benachrichtigungen des Telefons kommt
 * eine zweite dazu, und damit muss der Zettel sagen, welche er meint.
 */
sealed class Quelle {

    /** Eine AppMessage von einer Uhr-App. */
    data class Uhr(val uuid: UUID) : Quelle()

    /** Eine Benachrichtigung eines Pakets auf dem Telefon. */
    data class Benachrichtigung(val paket: String) : Quelle()

    /** Prueft, ob ein Feldname in dieser Quelle ueberhaupt vorkommen kann. */
    fun kenntFeld(name: String, schluessel: Map<String, Int>): Boolean = when (this) {
        is Uhr -> name == JETZT || schluessel.containsKey(name)
        is Benachrichtigung -> Benachrichtigungsfeld.gueltig(name)
    }

    fun klartext(): String = when (this) {
        is Uhr -> "Uhr-App $uuid"
        is Benachrichtigung -> "Benachrichtigungen von $paket"
    }
}

/**
 * Was mit den Werten geschehen soll.
 *
 * Drei Senken, und eine Regel waehlt genau eine. Mehr Formen als diese kann
 * ein Zettel nicht verlangen - das ist die Mauer, die auch nachgeladener Code
 * nicht einreissen wuerde.
 */
sealed class Senke {

    /** In die Gesundheitsakte eintragen. */
    data class Akteneintrag(
        val art: Satzart,
        val wert: Bezug?,
        val zeit: Bezug,
        val zeitInMillisekunden: Boolean,
        val dauerSekunden: Long,
    ) : Senke()

    /** Als AppMessage an eine Uhr-App schicken. */
    data class AnDieUhr(
        val uuid: UUID,
        val starten: Boolean,
        /**
         * Hoechstens alle n Sekunden senden; 0 = ohne Grenze.
         *
         * Google Maps frischt seine Navigationsmeldung im Sekundentakt auf.
         * Ohne Riegel ginge jedes Mal eine Nachricht ueber Bluetooth an die
         * Uhr - fuer eine Anzeige, die sich dabei um wenige Meter aendert.
         * Der Riegel "nicht_zweimal_fuer" hilft hier nicht: er wuerde die
         * Anweisung einfrieren, und genau die Entfernung soll ja laufen.
         */
        val hoechstensAlleS: Long,
        val felder: List<Feldbelegung>,
    ) : Senke()

    /** Als Benachrichtigung auf dem Telefon zeigen. */
    data class Meldung(
        val titel: String,
        val text: String,
    ) : Senke()
}

/**
 * Wie aus einem Feld der Quelle ein Wert wird.
 *
 * `muster` ist der Grund, warum es diese Klasse ueberhaupt gibt: eine
 * Benachrichtigung traegt "in 250 m", nicht 250. Ohne einen Ausschnitt waere
 * die Haelfte aller Quellen unbrauchbar. Mehr Rechnen als Ausschneiden und
 * Malnehmen gibt es hier nicht - ein Zettel soll kein Programm werden.
 */
data class Bezug(
    val aus: String,
    val muster: Regex?,
    val faktor: Double,
)

/**
 * Ein Feld einer ausgehenden Nachricht an die Uhr.
 *
 * Entweder aus einem Feld der Quelle ([aus]) oder fest ([fest]). Fest braucht
 * man fuer das Ende: wenn eine Navigation vorbei ist, gibt es keine Quelle
 * mehr, aus der "Navigation beendet" kommen koennte - und ohne eine Null fuer
 * die Strecke zeigte die Uhr den letzten Wert weiter.
 */
data class Feldbelegung(
    val name: String,
    val nummer: Int,
    val aus: String?,
    val fest: Wert?,
    val alsText: Boolean,
    val muster: Regex?,
    val faktor: Double,
)

/**
 * Wann eine Regel ueberhaupt gepruefte wird.
 *
 * Bis hierher gab es nur das Erscheinen: eine Nachricht kommt an, eine
 * Benachrichtigung wird gezeigt. Das Verschwinden ist aber genauso eine
 * Nachricht - und bei einer Navigation die wichtigere zweite Haelfte. Ohne sie
 * zeigt die Uhr die letzte Anweisung weiter, obwohl auf dem Telefon laengst
 * niemand mehr navigiert.
 */
enum class Ausloeser { ERSCHEINT, VERSCHWINDET }

/** Eine Anweisung: unter dieser Bedingung das hier tun. */
data class Regel(
    /** Erscheinen oder Verschwinden der Quelle. */
    val ausloeser: Ausloeser,
    /** Diese Felder muessen vorhanden sein, damit die Regel greift. */
    val wenn: List<String>,
    /** Diese Felder muessen ausserdem zum Muster passen. */
    val nurWenn: Map<String, Regex>,
    /** Zweimal derselbe Wert hier heisst: schon getan, nichts tun. */
    val nichtZweimalFuer: String?,
    val senke: Senke,
    /** Text fuer die Statusanzeige; {FELD} wird ersetzt. */
    val meldung: String,
)

/**
 * Ein eingelesener Zettel: welche Quelle, welche Felder, was daraus werden soll.
 *
 * Hier steht kein Programmcode aus dem Netz, sondern ein Zettel. Was der Zettel
 * anordnen kann, ist durch [Quelle] und [Senke] begrenzt - und was dort nicht
 * steht, kann er nicht verlangen.
 */
data class Modul(
    val name: String,
    val quelle: Quelle,
    val herkunft: String,
    val beschreibung: String,
    /** Feldname -> Nummer, wie das Feld auf dem Draht zur Uhr heisst. */
    val schluessel: Map<String, Int>,
    val regeln: List<Regel>,
) {
    /** Alle Berechtigungen der Gesundheitsakte, die dieser Zettel braucht. */
    fun berechtigungen(): Set<String> = regeln
        .mapNotNull { (it.senke as? Senke.Akteneintrag)?.art?.berechtigung }
        .toSet()

    /** Braucht dieser Zettel Zugriff auf die Benachrichtigungen? */
    fun brauchtBenachrichtigungen(): Boolean = quelle is Quelle.Benachrichtigung

    /**
     * Kurzfassung dessen, was der Zettel tut - fuer die Karte.
     *
     * Der Anlass gehoert davor. Ohne ihn lesen sich zwei Regeln desselben
     * Zettels gleich - "schickt ... an die Uhr" und noch einmal "schickt ...
     * an die Uhr" -, und niemand sieht, welche fuers Ende gilt.
     */
    fun taetigkeiten(): List<String> = regeln.map { r ->
        val anlass = if (r.ausloeser == Ausloeser.VERSCHWINDET) "am Ende: " else ""
        anlass + when (val s = r.senke) {
            is Senke.Akteneintrag ->
                "trägt " + s.art.klartext + " ein, aus " + (s.wert?.aus ?: s.zeit.aus)
            is Senke.AnDieUhr ->
                "schickt " + s.felder.joinToString(", ") { it.name } + " an die Uhr"
            is Senke.Meldung ->
                "meldet auf dem Telefon"
        }
    }

    companion object {

        /** Welche Formatnummern diese Fassung versteht. */
        private val FORMATE = setOf(1, 2)

        /**
         * Zettel einlesen.
         *
         * Gibt ENTWEDER ein Modul ODER eine Liste von Fehlern zurueck, nie
         * beides und nie ein halbes Modul. Ein Zettel, der nur teilweise
         * verstanden wurde, schriebe teilweise falsche Werte in eine
         * Gesundheitsakte - und das faellt niemandem auf.
         */
        fun lies(text: String): Ergebnis {
            val fehler = mutableListOf<String>()
            val o = try {
                JSONObject(text)
            } catch (e: Exception) {
                return Ergebnis(null, listOf("Die Datei ist kein gültiges JSON: " + e.message))
            }

            val format = o.optInt("format", 0)
            if (format !in FORMATE) {
                return Ergebnis(
                    null,
                    listOf(
                        "Format $format wird nicht verstanden; diese Fassung kennt " +
                            FORMATE.joinToString(" und ") + "."
                    )
                )
            }

            val name = o.optString("name", "").trim()
            if (name.isEmpty()) fehler.add("Feld \"name\" fehlt.")

            // --- Schluessel ---
            val schluessel = mutableMapOf<String, Int>()
            val so = o.optJSONObject("schluessel")
            if (so != null) {
                for (k in so.keys()) {
                    val n = so.optInt(k, -1)
                    if (n < 0) fehler.add("Schlüssel \"$k\" hat keine gültige Nummer.")
                    else schluessel[k] = n
                }
            }

            val quelle = liesQuelle(o, schluessel, fehler)

            // --- Regeln ---
            val regeln = mutableListOf<Regel>()
            val ra = o.optJSONArray("regeln")
            if (ra == null || ra.length() == 0) {
                fehler.add("Feld \"regeln\" fehlt oder ist leer.")
            } else if (quelle != null) {
                for (i in 0 until ra.length()) {
                    val ro = ra.optJSONObject(i)
                    if (ro == null) {
                        fehler.add("Regel ${i + 1} ist kein Objekt.")
                        continue
                    }
                    liesRegel(ro, i + 1, quelle, schluessel, fehler)?.let { regeln.add(it) }
                }
            }

            if (fehler.isNotEmpty() || quelle == null) return Ergebnis(null, fehler)
            return Ergebnis(
                Modul(
                    name = name,
                    quelle = quelle,
                    // In Fassung 1 war "quelle" eine Adresse zum Nachlesen, in
                    // Fassung 2 ist es ein Objekt. optString wuerde bei einem
                    // Objekt dessen ganzen JSON-Text zurueckgeben - deshalb der
                    // ausdrueckliche Test auf Zeichenkette.
                    herkunft = (o.opt("quelle") as? String) ?: o.optString("seite", ""),
                    beschreibung = o.optString("beschreibung", ""),
                    schluessel = schluessel,
                    regeln = regeln,
                ),
                emptyList(),
            )
        }

        /**
         * Die Quelle lesen - und fuer Fassung 1 erraten.
         *
         * Ein Zettel der Fassung 1 hat kein Feld `quelle` im neuen Sinn,
         * sondern ein `uuid` ganz oben, und meint damit immer eine Uhr-App.
         * Das bleibt gueltig: die beiden Zettel, die es gibt, sollen
         * weiterlaufen, ohne angefasst zu werden.
         *
         * In Fassung 1 war `quelle` ausserdem eine Adresse zum Nachlesen. Sie
         * wird weiter als Text gelesen, nur eben nicht mehr als Quelle im Sinn
         * von "woher kommen die Werte" - deshalb der Blick auf `art`.
         */
        private fun liesQuelle(
            o: JSONObject,
            schluessel: Map<String, Int>,
            fehler: MutableList<String>,
        ): Quelle? {
            val qo = o.optJSONObject("quelle")
            if (qo == null) {
                val uuid = alsUuid(o.optString("uuid", ""))
                if (uuid == null) {
                    fehler.add("Weder \"quelle\" mit \"art\" noch ein \"uuid\" ganz oben ist angegeben.")
                    return null
                }
                if (schluessel.isEmpty()) {
                    fehler.add("Feld \"schluessel\" fehlt oder ist leer.")
                }
                return Quelle.Uhr(uuid)
            }

            return when (val art = qo.optString("art", "")) {
                "appmessage" -> {
                    val uuid = alsUuid(qo.optString("uuid", ""))
                    if (uuid == null) {
                        fehler.add("Quelle \"appmessage\": \"uuid\" fehlt oder ist keine UUID.")
                        null
                    } else {
                        if (schluessel.isEmpty()) {
                            fehler.add("Quelle \"appmessage\" braucht \"schluessel\".")
                        }
                        Quelle.Uhr(uuid)
                    }
                }
                "benachrichtigung" -> {
                    val paket = qo.optString("paket", "").trim()
                    if (paket.isEmpty()) {
                        fehler.add("Quelle \"benachrichtigung\": \"paket\" fehlt.")
                        null
                    } else {
                        Quelle.Benachrichtigung(paket)
                    }
                }
                else -> {
                    fehler.add(
                        "Quelle \"$art\" kennt diese Fassung nicht. Möglich sind: " +
                            "appmessage, benachrichtigung. Eine neue Quelle braucht eine neue " +
                            "Fassung der App - Komponenten lassen sich nicht nachreichen."
                    )
                    null
                }
            }
        }

        private fun liesRegel(
            ro: JSONObject,
            nr: Int,
            quelle: Quelle,
            schluessel: Map<String, Int>,
            fehler: MutableList<String>,
        ): Regel? {
            val vorher = fehler.size
            val benutzt = mutableSetOf<String>()

            val ausloeser = when (val a = ro.optString("ausloeser", "erscheint")) {
                "erscheint" -> Ausloeser.ERSCHEINT
                "verschwindet" -> {
                    // Eine AppMessage kann nicht verschwinden - sie kommt an
                    // oder nicht. Wer das hier schreibt, hat eine Regel, die
                    // nie greift, und merkt es nie.
                    if (quelle is Quelle.Uhr) {
                        fehler.add(
                            "Regel $nr: \"verschwindet\" gibt es nur bei Benachrichtigungen. " +
                                "Eine AppMessage kommt an oder nicht."
                        )
                    }
                    Ausloeser.VERSCHWINDET
                }
                else -> {
                    fehler.add(
                        "Regel $nr: \"ausloeser\": \"$a\" - möglich sind \"erscheint\" " +
                            "und \"verschwindet\"."
                    )
                    Ausloeser.ERSCHEINT
                }
            }

            val wenn = mutableListOf<String>()
            val wa = ro.optJSONArray("wenn")
            if (wa == null || wa.length() == 0) {
                fehler.add("Regel $nr: \"wenn\" fehlt.")
            } else {
                for (i in 0 until wa.length()) wenn.add(wa.optString(i, ""))
            }
            benutzt.addAll(wenn)

            val nurWenn = mutableMapOf<String, Regex>()
            ro.optJSONObject("nur_wenn")?.let { nw ->
                for (k in nw.keys()) {
                    alsMuster(nw.optString(k, ""), "Regel $nr: \"nur_wenn\" bei \"$k\"", fehler)
                        ?.let { nurWenn[k] = it }
                    benutzt.add(k)
                }
            }

            val nichtZweimal = ro.optString("nicht_zweimal_fuer", "").ifEmpty { null }
            nichtZweimal?.let { benutzt.add(it) }

            val senkenNamen = listOf("eintrag", "senden", "melden").filter { ro.has(it) }
            if (senkenNamen.size != 1) {
                fehler.add(
                    "Regel $nr: genau eines von \"eintrag\", \"senden\" oder \"melden\" muss " +
                        "dastehen; gefunden: " +
                        (if (senkenNamen.isEmpty()) "keines" else senkenNamen.joinToString(", "))
                )
                return null
            }

            val senke = when (senkenNamen[0]) {
                "eintrag" -> liesAkteneintrag(ro.getJSONObject("eintrag"), nr, benutzt, fehler)
                "senden" -> liesAnDieUhr(ro.getJSONObject("senden"), nr, schluessel, benutzt, fehler)
                else -> liesMeldung(ro.getJSONObject("melden"), nr, fehler)
            }

            // Jedes genannte Feld muss die Quelle auch liefern koennen.
            for (k in benutzt) {
                if (k.isEmpty()) fehler.add("Regel $nr: leerer Feldname.")
                else if (!quelle.kenntFeld(k, schluessel)) {
                    fehler.add(
                        "Regel $nr: Feld \"$k\" gibt es in dieser Quelle nicht (" +
                            quelle.klartext() + ")."
                    )
                }
            }

            if (fehler.size != vorher || senke == null) return null
            return Regel(
                ausloeser = ausloeser,
                wenn = wenn,
                nurWenn = nurWenn,
                nichtZweimalFuer = nichtZweimal,
                senke = senke,
                meldung = ro.optString("meldung", ""),
            )
        }

        private fun liesAkteneintrag(
            eo: JSONObject,
            nr: Int,
            benutzt: MutableSet<String>,
            fehler: MutableList<String>,
        ): Senke.Akteneintrag? {
            val artId = eo.optString("art", "")
            val art = Satzart.nachId(artId)
            if (art == null) {
                // Der wichtigste Fehler ueberhaupt. Er sagt nicht nur, dass es
                // nicht geht, sondern auch warum - sonst sucht jemand stundenlang
                // nach einem Tippfehler, wo in Wahrheit die App nicht mitspielt.
                fehler.add(
                    "Regel $nr: Satzart \"$artId\" kennt diese Fassung nicht. " +
                        "Möglich sind: " + Satzart.entries.joinToString(", ") { it.id } +
                        ". Eine neue Art braucht eine neue Fassung der App - " +
                        "Berechtigungen lassen sich nicht nachreichen."
                )
                return null
            }

            var wert: Bezug? = null
            var einheit = ""
            val zeitFeld: String
            var dauer = 0L

            when (art.form) {
                Form.WERT_ZEITPUNKT -> {
                    val w = eo.optJSONObject("wert")
                    if (w == null) {
                        fehler.add("Regel $nr: \"wert\" fehlt (nötig für ${art.id}).")
                    } else {
                        wert = liesBezug(w, "Regel $nr: \"wert\"", fehler)
                        einheit = w.optString("einheit", "")
                    }
                    zeitFeld = "zeitpunkt"
                }
                Form.MENGE_SPANNE -> {
                    val m = eo.optJSONObject("menge")
                    if (m == null) {
                        fehler.add("Regel $nr: \"menge\" fehlt (nötig für ${art.id}).")
                    } else {
                        wert = liesBezug(m, "Regel $nr: \"menge\"", fehler)
                        einheit = m.optString("einheit", "")
                    }
                    zeitFeld = "beginn"
                    dauer = eo.optLong("dauer_s", 0L)
                    if (dauer <= 0L) {
                        fehler.add(
                            "Regel $nr: \"dauer_s\" fehlt oder ist null. Die Akte lehnt eine " +
                                "Spanne der Länge null ab."
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
            if (wert != null && art.einheit.isNotEmpty() && einheit != art.einheit) {
                fehler.add(
                    "Regel $nr: ${art.id} erwartet die Einheit \"${art.einheit}\", " +
                        "der Zettel sagt \"$einheit\"."
                )
            }
            wert?.let { benutzt.add(it.aus) }

            val zo = eo.optJSONObject(zeitFeld)
            if (zo == null) {
                fehler.add("Regel $nr: \"$zeitFeld\" fehlt.")
                return null
            }
            val zeit = liesBezug(zo, "Regel $nr: \"$zeitFeld\"", fehler) ?: return null
            benutzt.add(zeit.aus)

            val zeitEinheit = zo.optString("einheit", "s")
            if (zeitEinheit != "s" && zeitEinheit != "ms") {
                fehler.add("Regel $nr: Zeiteinheit \"$zeitEinheit\" - möglich sind \"s\" und \"ms\".")
            }

            return Senke.Akteneintrag(
                art = art,
                wert = wert,
                zeit = zeit,
                zeitInMillisekunden = (zeitEinheit == "ms"),
                dauerSekunden = dauer,
            )
        }

        private fun liesAnDieUhr(
            so: JSONObject,
            nr: Int,
            schluessel: Map<String, Int>,
            benutzt: MutableSet<String>,
            fehler: MutableList<String>,
        ): Senke.AnDieUhr? {
            val uuid = alsUuid(so.optString("an", ""))
            if (uuid == null) {
                fehler.add("Regel $nr: \"senden.an\" fehlt oder ist keine UUID.")
                return null
            }
            val fo = so.optJSONObject("felder")
            if (fo == null || fo.length() == 0) {
                fehler.add("Regel $nr: \"senden.felder\" fehlt oder ist leer.")
                return null
            }
            val felder = mutableListOf<Feldbelegung>()
            for (name in fo.keys()) {
                val nummer = schluessel[name]
                if (nummer == null) {
                    fehler.add(
                        "Regel $nr: Feld \"$name\" ist nicht in \"schluessel\" deklariert - " +
                            "ohne Nummer weiss niemand, wo es auf dem Draht steht."
                    )
                    continue
                }
                val eo = fo.optJSONObject(name)
                if (eo == null) {
                    fehler.add("Regel $nr: \"senden.felder.$name\" ist kein Objekt.")
                    continue
                }
                val art = eo.optString("art", "text")
                if (art != "text" && art != "zahl") {
                    fehler.add(
                        "Regel $nr: Feld \"$name\" hat art \"$art\" - möglich sind " +
                            "\"text\" und \"zahl\"."
                    )
                    continue
                }

                // Fester Wert statt Feldbezug.
                if (eo.has("wert")) {
                    if (eo.has("aus")) {
                        fehler.add(
                            "Regel $nr: Feld \"$name\" hat \"aus\" UND \"wert\" - " +
                                "entweder aus der Quelle oder fest, nicht beides."
                        )
                        continue
                    }
                    val fest = if (art == "text") Wert.Text(eo.optString("wert", ""))
                    else Wert.Zahl(eo.optLong("wert", 0L))
                    felder.add(
                        Feldbelegung(name, nummer, null, fest, art == "text", null, 1.0)
                    )
                    continue
                }

                val bezug = liesBezug(eo, "Regel $nr: Feld \"$name\"", fehler) ?: continue
                benutzt.add(bezug.aus)
                felder.add(
                    Feldbelegung(
                        name = name,
                        nummer = nummer,
                        aus = bezug.aus,
                        fest = null,
                        alsText = (art == "text"),
                        muster = bezug.muster,
                        faktor = bezug.faktor,
                    )
                )
            }
            if (felder.isEmpty()) return null
            val takt = so.optLong("hoechstens_alle_s", 0L)
            if (takt < 0L) {
                fehler.add("Regel $nr: \"hoechstens_alle_s\" darf nicht negativ sein.")
                return null
            }
            return Senke.AnDieUhr(
                uuid = uuid,
                starten = so.optBoolean("starten", false),
                hoechstensAlleS = takt,
                felder = felder,
            )
        }

        private fun liesMeldung(
            mo: JSONObject,
            nr: Int,
            fehler: MutableList<String>,
        ): Senke.Meldung? {
            val text = mo.optString("text", "")
            if (text.isEmpty()) {
                fehler.add("Regel $nr: \"melden.text\" fehlt.")
                return null
            }
            return Senke.Meldung(titel = mo.optString("titel", ""), text = text)
        }

        private fun liesBezug(o: JSONObject, wo: String, fehler: MutableList<String>): Bezug? {
            val aus = o.optString("aus", "").trim()
            if (aus.isEmpty()) {
                fehler.add("$wo: \"aus\" fehlt.")
                return null
            }
            val musterRoh = o.optString("muster", "")
            val muster = if (musterRoh.isEmpty()) null
            else alsMuster(musterRoh, wo, fehler) ?: return null

            val faktor = o.optDouble("faktor", 1.0)
            if (faktor == 0.0 || faktor.isNaN() || faktor.isInfinite()) {
                fehler.add("$wo: \"faktor\" muss eine Zahl ungleich null sein.")
                return null
            }
            return Bezug(aus = aus, muster = muster, faktor = faktor)
        }

        private fun alsMuster(roh: String, wo: String, fehler: MutableList<String>): Regex? = try {
            Regex(roh)
        } catch (e: Exception) {
            fehler.add("$wo: \"$roh\" ist kein gültiger regulärer Ausdruck.")
            null
        }

        private fun alsUuid(roh: String): UUID? = try {
            if (roh.isEmpty()) null else UUID.fromString(roh)
        } catch (e: Exception) {
            null
        }
    }

    /** Entweder ein Modul oder Fehler - nie beides. */
    data class Ergebnis(val modul: Modul?, val fehler: List<String>)
}
