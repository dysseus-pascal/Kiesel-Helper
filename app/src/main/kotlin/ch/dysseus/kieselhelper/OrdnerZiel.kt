package ch.dysseus.kieselhelper

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * Der Ordner auf dem Telefon, in den gesichert wird - gewaehlt ueber den
 * Ordnerdialog des Systems (Storage Access Framework).
 *
 * KEIN EIGENER CLOUD-CODE, UND KEIN WEBDAV MEHR. Es gab eine WebDAV-Anbindung;
 * sie scheiterte an mailbox.org und an Icedrive, jeder Server hat seine
 * Eigenheiten, und eine Sicherung, die an ihnen scheitert, ist keine. Wer den
 * Ordner in einer Sync-App waehlt - DAVx5, Nextcloud, mailbox.org Drive,
 * Syncthing -, bekommt die Sicherung in die Cloud: die App des Anbieters
 * traegt sie hinauf, mit ihrer Anmeldung und ihrer Fehlerbehandlung. Das ist
 * der Weg, der mit jedem Anbieter geht - und diese App muss dafuer nicht
 * einmal ins Netz.
 *
 * DIE ERLAUBNIS IST DAUERHAFT (takePersistableUriPermission) und ueberlebt
 * den Neustart; sie faellt weg, wenn die Cloud-App deinstalliert oder der
 * Ordner geloescht wird. Dann sagt pruefe() das, und der Ordner wird neu
 * gewaehlt.
 */
class OrdnerZiel(private val context: Context, private val baum: Uri) {

    sealed class Ergebnis {
        object Gut : Ergebnis()
        data class Schlecht(val grund: String) : Ergebnis()
    }

    val name: String
        get() = DocumentFile.fromTreeUri(context, baum)?.name ?: baum.lastPathSegment ?: "Ordner"

    private fun wurzel(): DocumentFile? =
        DocumentFile.fromTreeUri(context, baum)?.takeIf { it.exists() && it.isDirectory }

    /** Erreichbar und beschreibbar? */
    fun pruefe(): Ergebnis {
        val w = wurzel() ?: return Ergebnis.Schlecht(
            "Der Ordner auf dem Telefon ist nicht mehr erreichbar — bitte neu wählen"
        )
        if (!w.canWrite()) return Ergebnis.Schlecht("In diesen Ordner darf die App nicht schreiben")
        return Ergebnis.Gut
    }

    /** Einen Unterordner anlegen; leer heisst: den Zielordner selbst. */
    fun ordner(name: String = ""): Ergebnis {
        val w = wurzel() ?: return pruefe()
        if (name.isBlank()) return Ergebnis.Gut
        val da = w.findFile(name)
        if (da != null && da.isDirectory) return Ergebnis.Gut
        return if (w.createDirectory(name) != null) Ergebnis.Gut
        else Ergebnis.Schlecht("Unterordner »$name« liess sich nicht anlegen")
    }

    /** "spuren/spur-1.jsonl" -> der Unterordner und der Dateiname darin. */
    private fun zerlege(pfad: String): Pair<DocumentFile, String>? {
        var d = wurzel() ?: return null
        val teile = pfad.trim('/').split('/')
        for (t in teile.dropLast(1)) {
            d = d.findFile(t)?.takeIf { it.isDirectory } ?: d.createDirectory(t) ?: return null
        }
        return d to teile.last()
    }

    fun lege(name: String, inhalt: ByteArray, typ: String = "application/json"): Ergebnis {
        val (d, datei) = zerlege(name) ?: return pruefe()
        return try {
            // ERSETZEN, NICHT DANEBENLEGEN. Ein zweites createFile mit demselben
            // Namen gaebe "kiesel-helper (1).json" - und die Sicherung von
            // gestern bliebe die, die man beim Zurueckholen findet.
            val ziel = d.findFile(datei)?.takeIf { it.isFile } ?: d.createFile(typ, datei)
                ?: return Ergebnis.Schlecht("»$datei« liess sich nicht anlegen")
            context.contentResolver.openOutputStream(ziel.uri, "wt")?.use { it.write(inhalt) }
                ?: return Ergebnis.Schlecht("»$datei« liess sich nicht schreiben")
            Ergebnis.Gut
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Ordner schreiben: " + e.message)
            Ergebnis.Schlecht("Schreiben fehlgeschlagen: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    /** Eine Datei als Text; null heisst: gibt es nicht oder ging nicht. */
    fun hole(name: String): String? {
        val (d, datei) = zerlege(name) ?: return null
        val quelle = d.findFile(datei)?.takeIf { it.isFile } ?: return null
        return try {
            context.contentResolver.openInputStream(quelle.uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Ordner lesen: " + e.message)
            null
        }
    }

    companion object {
        /** Gilt die Erlaubnis fuer diesen Ordner noch? */
        fun erlaubt(context: Context, baum: Uri): Boolean =
            context.contentResolver.persistedUriPermissions.any {
                it.uri == baum && it.isWritePermission
            }
    }
}
