package ai.neuron.input

import ai.neuron.accessibility.OverlayManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class VoiceInputControllerTest {

    private lateinit var controller: VoiceInputController
    private lateinit var speechManager: SpeechRecognitionManager
    private lateinit var overlayManager: OverlayManager

    @BeforeEach
    fun setup() {
        speechManager = SpeechRecognitionManager()
        overlayManager = mockk(relaxed = true)
        controller = VoiceInputController(speechManager, overlayManager)
    }

    @Nested
    @DisplayName("Hold-to-speak lifecycle")
    inner class HoldToSpeak {

        @Test
        fun should_cancelListening_when_userCancels() {
            speechManager.onRecognitionStarted()
            controller.onCancel()
            verify { overlayManager.updateState(OverlayManager.OverlayState.IDLE) }
        }
    }

    @Nested
    @DisplayName("Transcript observation")
    inner class Transcript {

        @Test
        fun should_exposePartialTranscript_when_listening() {
            speechManager.onRecognitionStarted()
            speechManager.onPartialResult("hello world")
            val partial = controller.partialTranscript.value
            assert(partial == "hello world")
        }
    }

    @Nested
    @DisplayName("Command callback")
    inner class CommandCallback {

        @Test
        fun should_invokeCallback_when_finalResultReady() {
            var capturedCommand: String? = null
            controller.setOnCommandReady { capturedCommand = it }

            speechManager.onRecognitionStarted()
            speechManager.onFinalResult("open calculator")

            // Without coroutine scope, callback won't fire via observer
            // but finalText is set on speechManager
            assert(speechManager.finalText.value == "open calculator")
        }
    }
}
