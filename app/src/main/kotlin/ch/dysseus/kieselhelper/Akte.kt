package ch.dysseus.kieselhelper

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant

/**
 * Traegt in die Gesundheitsakte des Telefons ein.
 *
 * Kennt keine einzelne Uhr-App mehr — nur noch [Satzart]en. Was davon wann
 * geschrieben wird, sagt die Beschreibung; dieser Teil fuehrt es nur aus.
 */
class Akte(private val context: Context) {

    /**
     * Einen Satz eintragen.
     *
     * Rueckgabe ist ein Satz fuer die Statusanzeige, kein Fehlercode: die App
     * besteht im Kern aus einem Empfaenger, und ohne diese Zeile saehe man ihr
     * von aussen nie an, ob sie ueberhaupt etwas tut.
     */
    suspend fun schreibe(regel: Regel, wert: Double, beginn: Instant, meldung: String): String {
        val klient = bereit() ?: return nichtVerfuegbar()

        val noetig = regel.art.berechtigung
        if (!erteilt(klient, noetig)) {
            return "Erlaubnis fuer ${regel.art.klartext} fehlt — App oeffnen und erteilen"
        }

        val satz = regel.art.baue(wert, beginn, regel.dauerSekunden, uhrenHerkunft())
        klient.insertRecords(listOf(satz))
        return meldung
    }

    /**
     * Welche der noetigen Berechtigungen fehlen?
     *
     * Wird beim LADEN einer Beschreibung gefragt, nicht erst beim Schreiben.
     * Sonst faellt eine fehlende Erlaubnis erst auf, wenn nachts um fuenf eine
     * Messung ankommt und still verschwindet.
     */
    suspend fun fehlendeBerechtigungen(noetig: Set<String>): Set<String> {
        val klient = bereit() ?: return noetig
        val erteilt = klient.permissionController.getGrantedPermissions()
        return noetig - erteilt
    }

    private fun bereit(): HealthConnectClient? {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectClient.getOrCreate(context)
            else -> null
        }
    }

    private fun nichtVerfuegbar(): String {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                "Health Connect muss aktualisiert werden"
            else -> "Health Connect ist auf diesem Geraet nicht verfuegbar"
        }
    }

    private suspend fun erteilt(klient: HealthConnectClient, berechtigung: String): Boolean {
        return klient.permissionController.getGrantedPermissions().contains(berechtigung)
    }

    /**
     * Die Herkunft, die an jedem Satz haengt.
     *
     * Bewusst die UHR und nicht das Telefon: gemessen hat die Uhr, das Telefon
     * hat nur weitergereicht. Wer spaeter in der Akte sieht, woher ein Wert
     * kommt, soll das Richtige lesen.
     */
    private fun uhrenHerkunft(): Metadata = Metadata.autoRecorded(
        device = Device(
            manufacturer = "Core Devices",
            model = "Pebble Time 2",
            type = Device.TYPE_WATCH,
        )
    )
}
