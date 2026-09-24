package ch.dysseus.kieselhelper

import java.io.File
import java.util.Locale

/**
 * Die Texte der App ohne Telefon - gelesen aus derselben strings.xml.
 *
 * WARUM NICHT EINFACH EIN CONTEXT: im Unit-Test ist android.jar nur eine
 * Attrappe, getString wirft. Statt einer Nachbildung mit erfundenen Saetzen
 * liest dieser Lieferant die echte Datei der gewaehlten Sprache - der Test
 * prueft damit auch, dass die Uebersetzung da ist und ihre Platzhalter passen.
 *
 * Die Namen zu den Nummern kommen aus der R-Klasse, die der Build erzeugt.
 */
class TestTexte(val ordner: String, val locale: Locale) : Texte {

    private val texte: Map<String, String>
    private val mehrzahlen: Map<String, Map<String, String>>

    init {
        val datei = Ressourcen.datei(ordner)
        texte = Ressourcen.texte(datei)
        mehrzahlen = Ressourcen.mehrzahlen(datei)
    }

    override fun text(id: Int, vararg args: Any): String {
        val name = Ressourcen.name(id, R.string::class.java)
        val roh = texte[name] ?: error("$ordner: kein Text $name")
        return if (args.isEmpty()) roh else String.format(locale, roh, *args)
    }

    override fun mehrzahl(id: Int, anzahl: Int, vararg args: Any): String {
        val name = Ressourcen.name(id, R.plurals::class.java)
        val formen = mehrzahlen[name] ?: error("$ordner: keine Mehrzahl $name")
        // Die Regeln der fuenf Sprachen: Franzoesisch zaehlt die Null zur
        // Einzahl, die anderen nur die Eins.
        val eins = if (locale.language == "fr") anzahl in 0..1 else anzahl == 1
        val roh = (if (eins) formen["one"] else null) ?: formen.getValue("other")
        return String.format(locale, roh, *args)
    }
}

/** Liest strings.xml so, wie Android sie liest - fuer die Tests genug. */
object Ressourcen {

    /** Gradle startet die Tests im Modulordner; die IDE manchmal eine Ebene hoeher. */
    fun datei(ordner: String): File =
        listOf(File("src/main/res/$ordner/strings.xml"), File("app/src/main/res/$ordner/strings.xml"))
            .firstOrNull { it.exists() } ?: error("strings.xml in $ordner nicht gefunden")

    fun texte(datei: File): Map<String, String> =
        Regex("""<string name="(\w+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(datei.readText())
            .associate { it.groupValues[1] to entschluessle(it.groupValues[2]) }

    fun mehrzahlen(datei: File): Map<String, Map<String, String>> =
        Regex("""<plurals name="(\w+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(datei.readText())
            .associate { p ->
                p.groupValues[1] to Regex("""<item quantity="(\w+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(p.groupValues[2])
                    .associate { it.groupValues[1] to entschluessle(it.groupValues[2]) }
            }

    /** Rohtext wie in der Datei, noch mit Maskierung - fuer die Pruefung der Datei selbst. */
    fun roh(datei: File): Map<String, String> =
        Regex("""<(?:string|item) name="(\w+)"[^>]*>(.*?)</(?:string|item)>|<plurals name="(\w+)">(.*?)</plurals>""",
            RegexOption.DOT_MATCHES_ALL)
            .findAll(datei.readText())
            .associate { m ->
                if (m.groupValues[1].isNotEmpty()) m.groupValues[1] to m.groupValues[2]
                else m.groupValues[3] to m.groupValues[4]
            }

    private fun entschluessle(s: String): String {
        val b = StringBuilder()
        var i = 0
        val t = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&amp;", "&")
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length) {
                b.append(if (t[i + 1] == 'n') '\n' else t[i + 1])
                i += 2
            } else {
                b.append(c)
                i++
            }
        }
        return b.toString()
    }

    fun name(id: Int, klasse: Class<*>): String =
        klasse.fields.firstOrNull { it.type == Int::class.javaPrimitiveType && it.getInt(null) == id }?.name
            ?: error("keine Ressource mit Nummer $id")
}
