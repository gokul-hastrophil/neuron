package ai.neuron.input

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages speech recognition state and wraps Android SpeechRecognizer.
 *
 * State machine: IDLE → LISTENING → PROCESSING → IDLE
 *                          ↓           ↓
 *                        ERROR       ERROR
 */
@Singleton
class SpeechRecognitionManager @Inject constructor() {

    enum class State {
        IDLE,
        LISTENING,
        PROCESSING,
        ERROR,
    }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText

    private val _finalText = MutableStateFlow<String?>(null)
    val finalText: StateFlow<String?> = _finalText

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb

    val isListening: Boolean get() = _state.value == State.LISTENING

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Start listening for speech. Requires RECORD_AUDIO permission.
     * Returns false if SpeechRecognizer is not available on this device.
     */
    fun startListening(context: Context): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            _errorMessage.value = "Speech recognition not available"
            _state.value = State.ERROR
            return false
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onRecognitionStarted()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                _rmsDb.value = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onProcessing()
            }

            override fun onError(error: Int) {
                val message = mapErrorCode(error)
                onError(message)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestMatch = matches?.firstOrNull()
                if (bestMatch != null) {
                    onFinalResult(bestMatch)
                } else {
                    onError("No speech recognized")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { onPartialResult(it) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.startListening(intent)
        return true
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        onRecognitionStopped()
    }

    fun cancel() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        onRecognitionStopped()
    }

    // --- State transition methods (internal for testing) ---

    internal fun onRecognitionStarted() {
        _state.value = State.LISTENING
        _errorMessage.value = null
        _partialText.value = ""
    }

    internal fun onRecognitionStopped() {
        _state.value = State.IDLE
        _partialText.value = ""
        _rmsDb.value = 0f
    }

    internal fun onProcessing() {
        _state.value = State.PROCESSING
    }

    internal fun onPartialResult(text: String) {
        _partialText.value = text
    }

    internal fun onFinalResult(text: String) {
        _finalText.value = text
        _partialText.value = ""
        _state.value = State.IDLE
    }

    internal fun onError(message: String) {
        _errorMessage.value = message
        _state.value = State.ERROR
    }

    private fun mapErrorCode(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Unknown error ($error)"
    }

    companion object {
        private const val TAG = "NeuronSpeech"
    }
}
