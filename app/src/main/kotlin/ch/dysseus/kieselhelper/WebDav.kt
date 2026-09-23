package ch.dysseus.kieselhelper

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Der Draht zu einem WebDAV-Ordner - PUT, GET, MKCOL, mehr nicht.
 *
 * WEBDAV, WEIL ES ÜBERALL SCHON DA IST. Nextcloud, ownCloud, Synology, ein
 * Webspace mit mod_dav: kein Konto bei jemandem Neuen, kein Schlüssel, kein
 * Dienst, der in zwei Jahren eingestellt wird. Ein Ordner, in dem Dateien
 * liegen — und genau das braucht eine Sicherung.
 *
 * NUR HTTPS. Über eine unverschlüsselte Verbindung gingen Passwort und ein
 * Jahr Gesundheitsdaten im Klartext durchs Netz. Android verbietet Klartext
 * ohnehin seit Fassung 9; hier wird es zusätzlich gesagt, damit der Grund
 * dasteht und nicht nur eine Fehlermeldung.
 *
 * OKHTTP UND NICHT HttpURLConnection: die kennt MKCOL nicht. Ihre Liste
 * erlaubter Methoden ist fest eingebaut, und alles daneben endet in einer
 * ProtocolException. Man kann das mit Reflexion umgehen; man kann es auch
 * lassen.
 */
class WebDav(basis: String, private val nutzer: String, private val geheim: String) {

    /** Der Ordner, immer mit Schrägstrich am Ende. */
    val basis: String = if (basis.endsWith("/")) basis else "$basis/"

    sealed class Ergebnis {
        data class Gut(val text: String = "") : Ergebnis()
        data class Schlecht(val grund: String) : Ergebnis()
    }

    private val kunde = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun kopf(bauer: Request.Builder): Request.Builder {
        val schluessel = Base64.encodeToString(
            ("$nutzer:$geheim").toByteArray(Charsets.UTF_8), Base64.NO_WRAP
        )
        return bauer.header("Authorization", "Basic $schluessel")
            .header("User-Agent", "Kiesel-Helper")
    }

    private fun pfad(name: String) = basis + name.trimStart('/')

    /**
     * Steht der Ordner, und stimmen die Zugangsdaten? Fehlt er, wird er
     * angelegt.
     *
     * MIT PROPFIND, NICHT MIT HEAD. Der erste Entwurf fragte mit HEAD, und
     * das verstehen nicht alle Server als Frage nach einem Ordner: Open-
     * Xchange (mailbox.org) antwortet auf HEAD zu einem Ordner mit 404, als
     * gaebe es ihn nicht - und der Nutzer sucht einen Fehler in einer
     * Adresse, die stimmt. PROPFIND mit Tiefe 0 ist die Frage, die WebDAV
     * dafuer vorsieht.
     *
     * NUR 207 IST EIN JA. Eine Webseite antwortet auf PROPFIND mit 200 und
     * einer HTML-Seite - "https://app.mailbox.org" sah so aus wie ein
     * erreichbarer Ordner, und die Sicherung lief danach ins Leere. Ein
     * WebDAV-Server antwortet mit 207 Multi-Status, sonst ist es keiner.
     *
     * UND FEHLT ER WIRKLICH, WIRD ER ANGELEGT. Die Sicherung legte ihn beim
     * ersten Mal ohnehin an; die Pruefung soll nicht an etwas scheitern, das
     * die Sicherung selbst behebt. Geht auch das nicht, sagt die Antwort,
     * welche Ordner es eine Stufe hoeher gibt - bei mailbox.org heisst der
     * eigene "Vorname, Nachname", und das raet niemand.
     */
    fun pruefe(): Ergebnis {
        val e = propfind(basis, tiefe = 0)
        if (e is Ergebnis.Gut && e.text != "207") {
            return Ergebnis.Schlecht(
                "Unter dieser Adresse antwortet kein WebDAV-Ordner, sondern eine " +
                    "Webseite (${e.text} statt 207). Bei mailbox.org: " +
                    "https://dav.mailbox.org/servlet/webdav.infostore/Userstore/…"
            )
        }
        if (e is Ergebnis.Schlecht && e.grund.startsWith("Diesen Ordner gibt es nicht")) {
            val an = ordner()
            if (an is Ergebnis.Gut) {
                // Angelegt heisst erst, wenn er danach auch da ist: mancher
                // Server sagt 201 und meint nichts damit.
                val nochmal = propfind(basis, tiefe = 0)
                if (nochmal is Ergebnis.Gut && nochmal.text == "207") return Ergebnis.Gut("angelegt")
            }
            val grund = if (an is Ergebnis.Schlecht) an.grund else e.grund
            val nachbarn = nachbarn()
            return Ergebnis.Schlecht(
                grund + if (nachbarn.isNotEmpty()) {
                    " — eine Stufe höher gibt es: " + nachbarn.joinToString(", ") { "»$it«" }
                } else {
                    ""
                }
            )
        }
        return e
    }

    private fun propfind(ziel: String, tiefe: Int): Ergebnis =
        versuche("PROPFIND", erlaubtAuch = setOf(207), mitCode = true) {
            kopf(
                Request.Builder().url(ziel)
                    .method("PROPFIND", null)
                    .header("Depth", tiefe.toString())
            ).build()
        }

    /**
     * Die Ordner eine Stufe ueber dem gewaehlten - damit der Nutzer sieht,
     * wie der Server sie nennt, statt zu raten.
     */
    fun nachbarn(): List<String> {
        val ohne = basis.trimEnd('/')
        val schnitt = ohne.lastIndexOf('/')
        if (schnitt <= "https://".length) return emptyList()
        val eltern = ohne.substring(0, schnitt + 1)
        return try {
            kunde.newCall(
                kopf(
                    Request.Builder().url(eltern)
                        .method("PROPFIND", null)
                        .header("Depth", "1")
                ).build()
            ).execute().use { antwort ->
                if (antwort.code != 207) return emptyList()
                val xml = antwort.body?.string() ?: return emptyList()
                hrefs(xml)
                    .filter { it.endsWith("/") }
                    .map { it.trimEnd('/').substringAfterLast('/') }
                    .map { java.net.URLDecoder.decode(it, "UTF-8") }
                    .filter { it.isNotBlank() && eltern.trimEnd('/').substringAfterLast('/') != it }
                    .distinct()
                    .take(12)
            }
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Nachbarn: " + e.message)
            emptyList()
        }
    }

    companion object {
        /** Alle href-Werte einer PROPFIND-Antwort, gleich welches Namensraum-Kuerzel. */
        internal fun hrefs(xml: String): List<String> =
            Regex("<(?:[A-Za-z0-9_]+:)?href[^>]*>([^<]+)</")
                .findAll(xml)
                .map { it.groupValues[1].trim() }
                .toList()
    }

    /** Den Ordner anlegen. Gibt es ihn schon, ist das kein Fehler. */
    fun ordner(name: String = ""): Ergebnis {
        val ziel = if (name.isBlank()) basis else pfad(name)
        return versuche("MKCOL", erlaubtAuch = setOf(405, 301)) {
            kopf(Request.Builder().url(ziel).method("MKCOL", null)).build()
        }
    }

    fun lege(name: String, inhalt: ByteArray, typ: String = "application/json"): Ergebnis =
        versuche("PUT") {
            kopf(
                Request.Builder().url(pfad(name))
                    .put(inhalt.toRequestBody(typ.toMediaType()))
            ).build()
        }

    /** Holt eine Datei. null heisst: gibt es nicht oder ging nicht. */
    fun hole(name: String): String? {
        if (!istSicher()) return null
        return try {
            kunde.newCall(kopf(Request.Builder().url(pfad(name)).get()).build())
                .execute().use { antwort ->
                    if (!antwort.isSuccessful) {
                        Log.i(PebbleEmpfaenger.TAG, "WebDAV GET " + antwort.code)
                        null
                    } else {
                        antwort.body?.string()
                    }
                }
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "WebDAV GET: " + e.message)
            null
        }
    }

    private fun istSicher() = basis.startsWith("https://", ignoreCase = true)

    private fun versuche(
        was: String,
        erlaubtAuch: Set<Int> = emptySet(),
        mitCode: Boolean = false,
        baue: () -> Request,
    ): Ergebnis {
        if (!istSicher()) {
            return Ergebnis.Schlecht(
                "Die Adresse muss mit https:// anfangen — über eine " +
                    "unverschlüsselte Verbindung gingen Passwort und Daten im " +
                    "Klartext durchs Netz."
            )
        }
        return try {
            kunde.newCall(baue()).execute().use { antwort ->
                val gut = if (mitCode) Ergebnis.Gut(antwort.code.toString()) else Ergebnis.Gut()
                when {
                    antwort.isSuccessful -> gut
                    antwort.code in erlaubtAuch -> gut
                    // DIE HÄUFIGEN FEHLER BEIM NAMEN NENNEN. "Fehler 401"
                    // schickt niemanden zur Lösung, "Passwort stimmt nicht"
                    // schon.
                    antwort.code == 401 -> Ergebnis.Schlecht(
                        "Benutzername oder Passwort stimmt nicht (401)"
                    )
                    antwort.code == 403 -> Ergebnis.Schlecht(
                        "Zugriff verweigert (403) — darf dieses Konto dorthin schreiben?"
                    )
                    antwort.code == 404 -> Ergebnis.Schlecht(
                        "Diesen Ordner gibt es nicht (404)"
                    )
                    antwort.code == 409 -> Ergebnis.Schlecht(
                        "Der übergeordnete Ordner fehlt (409)"
                    )
                    antwort.code == 507 -> Ergebnis.Schlecht("Kein Platz mehr auf dem Server (507)")
                    else -> Ergebnis.Schlecht("$was: Fehler ${antwort.code}")
                }
            }
        } catch (e: javax.net.ssl.SSLException) {
            Ergebnis.Schlecht("Die verschlüsselte Verbindung kam nicht zustande: " + e.message)
        } catch (e: java.net.UnknownHostException) {
            Ergebnis.Schlecht("Diese Adresse ist nicht erreichbar")
        } catch (e: Exception) {
            Ergebnis.Schlecht(e.message ?: e.javaClass.simpleName)
        }
    }
}
