package ai.neuron.character.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State of the voice synthesizer.
 */
enum class SpeakingState {
    IDLE,
    SPEAKING,
}

/**
 * Manages TTS speaking state and utterance tracking.
 * This is a pure state machine — the actual Android TextToSpeech calls
 * live in [EmotionalTTSEngine] which delegates state tracking here.
 */
class VoiceSynthesizer {
    private val _speakingState = MutableStateFlow(SpeakingState.IDLE)
    val speakingState: StateFlow<SpeakingState> = _speakingState.asStateFlow()

    /** The utterance ID currently being spoken, or null if idle. */
    var currentUtteranceId: String? = null
        private set

    /** Called when TTS begins speaking an utterance. */
    fun onSpeakStart(utteranceId: String) {
        currentUtteranceId = utteranceId
        _speakingState.value = SpeakingState.SPEAKING
    }

    /** Called when TTS finishes speaking an utterance. */
    fun onSpeakDone(utteranceId: String) {
        if (currentUtteranceId == utteranceId) {
            currentUtteranceId = null
            _speakingState.value = SpeakingState.IDLE
        }
    }

    /** Force stop — returns to idle immediately. */
    fun stop() {
        currentUtteranceId = null
        _speakingState.value = SpeakingState.IDLE
    }
}
