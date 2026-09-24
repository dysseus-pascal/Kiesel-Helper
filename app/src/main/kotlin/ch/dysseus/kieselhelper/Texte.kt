package ch.dysseus.kieselhelper

import android.content.Context

/**
 * Woher die Saetze kommen.
 *
 * DIE APP SPRICHT FUENF SPRACHEN, und die Texte stehen deshalb in den
 * Ressourcen (values, values-de, -fr, -it, -es) statt im Code. Wo ein Context
 * zur Hand ist, holt man sie mit getString wie ueberall in Android.
 *
 * DIESE SCHNITTSTELLE GIBT ES FUER DIE REINE LOGIK. Widgetlage baut Saetze,
 * soll sich aber ohne Telefon pruefen lassen - und ein Context ist im
 * Unit-Test nur eine Attrappe, die bei jedem Aufruf wirft. Die Logik bekommt
 * darum einen Texte-Lieferanten: die App reicht die Ressourcen durch, der Test
 * liest dieselbe strings.xml selbst.
 */
interface Texte {
    fun text(id: Int, vararg args: Any): String

    /** Mehrzahl nach den Regeln der Sprache: "1 Glas", "3 Gläser". */
    fun mehrzahl(id: Int, anzahl: Int, vararg args: Any): String
}

/** Die Texte aus den Ressourcen - in der Sprache, die das Telefon gerade spricht. */
fun Context.texte(): Texte {
    val ctx = this
    return object : Texte {
        override fun text(id: Int, vararg args: Any): String =
            if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)

        override fun mehrzahl(id: Int, anzahl: Int, vararg args: Any): String =
            ctx.resources.getQuantityString(id, anzahl, *args)
    }
}
