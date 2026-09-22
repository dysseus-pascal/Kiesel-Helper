package ch.dysseus.kieselhelper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.time.LocalDate

/**
 * Das eigene Gedaechtnis: ein Tag, eine Zeile.
 *
 * WARUM NOCH EINE DATENBANK, wo doch die Gesundheitsakte schon eine ist? Weil
 * die Akte vergisst. Health Connect haelt die Rohdaten NICHT ewig - was aelter
 * ist, ist weg, und mit ihm jede Aussage darueber, wie ein Mittwoch bei einem
 * normalerweise aussieht. Ein Tagesstand kostet gut hundert Zeichen; ein Jahr
 * passt damit in weniger, als ein einziges Foto braucht.
 *
 * EINE ZEILE JE TAG, keine Rohdaten. Es geht nicht darum, die Akte zu kopieren
 * - der Pulsverlauf von vorletztem Dienstag interessiert niemanden. Es geht um
 * die zwoelf Zahlen, aus denen sich ein Muster lesen laesst.
 *
 * NULL BLEIBT NULL. Ein Feld, das nichts weiss, wird nie mit einer Null
 * gefuellt, und [merke] ueberschreibt einen bekannten Wert NICHT mit einem
 * unbekannten: wer morgens die App oeffnet, hat noch keinen Schlaf von heute
 * Nacht in der Akte, und der gestrige Eintrag darf davon nicht sterben.
 *
 * Kein Room: das braeuchte einen Annotationsverarbeiter im Bau, fuer eine
 * Tabelle mit dreizehn Spalten und vier Abfragen.
 */
class Speicher(context: Context) : SQLiteOpenHelper(context, NAME, null, FASSUNG) {

    companion object {
        private const val NAME = "gesundheit.db"
        private const val FASSUNG = 6

        /** Die Spalten, die einen Messwert tragen - in der Reihenfolge der Tabelle. */
        val SPALTEN = listOf(
            "schritte", "distanz", "kalorien", "aktiv", "wasser",
            "schlaf", "tief", "rem", "leicht", "wach",
            "ruhepuls", "puls_min", "puls_hoch", "puls_tief", "hrv",
            "supp_faellig", "supp_genommen",
            "schlaf_von", "schlaf_bis", "schlaf_mitte",
            "energie", "koffein_mg", "koffein_letzt",
            "puls_nacht_sd",
        )

        /** Was seit Fassung 1 dazugekommen ist - fuer [onUpgrade]. */
        private val NACHGEWACHSEN = mapOf(
            2 to listOf("puls_hoch", "puls_tief"),
            3 to listOf("supp_faellig", "supp_genommen"),
            4 to listOf("schlaf_von", "schlaf_bis", "schlaf_mitte"),
            5 to listOf("energie", "koffein_mg", "koffein_letzt"),
            6 to listOf("puls_nacht_sd"),
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tag (" +
                "datum TEXT PRIMARY KEY, " +
                SPALTEN.joinToString(", ") { "$it REAL" } + ", " +
                "geaendert INTEGER)"
        )
    }

    /**
     * Spalten nachziehen - mit ALTER TABLE und NIEMALS mit DROP.
     *
     * Die Zeilen sind der ganze Wert dieser Datei: was die Akte laengst
     * vergessen hat, steht nur noch hier. Eine Tabelle neu anzulegen ist in
     * jeder anderen App eine Lappalie und hier ein Datenverlust.
     */
    override fun onUpgrade(db: SQLiteDatabase, alt: Int, neu: Int) {
        ((alt + 1)..neu).forEach { fassung ->
            NACHGEWACHSEN[fassung]?.forEach { spalte ->
                try {
                    db.execSQL("ALTER TABLE tag ADD COLUMN $spalte REAL")
                } catch (e: Exception) {
                    Log.w(PebbleEmpfaenger.TAG, "Spalte $spalte: " + e.message)
                }
            }
        }
    }

    /**
     * Einen Tagesstand festhalten.
     *
     * Was in [werte] fehlt oder `null` ist, bleibt in der Zeile unberuehrt.
     * Das ist der ganze Unterschied zwischen "heute noch nicht gemessen" und
     * "heute war es null".
     */
    fun merke(tag: LocalDate, werte: Map<String, Double?>) {
        val vorhanden = werte.filterValues { it != null }
        if (vorhanden.isEmpty()) return
        try {
            val inhalt = ContentValues().apply {
                put("datum", tag.toString())
                put("geaendert", System.currentTimeMillis() / 1000)
                vorhanden.forEach { (spalte, wert) ->
                    if (spalte in SPALTEN) put(spalte, wert)
                }
            }
            // KEIN use{} um die Datenbank: der Helfer haelt sie offen und
            // gibt sie jedem weiter. Wer sie hier schliesst, zieht sie einem
            // gleichzeitig lesenden Aufruf unter den Fuessen weg.
            val db = writableDatabase
            val geaendert = db.update("tag", inhalt, "datum = ?", arrayOf(tag.toString()))
            if (geaendert == 0) db.insert("tag", null, inhalt)
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Speicher schreiben: " + e.message)
        }
    }

    /**
     * Einen Wert dazuzaehlen statt ihn zu setzen.
     *
     * Fuer das, was im Lauf des Tages mehrfach kommt: drei Kaffee sind drei
     * Eintraege und eine Summe. Ein blosses [merke] ueberschriebe den zweiten
     * mit dem dritten.
     */
    fun zaehleDazu(tag: LocalDate, spalte: String, wieviel: Double) {
        val bisher = wert(tag, spalte) ?: 0.0
        merke(tag, mapOf(spalte to bisher + wieviel))
    }

    /**
     * Alles, was in der Tabelle steht - fuer die Sicherung.
     *
     * EIN JAHR SIND 365 ZEILEN mit je zwei Dutzend Zahlen. Das passt in den
     * Speicher, und eine Sicherung, die stueckweise liest, waere zwischen
     * zwei Stuecken nicht mehr derselbe Stand.
     */
    fun alleTage(): List<Pair<LocalDate, Map<String, Double>>> {
        val aus = mutableListOf<Pair<LocalDate, Map<String, Double>>>()
        try {
            readableDatabase.rawQuery(
                "SELECT * FROM tag ORDER BY datum", null
            ).use { z ->
                while (z.moveToNext()) {
                    val datum = z.getString(z.getColumnIndexOrThrow("datum"))
                    val werte = mutableMapOf<String, Double>()
                    SPALTEN.forEach { spalte ->
                        val i = z.getColumnIndex(spalte)
                        if (i >= 0 && !z.isNull(i)) werte[spalte] = z.getDouble(i)
                    }
                    aus += LocalDate.parse(datum) to werte.toMap()
                }
            }
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Tabelle lesen: " + e.message)
        }
        return aus
    }

    /** Welche Tage schon eine Zeile haben - um nur die Luecken nachzutragen. */
    fun bekannteTage(): Set<LocalDate> = frage(
        "SELECT datum FROM tag", { es -> LocalDate.parse(es) }
    ).toSet()

    /**
     * Eine Groesse ueber alle Tage, aufsteigend.
     *
     * Alles auf einmal in den Speicher zu holen ist hier kein Leichtsinn: ein
     * Jahr sind 365 Zeilen mit je einer Zahl. Die Auswertung in Kotlin zu
     * rechnen statt in SQL spart eine Handvoll Abfragen, die man sonst
     * einzeln richtig hinschreiben muesste.
     */
    fun reihe(spalte: String): List<Pair<LocalDate, Double>> {
        if (spalte !in SPALTEN) return emptyList()
        val ergebnis = mutableListOf<Pair<LocalDate, Double>>()
        try {
            readableDatabase.rawQuery(
                "SELECT datum, $spalte FROM tag " +
                    "WHERE $spalte IS NOT NULL ORDER BY datum", null
            ).use { zeiger ->
                while (zeiger.moveToNext()) {
                    ergebnis += LocalDate.parse(zeiger.getString(0)) to zeiger.getDouble(1)
                }
            }
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Speicher lesen: " + e.message)
        }
        return ergebnis
    }

    /**
     * Ein einzelner Wert eines einzelnen Tages.
     *
     * Fuer die Dinge, die NICHT aus der Gesundheitsakte kommen: Supplemente
     * stehen nur hier, weil die Akte keine Satzart fuer "genommen" kennt.
     */
    fun wert(tag: LocalDate, spalte: String): Double? {
        if (spalte !in SPALTEN) return null
        var ergebnis: Double? = null
        try {
            readableDatabase.rawQuery(
                "SELECT $spalte FROM tag WHERE datum = ?", arrayOf(tag.toString())
            ).use { zeiger ->
                if (zeiger.moveToNext() && !zeiger.isNull(0)) ergebnis = zeiger.getDouble(0)
            }
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Speicher Einzelwert: " + e.message)
        }
        return ergebnis
    }

    /** Wie viele Tage ueberhaupt dastehen, und seit wann. */
    fun umfang(): Pair<Int, LocalDate?> {
        val tage = frage("SELECT datum FROM tag ORDER BY datum", { LocalDate.parse(it) })
        return tage.size to tage.firstOrNull()
    }

    private fun <T> frage(sql: String, wandle: (String) -> T): List<T> {
        val ergebnis = mutableListOf<T>()
        try {
            readableDatabase.rawQuery(sql, null).use { zeiger ->
                while (zeiger.moveToNext()) ergebnis += wandle(zeiger.getString(0))
            }
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Speicher fragen: " + e.message)
        }
        return ergebnis
    }
}
