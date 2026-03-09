package ai.neuron.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SpeechRecognitionServiceTest {

    private lateinit var service: SpeechRecognitionManager

    @BeforeEach
    fun setup() {
        service = SpeechRecognitionManager()
    }

    @Nested
    @DisplayName("State management")
    inner class StateManagement {

        @Test
        fun should_beIdle_when_created() {
            assertEquals(SpeechRecognitionManager.State.IDLE, service.state.value)
        }

        @Test
        fun should_trackListeningState_when_started() {
            service.onRecognitionStarted()
            assertEquals(SpeechRecognitionManager.State.LISTENING, service.state.value)
        }

        @Test
        fun should_returnToIdle_when_stopped() {
            service.onRecognitionStarted()
            service.onRecognitionStopped()
            assertEquals(SpeechRecognitionManager.State.IDLE, service.state.value)
        }

        @Test
        fun should_trackProcessingState_when_processingAudio() {
            service.onRecognitionStarted()
            service.onProcessing()
            assertEquals(SpeechRecognitionManager.State.PROCESSING, service.state.value)
        }

        @Test
        fun should_trackErrorState_when_errorOccurs() {
            service.onRecognitionStarted()
            service.onError("No match")
            assertEquals(SpeechRecognitionManager.State.ERROR, service.state.value)
        }
    }

    @Nested
    @DisplayName("Partial results")
    inner class PartialResults {

        @Test
        fun should_storePartialResults_when_receivedDuringListening() {
            service.onRecognitionStarted()
            service.onPartialResult("hello")
            assertEquals("hello", service.partialText.value)
        }

        @Test
        fun should_updatePartialResults_when_newPartialReceived() {
            service.onRecognitionStarted()
            service.onPartialResult("hello")
            service.onPartialResult("hello world")
            assertEquals("hello world", service.partialText.value)
        }

        @Test
        fun should_clearPartialResults_when_stopped() {
            service.onRecognitionStarted()
            service.onPartialResult("some text")
            service.onRecognitionStopped()
            assertEquals("", service.partialText.value)
        }
    }

    @Nested
    @DisplayName("Final results")
    inner class FinalResults {

        @Test
        fun should_storeFinalResult_when_recognitionComplete() {
            service.onRecognitionStarted()
            service.onPartialResult("open calc")
            service.onFinalResult("open calculator")
            assertEquals("open calculator", service.finalText.value)
        }

        @Test
        fun should_transitionToIdle_when_finalResultReceived() {
            service.onRecognitionStarted()
            service.onFinalResult("open calculator")
            assertEquals(SpeechRecognitionManager.State.IDLE, service.state.value)
        }

        @Test
        fun should_clearPartial_when_finalResultReceived() {
            service.onRecognitionStarted()
            service.onPartialResult("open calc")
            service.onFinalResult("open calculator")
            assertEquals("", service.partialText.value)
        }

        @Test
        fun should_haveNoFinalResult_when_freshlyCreated() {
            assertNull(service.finalText.value)
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {

        @Test
        fun should_storeErrorMessage_when_errorOccurs() {
            service.onRecognitionStarted()
            service.onError("No speech detected")
            assertEquals("No speech detected", service.errorMessage.value)
        }

        @Test
        fun should_clearError_when_newRecognitionStarts() {
            service.onError("Previous error")
            service.onRecognitionStarted()
            assertNull(service.errorMessage.value)
        }
    }

    @Nested
    @DisplayName("Availability")
    inner class Availability {

        @Test
        fun should_notBeListening_when_idle() {
            assertFalse(service.isListening)
        }

        @Test
        fun should_beListening_when_started() {
            service.onRecognitionStarted()
            assertTrue(service.isListening)
        }

        @Test
        fun should_notBeListening_when_processing() {
            service.onRecognitionStarted()
            service.onProcessing()
            assertFalse(service.isListening)
        }
    }
}
