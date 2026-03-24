package ai.neuron.input

import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.content.Context
import android.util.Log

/**
 * Wraps Porcupine wake word detection.
 * Listens continuously for a keyword (default: JARVIS) and invokes
 * [onWakeWordDetected] when the user says it.
 *
 * Lifecycle: call [start] to begin listening, [stop] to release resources.
 * Requires RECORD_AUDIO permission and a valid Picovoice access key.
 */
class WakeWordService(
    private val context: Context,
    private val accessKey: String,
    private val keyword: Porcupine.BuiltInKeyword = Porcupine.BuiltInKeyword.JARVIS,
) {
    companion object {
        private const val TAG = "NeuronWakeWord"

        /** All built-in keywords available for selection. */
        val AVAILABLE_KEYWORDS = Porcupine.BuiltInKeyword.entries.map { it.name }
    }

    var onWakeWordDetected: (() -> Unit)? = null

    private var porcupineManager: PorcupineManager? = null
    private var isRunning = false

    val running: Boolean get() = isRunning

    /**
     * Start continuous wake word listening.
     * Returns true if started successfully, false on error (missing key, permission, etc.).
     */
    fun start(): Boolean {
        if (isRunning) {
            Log.w(TAG, "Already running")
            return true
        }

        if (accessKey.isBlank()) {
            Log.e(TAG, "Picovoice access key is empty — cannot start wake word detection")
            return false
        }

        return try {
            val callback =
                PorcupineManagerCallback { keywordIndex ->
                    Log.i(TAG, "Wake word detected! keyword=$keyword index=$keywordIndex")
                    onWakeWordDetected?.invoke()
                }

            porcupineManager =
                PorcupineManager.Builder()
                    .setAccessKey(accessKey)
                    .setKeyword(keyword)
                    .setSensitivity(0.7f)
                    .build(context, callback)

            porcupineManager?.start()
            isRunning = true
            Log.i(TAG, "Wake word listening started (keyword=$keyword)")
            true
        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to start Porcupine: ${e.message}", e)
            isRunning = false
            false
        }
    }

    /**
     * Stop wake word listening and release resources.
     */
    fun stop() {
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: PorcupineException) {
            Log.w(TAG, "Error stopping Porcupine: ${e.message}", e)
        }
        porcupineManager = null
        isRunning = false
        Log.i(TAG, "Wake word listening stopped")
    }
}
