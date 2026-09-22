package ch.dysseus.kieselhelper

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Das Passwort für den Sicherungsordner - verschlüsselt abgelegt.
 *
 * EIN PASSWORT IN EINER EINSTELLUNGSDATEI IST KEIN PASSWORT. Die Datei liegt
 * im App-Ordner, lesbar für alles, was Zugriff auf diesen Ordner bekommt: ein
 * Sicherungsprogramm, ein gerootetes Telefon, ein Auslesewerkzeug. Es kostet
 * fünfzig Zeilen, das richtig zu machen.
 *
 * DER SCHLÜSSEL VERLÄSST DAS TELEFON NIE. Er liegt im Android-Schlüsselbund,
 * in Hardware, wo sie da ist; herausholen kann man ihn nicht, nur benutzen.
 * Wer das Telefon zurücksetzt, verliert ihn — und damit das Passwort, nicht
 * aber die Sicherung: die liegt auf dem Server und wartet auf ein neu
 * eingetipptes Passwort.
 *
 * KEINE FREMDE BIBLIOTHEK. EncryptedSharedPreferences täte dasselbe und ist
 * seit Jahren in einer Alpha-Fassung; AES-GCM aus dem Schlüsselbund ist seit
 * Android 6 stabil und steht hier vollständig da.
 */
object Tresor {

    private const val SCHLUESSEL = "kiesel-sicherung"
    private const val SPEICHER = "AndroidKeyStore"
    private const val VERFAHREN = "AES/GCM/NoPadding"
    private const val DATEI = "kiesel-tresor"

    private fun schluessel(): SecretKey? = try {
        val laden = KeyStore.getInstance(SPEICHER).apply { load(null) }
        (laden.getEntry(SCHLUESSEL, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, SPEICHER).run {
                init(
                    KeyGenParameterSpec.Builder(
                        SCHLUESSEL,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
    } catch (e: Exception) {
        Log.w(PebbleEmpfaenger.TAG, "Schlüsselbund: " + e.message)
        null
    }

    fun merke(context: Context, geheim: String) {
        val k = schluessel() ?: return
        try {
            val c = Cipher.getInstance(VERFAHREN).apply { init(Cipher.ENCRYPT_MODE, k) }
            val verschlossen = c.doFinal(geheim.toByteArray(Charsets.UTF_8))
            // DER ZUFALLSWERT GEHOERT DAZU und ist kein Geheimnis: ohne ihn
            // laesst sich nichts mehr aufschliessen. Er steht vorn, durch
            // einen Punkt getrennt.
            val text = Base64.encodeToString(c.iv, Base64.NO_WRAP) + "." +
                Base64.encodeToString(verschlossen, Base64.NO_WRAP)
            context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
                .edit().putString("geheim", text).apply()
        } catch (e: Exception) {
            Log.w(PebbleEmpfaenger.TAG, "Verschliessen: " + e.message)
        }
    }

    fun lies(context: Context): String {
        val text = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getString("geheim", null) ?: return ""
        val k = schluessel() ?: return ""
        return try {
            val teile = text.split(".")
            if (teile.size != 2) return ""
            val iv = Base64.decode(teile[0], Base64.NO_WRAP)
            val inhalt = Base64.decode(teile[1], Base64.NO_WRAP)
            val c = Cipher.getInstance(VERFAHREN).apply {
                init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
            }
            String(c.doFinal(inhalt), Charsets.UTF_8)
        } catch (e: Exception) {
            // Nach einem Zuruecksetzen ist der Schluessel weg. Das ist kein
            // Fehler, sondern der Zeitpunkt, an dem das Passwort neu
            // eingetippt wird.
            Log.i(PebbleEmpfaenger.TAG, "Aufschliessen ging nicht: " + e.message)
            ""
        }
    }

    fun vergiss(context: Context) {
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun hatGeheimnis(context: Context): Boolean =
        !context.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
            .getString("geheim", null).isNullOrBlank()
}
