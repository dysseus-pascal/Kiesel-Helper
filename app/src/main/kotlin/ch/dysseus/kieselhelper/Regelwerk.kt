package ch.dysseus.kieselhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.time.Instant

/**
 * Der Kern: Regeln auf einen Satz Felder anwenden.
 *
 * Steht bewusst FUER SICH und nicht in einer Quelle. Bis Fassung 1 lag das
 * Regelwerk im Broadcast-Empfaenger, und damit haette die zweite Quelle - die
 * Benachrichtigungen - es entweder aufgerufen oder abgeschrieben. Aufgerufen
 * heisst: der Empfaenger wird zum Umweg. Abgeschrieben heisst: zwei Fassungen
 * derselben Regel, und die eine faellt irgendwann hinter die andere zurueck.
 *
 * Eine Quelle tut deshalb genau zwei Dinge: Felder benennen und hierher
 * reichen.
 */
object Regelwerk {

    /**
     * Alle Regeln eines Zettels der Reihe nach pruefen.
     *
     * Rueckgabe ist der Text fuer die Statusanzeige.
     */
    suspend fun wendeAn(
        context: Context,
        modul: Modul,
        felder: Map<String, Wert>,
    ): String {
        val verlauf = Verlauf(context)
        val akte = Akte(context)
        val meldungen = mutableListOf<String>()

        // Jede Quelle liefert den Augenblick des Empfangs mit - fuer Zettel,
        // deren Quelle keinen eigenen Zeitstempel traegt.
        val alle = felder + (JETZT to Wert.Zahl(System.currentTimeMillis() / 1000))

        modul.regeln.forEachIndexed { nr, regel ->
            // Greift die Regel? Alle unter `wenn` genannten Felder muessen da
            // sein. Ohne diese Bedingung truege eine blosse Standmeldung - die
            // kommt bei jedem Start und jedem Wecker - jedes Mal einen weiteren
            // Eintrag in die Akte.
            if (regel.wenn.any { alle[it] == null }) return@forEachIndexed

            // Und sie muessen passen. Das ist der Unterschied zwischen "Google
            // Maps hat eine Benachrichtigung" und "Google Maps navigiert".
            val passtNicht = regel.nurWenn.any { (feld, muster) ->
                val w = alle[feld] ?: return@any true
                !muster.containsMatchIn(w.alsText())
            }
            if (passtNicht) return@forEachIndexed

            // Schon getan? Eine erneut zugestellte Nachricht darf nicht ein
            // zweites Mal wirken - und eine Navigationsanweisung, die sich im
            // Sekundentakt wiederholt, auch nicht.
            val merkmal = regel.nichtZweimalFuer?.let { alle[it]?.alsText() }
            if (merkmal != null && verlauf.schonGetan(modul.name, nr, merkmal)) {
                return@forEachIndexed
            }

            // Schon wieder so bald? Eine Quelle, die im Sekundentakt
            // auffrischt, soll nicht im Sekundentakt über Bluetooth gehen.
            val senke = regel.senke
            if (senke is Senke.AnDieUhr && senke.hoechstensAlleS > 0) {
                val her = System.currentTimeMillis() / 1000 -
                    verlauf.zuletztGesendet(modul.name, nr)
                if (her < senke.hoechstensAlleS) return@forEachIndexed
            }

            val ergebnis = try {
                fuehreAus(context, akte, modul, regel, alle)
            } catch (e: Exception) {
                Log.e(PebbleEmpfaenger.TAG, "Regel ${nr + 1} fehlgeschlagen", e)
                "Fehlgeschlagen: " + (e.message ?: e.javaClass.simpleName)
            }
            if (ergebnis == null) return@forEachIndexed
            meldungen.add(ergebnis)
            if (senke is Senke.AnDieUhr) verlauf.merkeGesendet(modul.name, nr)

            // Erst merken, wenn es geklappt hat. Wer den Riegel vorher setzte,
            // verloere die Messung endgueltig, sobald ein Versuch einmal
            // scheitert.
            if (merkmal != null && !ergebnis.contains("ehl") && !ergebnis.contains("Erlaubnis")) {
                verlauf.merkeGetan(modul.name, nr, merkmal)
            }
        }

        val text = if (meldungen.isEmpty()) modul.name + ": nichts zu tun"
        else meldungen.joinToString("; ")
        Log.i(PebbleEmpfaenger.TAG, text)

        // Nur ECHTE Ergebnisse in die Statusanzeige. Sonst ueberschreibt die
        // naechste abgewiesene Wiederholung das, was wirklich geschehen ist -
        // und im Kasten steht "nichts zu tun", Sekunden nachdem etwas getan
        // wurde. Bei einer Quelle, die im Sekundentakt dasselbe schickt, waere
        // der Zustand damit dauerhaft nichtssagend.
        if (meldungen.isNotEmpty()) verlauf.merkeMeldung(text)
        return text
    }

    /**
     * Eine einzelne Regel ausfuehren.
     *
     * Rueckgabe null heisst: die Regel greift doch nicht, weil ein Wert sich
     * nicht in die noetige Form bringen laesst. Das ist kein Fehler - eine
     * Benachrichtigung, in deren Titel diesmal keine Zahl steht, ist einfach
     * nicht gemeint.
     */
    private suspend fun fuehreAus(
        context: Context,
        akte: Akte,
        modul: Modul,
        regel: Regel,
        felder: Map<String, Wert>,
    ): String? = when (val senke = regel.senke) {

        is Senke.Akteneintrag -> {
            val zeitRoh = zahl(felder, senke.zeit)
            if (zeitRoh == null) null else {
                val beginn =
                    if (senke.zeitInMillisekunden) Instant.ofEpochMilli(zeitRoh)
                    else Instant.ofEpochSecond(zeitRoh)
                val wert = senke.wert?.let { zahl(felder, it) }
                if (senke.wert != null && wert == null) null
                else akte.schreibe(
                    art = senke.art,
                    dauerSekunden = senke.dauerSekunden,
                    wert = (wert ?: 0L).toDouble(),
                    beginn = beginn,
                    meldung = fuelle(regel.meldung, felder)
                        .ifEmpty { modul.name + ": " + senke.art.klartext + " eingetragen" },
                )
            }
        }

        is Senke.AnDieUhr -> {
            val belegt = mutableMapOf<Int, Wert>()
            var fehlt = false
            for (f in senke.felder) {
                val roh = felder[f.aus]
                if (roh == null) { fehlt = true; break }
                val w = if (f.alsText) Wert.Text(roh.alsText())
                else roh.alsZahl(f.muster, f.faktor)?.let { Wert.Zahl(it) }
                if (w == null) { fehlt = true; break }
                belegt[f.nummer] = w
            }
            if (fehlt) null else UhrSender.sende(context, senke.uuid, senke.starten, belegt)
        }

        is Senke.Meldung -> {
            zeige(context, fuelle(senke.titel, felder).ifEmpty { modul.name },
                fuelle(senke.text, felder))
            fuelle(regel.meldung, felder).ifEmpty { "gemeldet" }
        }
    }

    private fun zahl(felder: Map<String, Wert>, bezug: Bezug): Long? =
        felder[bezug.aus]?.alsZahl(bezug.muster, bezug.faktor)

    /** {FELD} in einem Text durch den Wert ersetzen. */
    private fun fuelle(vorlage: String, felder: Map<String, Wert>): String {
        if (vorlage.isEmpty()) return ""
        var text = vorlage
        for ((name, wert) in felder) {
            if (text.contains('{')) text = text.replace("{$name}", wert.alsText())
        }
        return text
    }

    /**
     * Die Senke "melden": eine Benachrichtigung auf dem Telefon.
     *
     * Die billigste der drei und trotzdem nuetzlich - sie ist der Weg, einen
     * Zettel zu erproben, ohne dass eine Uhr oder eine Gesundheitsakte im
     * Spiel ist. Was ankommt, sieht man sofort.
     */
    private fun zeige(context: Context, titel: String, text: String) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val kanal = NotificationChannel(
                KANAL, context.getString(R.string.kanal_meldung),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            mgr.createNotificationChannel(kanal)
        }
        val meldung = Notification.Builder(context, KANAL)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(titel)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        // Eine eigene Nummer je Text, damit sich aufeinanderfolgende Meldungen
        // nicht gegenseitig ueberschreiben, aber dieselbe sich nicht haeuft.
        mgr.notify(text.hashCode(), meldung)
    }

    private const val KANAL = "zettel"
}
