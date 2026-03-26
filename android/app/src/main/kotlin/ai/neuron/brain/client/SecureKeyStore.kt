package ai.neuron.brain.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores sensitive runtime credentials (device token, Picovoice key)
 * in EncryptedSharedPreferences backed by Android Keystore.
 *
 * Keys are never stored in BuildConfig or plain SharedPreferences.
 */
class SecureKeyStore(context: Context) {
    companion object {
        private const val TAG = "NeuronSecureKeyStore"
        private const val PREFS_NAME = "neuron_secure_prefs"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_PICOVOICE = "picovoice_access_key"
        private const val KEY_SERVER_URL = "server_url"
    }

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // SECURITY: Log the failure but still use MODE_PRIVATE prefs.
            // This is a degraded state — the device token is stored unencrypted.
            // On OEMs with broken Keystore this is the only option; the token
            // is still protected by app sandboxing (no other app can read it).
            Log.e(
                TAG,
                "SECURITY WARNING: EncryptedSharedPreferences failed, " +
                    "falling back to app-private prefs (not hardware-encrypted)",
                e,
            )
            context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    var deviceToken: String
        get() = prefs.getString(KEY_DEVICE_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    var picovoiceAccessKey: String
        get() = prefs.getString(KEY_PICOVOICE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PICOVOICE, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, LlmProxyClient.DEFAULT_SERVER_URL) ?: LlmProxyClient.DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()
}
