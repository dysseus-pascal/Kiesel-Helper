package ch.dysseus.kieselhelper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Haelt die geladenen Beschreibungen.
 *
 * NICHTS WIRD VON SELBST GEHOLT. Aktualisiert wird nur, wenn jemand den Knopf
 * drueckt. Eine stille Aenderung an dem, was in eine Gesundheitsakte schreibt,
 * waere genau das, was man nicht will — und ein Netzzugriff im Hintergrund
 * waere ausserdem ein Fernsteuerungskanal, den niemand bestellt hat.
 *
 * Abgelegt wird der ROHE TEXT, nicht das eingelesene Modul. So laesst sich
 * spaeter nachsehen, was tatsaechlich ankam, und ein Einleseschritt mehr kostet
 * beim Start nichts Messbares.
 */
class ModulSpeicher(context: Context) {

    private val prefs = context.getSharedPreferences("kiesel-module", Context.MODE_PRIVATE)

    data class Eintrag(
        val quelle: String,
        val text: String,
        val geholtAm: Long,
    )

    fun alleEintraege(): List<Eintrag> {
        val roh = prefs.getString(SCHLUESSEL, "[]") ?: "[]"
        val aus = mutableListOf<Eintrag>()
        try {
            val a = JSONArray(roh)
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                aus.add(
                    Eintrag(
                        quelle = o.optString("quelle", ""),
                        text = o.optString("text", ""),
                        geholtAm = o.optLong("geholt", 0L),
                    )
                )
            }
        } catch (e: Exception) {
            // Ein verdorbener Speicher darf die App nicht am Starten hindern.
            return emptyList()
        }
        return aus
    }

    /** Die eingelesenen Module — fehlerhafte fallen still heraus. */
    fun alle(): List<Modul> = alleEintraege().mapNotNull { Modul.lies(it.text).modul }

    /** Das Modul zu einer UUID, oder null. */
    fun fuer(uuid: java.util.UUID): Modul? = alle().firstOrNull { it.uuid == uuid }

    fun lege(quelle: String, text: String) {
        val bleibt = alleEintraege().filter { it.quelle != quelle }
        val neu = bleibt + Eintrag(quelle, text, System.currentTimeMillis() / 1000)
        schreibe(neu)
    }

    fun entferne(quelle: String) {
        schreibe(alleEintraege().filter { it.quelle != quelle })
    }

    private fun schreibe(liste: List<Eintrag>) {
        val a = JSONArray()
        for (e in liste) {
            a.put(
                JSONObject()
                    .put("quelle", e.quelle)
                    .put("text", e.text)
                    .put("geholt", e.geholtAm)
            )
        }
        prefs.edit().putString(SCHLUESSEL, a.toString()).apply()
    }

    companion object {
        private const val SCHLUESSEL = "module"

        /**
         * Aus einer GitHub-Adresse die Adresse der Datei machen.
         *
         * Damit man die Adresse einsetzen kann, die im Browser oben steht,
         * statt den raw-Pfad von Hand zusammenzusetzen. Wer schon eine fertige
         * Adresse hat, gibt sie unveraendert ein.
         */
        fun zuDateiAdresse(eingabe: String): String {
            val t = eingabe.trim().removeSuffix("/")
            if (t.endsWith(".json")) return t
            val m = Regex("^https://github\\.com/([^/]+)/([^/]+)$").find(t)
            if (m != null) {
                val (besitzer, repo) = m.destructured
                return "https://raw.githubusercontent.com/$besitzer/$repo/main/kiesel.json"
            }
            return t
        }

        /**
         * Die Datei holen.
         *
         * Laeuft auf dem Netz-Strang, nie auf dem der Oberflaeche. Rueckgabe
         * ist entweder der Text oder eine Meldung, warum nicht.
         */
        suspend fun hole(adresse: String): Result<String> = withContext(Dispatchers.IO) {
            try {
                val verbindung = URL(adresse).openConnection() as HttpURLConnection
                verbindung.connectTimeout = 15000
                verbindung.readTimeout = 15000
                verbindung.requestMethod = "GET"
                try {
                    val code = verbindung.responseCode
                    if (code != 200) {
                        return@withContext Result.failure(
                            Exception("Der Server antwortet mit $code")
                        )
                    }
                    val text = verbindung.inputStream.bufferedReader().use { it.readText() }
                    Result.success(text)
                } finally {
                    verbindung.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(Exception(e.message ?: "Netzwerkfehler"))
            }
        }
    }
}
