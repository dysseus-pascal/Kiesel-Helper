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
     * Steht der Ordner, und stimmen die Zugangsdaten?
     *
     * MIT EINEM HEAD AUF DEN ORDNER. Ein PROPFIND wäre die richtige Frage,
     * aber die Antwort wäre XML und die Frage hier ist einfacher: kommt 401,
     * stimmt das Passwort nicht; kommt 404, gibt es den Ordner nicht; kommt
     * irgendetwas Zweihundertartiges, ist alles gut.
     */
    fun pruefe(): Ergebnis = versuche("HEAD") {
        kopf(Request.Builder().url(basis).head()).build()
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
                when {
                    antwort.isSuccessful -> Ergebnis.Gut()
                    antwort.code in erlaubtAuch -> Ergebnis.Gut()
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
