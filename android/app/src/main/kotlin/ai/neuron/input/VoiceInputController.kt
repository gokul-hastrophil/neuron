package ai.neuron.input

import ai.neuron.accessibility.OverlayManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges SpeechRecognitionManager with OverlayManager.
 * Hold-to-speak: press starts listening, release submits.
 * Shows transcript in overlay before submission.
 */
@Singleton
class VoiceInputController @Inject constructor(
    private val speechManager: SpeechRecognitionManager,
    private val overlayManager: OverlayManager,
) {

    companion object {
        private const val TAG = "NeuronVoiceInput"
    }

    private var stateObserverJob: Job? = null
    private var onCommandReady: ((String) -> Unit)? = null

    /**
     * Start observing speech state and updating overlay accordingly.
     * Call once when the accessibility service starts.
     */
    fun startObserving(scope: CoroutineScope) {
        stateObserverJob?.cancel()
        stateObserverJob = scope.launch(Dispatchers.Main) {
            speechManager.state.collectLatest { state ->
                when (state) {
                    SpeechRecognitionManager.State.LISTENING -> {
                        overlayManager.updateState(OverlayManager.OverlayState.LISTENING)
                    }
                    SpeechRecognitionManager.State.PROCESSING -> {
                        overlayManager.updateState(OverlayManager.OverlayState.THINKING)
                    }
                    SpeechRecognitionManager.State.ERROR -> {
                        overlayManager.updateState(OverlayManager.OverlayState.ERROR)
                    }
                    SpeechRecognitionManager.State.IDLE -> {
                        // Check if we have a final result to submit
                        val finalResult = speechManager.finalText.value
                        if (finalResult != null) {
                            Log.i(TAG, "Voice command ready: $finalResult")
                            onCommandReady?.invoke(finalResult)
                        }
                    }
                }
            }
        }
    }

    fun stopObserving() {
        stateObserverJob?.cancel()
        stateObserverJob = null
    }

    /**
     * Set callback for when a voice command is finalized.
     */
    fun setOnCommandReady(callback: (String) -> Unit) {
        onCommandReady = callback
    }

    /**
     * Called when user presses and holds the overlay bubble.
     * Starts speech recognition.
     */
    fun onHoldStart(context: Context) {
        Log.d(TAG, "Hold-to-speak: started")
        speechManager.startListening(context)
    }

    /**
     * Called when user releases the overlay bubble.
     * Stops listening and triggers processing of recorded audio.
     */
    fun onHoldRelease() {
        Log.d(TAG, "Hold-to-speak: released")
        speechManager.stopListening()
    }

    /**
     * Called when user cancels (swipes away during recording).
     */
    fun onCancel() {
        Log.d(TAG, "Hold-to-speak: cancelled")
        speechManager.cancel()
        overlayManager.updateState(OverlayManager.OverlayState.IDLE)
    }

    /**
     * Current partial transcript for display in overlay.
     */
    val partialTranscript get() = speechManager.partialText

    /**
     * Current audio RMS level for waveform visualization.
     */
    val audioLevel get() = speechManager.rmsDb
}
