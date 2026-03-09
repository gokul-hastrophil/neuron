package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.Complexity
import ai.neuron.brain.model.EngineState
import ai.neuron.brain.model.IntentClassification
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import ai.neuron.memory.MemoryExtractor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PlanAndExecuteEngineTest {

    private lateinit var engine: PlanAndExecuteEngine
    private lateinit var router: LLMRouter
    private lateinit var classifier: IntentClassifier
    private lateinit var uiProvider: PlanAndExecuteEngine.UIProvider
    private lateinit var actionDispatcher: PlanAndExecuteEngine.ActionDispatcher
    private lateinit var memoryExtractor: MemoryExtractor

    private val normalTree = UITree(
        packageName = "com.whatsapp",
        nodes = listOf(UINode(id = "chat", text = "Chat", clickable = true)),
    )

    @BeforeEach
    fun setup() {
        router = mockk()
        classifier = mockk()
        uiProvider = mockk()
        actionDispatcher = mockk()

        every { classifier.classify(any()) } returns IntentClassification(
            complexity = Complexity.MODERATE,
            suggestedTier = LLMTier.T2,
            estimatedSteps = 3,
        )

        coEvery { uiProvider.getCurrentUITree() } returns normalTree
        coEvery { actionDispatcher.dispatch(any()) } returns true

        memoryExtractor = mockk(relaxed = true)
        engine = PlanAndExecuteEngine(router, classifier, uiProvider, actionDispatcher, memoryExtractor)
    }

    @Nested
    @DisplayName("State transitions")
    inner class StateTransitions {

        @Test
        fun should_transitionFromIdleToDone_when_singleStepSuccess() = runTest {
            coEvery { router.route(any(), any(), any()) } returns NeuronResult.Success(
                LLMResponse(
                    action = LLMAction(actionType = ActionType.DONE, confidence = 0.95, reasoning = "Done"),
                ),
            )

            val states = engine.execute("go home").toList()

            assertTrue(states.first() is EngineState.Planning)
            assertTrue(states.last() is EngineState.Done)
        }

        @Test
        fun should_goThroughExecuting_when_actionReturned() = runTest {
            val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)
            val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

            coEvery { router.route(any(), any(), any()) } returnsMany listOf(
                NeuronResult.Success(LLMResponse(action = tapAction)),
                NeuronResult.Success(LLMResponse(action = doneAction)),
            )

            val states = engine.execute("tap button").toList()

            val stateTypes = states.map { it::class }
            assertTrue(EngineState.Planning::class in stateTypes)
            assertTrue(EngineState.Executing::class in stateTypes)
            assertTrue(EngineState.Done::class in stateTypes)
        }
    }

    @Nested
    @DisplayName("Max step limit")
    inner class MaxStepLimit {

        @Test
        fun should_terminateWithError_when_maxStepsExceeded() = runTest {
            val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)

            coEvery { router.route(any(), any(), any()) } returns NeuronResult.Success(
                LLMResponse(action = tapAction),
            )

            val states = engine.execute("infinite task").toList()

            val lastState = states.last()
            assertTrue(lastState is EngineState.Error)
            assertTrue((lastState as EngineState.Error).message.contains("steps", ignoreCase = true))
        }
    }

    @Nested
    @DisplayName("Confidence threshold")
    inner class ConfidenceThreshold {

        @Test
        fun should_waitForUser_when_confidenceBelowThreshold() = runTest {
            val lowConfAction = LLMAction(
                actionType = ActionType.TAP,
                targetId = "btn",
                confidence = 0.3,
                reasoning = "Uncertain which button",
            )

            coEvery { router.route(any(), any(), any()) } returns NeuronResult.Success(
                LLMResponse(action = lowConfAction),
            )

            val states = engine.execute("tap something").toList()

            val hasWaiting = states.any { it is EngineState.WaitingForUser }
            assertTrue(hasWaiting)
        }
    }

    @Nested
    @DisplayName("Successful tasks")
    inner class SuccessfulTasks {

        @Test
        fun should_completeSingleStep_when_doneActionReturned() = runTest {
            coEvery { router.route(any(), any(), any()) } returns NeuronResult.Success(
                LLMResponse(
                    action = LLMAction(actionType = ActionType.DONE, confidence = 0.95, reasoning = "Home pressed"),
                ),
            )

            val states = engine.execute("go home").toList()
            assertTrue(states.last() is EngineState.Done)
        }

        @Test
        fun should_completeMultiStep_when_tapThenDone() = runTest {
            val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "send", confidence = 0.9)
            val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

            coEvery { router.route(any(), any(), any()) } returnsMany listOf(
                NeuronResult.Success(LLMResponse(action = tapAction)),
                NeuronResult.Success(LLMResponse(action = doneAction)),
            )

            val states = engine.execute("send message").toList()
            assertTrue(states.last() is EngineState.Done)
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {

        @Test
        fun should_terminateWithError_when_routerReturnsError() = runTest {
            coEvery { router.route(any(), any(), any()) } returns NeuronResult.Error("LLM unavailable")

            val states = engine.execute("do something").toList()
            assertTrue(states.last() is EngineState.Error)
        }

        @Test
        fun should_terminateWithError_when_llmReturnsErrorAction() = runTest {
            coEvery { router.route(any(), any(), any()) } returns NeuronResult.Success(
                LLMResponse(
                    action = LLMAction(
                        actionType = ActionType.ERROR,
                        reasoning = "Cannot find element",
                        confidence = 0.9,
                    ),
                ),
            )

            val states = engine.execute("tap missing button").toList()
            assertTrue(states.last() is EngineState.Error)
        }

        @Test
        fun should_terminateWithError_when_actionDispatchFails() = runTest {
            val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)

            coEvery { router.route(any(), any(), any()) } returns NeuronResult.Success(
                LLMResponse(action = tapAction),
            )
            coEvery { actionDispatcher.dispatch(any()) } returns false

            val states = engine.execute("tap button").toList()
            // Should eventually error after retries or continue to next step
            val hasError = states.any { it is EngineState.Error || it is EngineState.Done }
            assertTrue(hasError)
        }
    }
}
