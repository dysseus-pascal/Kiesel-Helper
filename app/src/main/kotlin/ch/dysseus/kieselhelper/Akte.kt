package ch.dysseus.kieselhelper

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant

/**
 * Der Zugang zur Gesundheitsakte des Telefons.
 *
 * NUR NOCH DER ZUGANG. Frueher stand hier auch das Eintragen, gesteuert ueber
 * einen Katalog von 22 Satzarten - eine Auswahl fuer Beschreibungen, die nie
 * geschrieben wurden. Was eingetragen wird, steht jetzt in [Aufgaben], und
 * zwar ausgeschrieben: zwei Satzarten, beide sichtbar an der Stelle, an der
 * sie gebraucht werden.
 */
class Akte(private val context: Context) {

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

    fun bereit(): HealthConnectClient? {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectClient.getOrCreate(context)
            else -> null
        }
    }

    private fun nichtVerfuegbar(): String {
        return when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                context.getString(R.string.hc_update)
            else -> context.getString(R.string.hc_nicht_verfuegbar)
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
