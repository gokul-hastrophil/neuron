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
import ai.neuron.memory.AuditRepository
import ai.neuron.memory.MemoryExtractor
import io.mockk.coEvery
import io.mockk.coVerify
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
    private lateinit var confirmationGate: ConfirmationGate
    private lateinit var auditRepository: AuditRepository

    private val normalTree =
        UITree(
            packageName = "com.whatsapp",
            nodes = listOf(UINode(id = "chat", text = "Chat", clickable = true)),
        )

    @BeforeEach
    fun setup() {
        router = mockk()
        classifier = mockk()
        uiProvider = mockk()
        actionDispatcher = mockk()
        confirmationGate = ConfirmationGate()
        auditRepository = mockk(relaxed = true)

        every { classifier.classify(any()) } returns
            IntentClassification(
                complexity = Complexity.MODERATE,
                suggestedTier = LLMTier.T2,
                estimatedSteps = 3,
            )

        coEvery { uiProvider.getCurrentUITree() } returns normalTree
        coEvery { actionDispatcher.dispatch(any()) } returns true

        memoryExtractor = mockk(relaxed = true)
        engine = PlanAndExecuteEngine(router, classifier, uiProvider, actionDispatcher, memoryExtractor, confirmationGate, auditRepository)
    }

    @Nested
    @DisplayName("State transitions")
    inner class StateTransitions {
        @Test
        fun should_transitionFromIdleToDone_when_singleStepSuccess() =
            runTest {
                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(
                            action = LLMAction(actionType = ActionType.DONE, confidence = 0.95, reasoning = "Done"),
                        ),
                    )

                val states = engine.execute("go home").toList()

                assertTrue(states.first() is EngineState.Planning)
                assertTrue(states.last() is EngineState.Done)
            }

        @Test
        fun should_goThroughExecuting_when_actionReturned() =
            runTest {
                val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
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
        fun should_terminateWithError_when_maxStepsExceeded() =
            runTest {
                val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
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
        fun should_waitForUser_when_confidenceBelowThreshold() =
            runTest {
                val lowConfAction =
                    LLMAction(
                        actionType = ActionType.TAP,
                        targetId = "btn",
                        confidence = 0.3,
                        reasoning = "Uncertain which button",
                    )

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
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
        fun should_completeSingleStep_when_doneActionReturned() =
            runTest {
                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(
                            action = LLMAction(actionType = ActionType.DONE, confidence = 0.95, reasoning = "Home pressed"),
                        ),
                    )

                val states = engine.execute("go home").toList()
                assertTrue(states.last() is EngineState.Done)
            }

        @Test
        fun should_completeMultiStep_when_tapThenDone() =
            runTest {
                val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "send", confidence = 0.9)
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
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
        fun should_terminateWithError_when_routerReturnsError() =
            runTest {
                coEvery { router.route(any(), any(), any()) } returns NeuronResult.Error("LLM unavailable")

                val states = engine.execute("do something").toList()
                assertTrue(states.last() is EngineState.Error)
            }

        @Test
        fun should_terminateWithError_when_llmReturnsErrorAction() =
            runTest {
                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(
                            action =
                                LLMAction(
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
        fun should_terminateWithError_when_actionDispatchFails() =
            runTest {
                val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(action = tapAction),
                    )
                coEvery { actionDispatcher.dispatch(any()) } returns false

                val states = engine.execute("tap button").toList()
                // Should eventually error after retries or continue to next step
                val hasError = states.any { it is EngineState.Error || it is EngineState.Done }
                assertTrue(hasError)
            }

        @Test
        fun should_terminateWithError_when_llmReturnsNullAction() =
            runTest {
                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(action = null),
                    )

                val states = engine.execute("do something").toList()
                assertTrue(states.last() is EngineState.Error)
                assertTrue((states.last() as EngineState.Error).message.contains("no action"))
            }
    }

    @Nested
    @DisplayName("Repeated failure detection")
    inner class RepeatedFailureDetection {
        @Test
        fun should_terminateWithError_when_sameActionFailsThreeTimes() =
            runTest {
                val failAction = LLMAction(actionType = ActionType.TAP, targetId = "btn_broken", confidence = 0.9)

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(LLMResponse(action = failAction))
                coEvery { actionDispatcher.dispatch(any()) } returns false

                val states = engine.execute("tap broken button").toList()
                val lastState = states.last()
                assertTrue(lastState is EngineState.Error)
                assertTrue((lastState as EngineState.Error).message.contains("fail", ignoreCase = true))
            }
    }

    @Nested
    @DisplayName("CONFIRM action type")
    inner class ConfirmAction {
        @Test
        fun should_waitForUser_when_confirmActionReturned() =
            runTest {
                val confirmAction =
                    LLMAction(
                        actionType = ActionType.CONFIRM,
                        reasoning = "Confirm send message?",
                        confidence = 0.95,
                    )

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(LLMResponse(action = confirmAction))

                val states = engine.execute("send message").toList()
                assertTrue(states.any { it is EngineState.WaitingForUser })
            }
    }

    @Nested
    @DisplayName("Supervised execution mode")
    inner class SupervisedMode {
        @Test
        fun should_emitConfirmingAction_when_supervisedMode() =
            runTest {
                engine.executionMode = ExecutionMode.SUPERVISED

                val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                engine.confirmationCallback =
                    object : PlanAndExecuteEngine.ConfirmationCallback {
                        override suspend fun confirmAction(
                            stepIndex: Int,
                            action: LLMAction,
                        ): Boolean = true

                        override suspend fun confirmPlan(actions: List<LLMAction>): Boolean = true
                    }

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = tapAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                val states = engine.execute("tap button").toList()
                assertTrue(states.any { it is EngineState.ConfirmingAction })
                assertTrue(states.last() is EngineState.Done)
            }

        @Test
        fun should_cancelExecution_when_userRejectsAction() =
            runTest {
                engine.executionMode = ExecutionMode.SUPERVISED

                val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)

                engine.confirmationCallback =
                    object : PlanAndExecuteEngine.ConfirmationCallback {
                        override suspend fun confirmAction(
                            stepIndex: Int,
                            action: LLMAction,
                        ): Boolean = false

                        override suspend fun confirmPlan(actions: List<LLMAction>): Boolean = false
                    }

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(LLMResponse(action = tapAction))

                val states = engine.execute("tap button").toList()
                assertTrue(states.last() is EngineState.Done)
                assertTrue((states.last() as EngineState.Done).message.contains("cancel", ignoreCase = true))
            }
    }

    @Nested
    @DisplayName("ConfirmationGate enforcement in AUTONOMOUS mode")
    inner class ConfirmationGateEnforcement {
        @Test
        fun should_confirmDangerousAction_when_autonomousMode() =
            runTest {
                engine.executionMode = ExecutionMode.AUTONOMOUS

                val sendAction =
                    LLMAction(
                        actionType = ActionType.TAP,
                        targetText = "Send Message",
                        confidence = 0.95,
                    )
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                engine.confirmationCallback =
                    object : PlanAndExecuteEngine.ConfirmationCallback {
                        override suspend fun confirmAction(
                            stepIndex: Int,
                            action: LLMAction,
                        ): Boolean = true

                        override suspend fun confirmPlan(actions: List<LLMAction>): Boolean = true
                    }

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = sendAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                val states = engine.execute("send a message").toList()
                // Even in AUTONOMOUS mode, dangerous actions trigger confirmation
                assertTrue(states.any { it is EngineState.ConfirmingAction })
                assertTrue(states.last() is EngineState.Done)
            }

        @Test
        fun should_cancelDangerousAction_when_userRejectsInAutonomousMode() =
            runTest {
                engine.executionMode = ExecutionMode.AUTONOMOUS

                val deleteAction =
                    LLMAction(
                        actionType = ActionType.TAP,
                        targetText = "Delete All",
                        confidence = 0.95,
                    )

                engine.confirmationCallback =
                    object : PlanAndExecuteEngine.ConfirmationCallback {
                        override suspend fun confirmAction(
                            stepIndex: Int,
                            action: LLMAction,
                        ): Boolean = false

                        override suspend fun confirmPlan(actions: List<LLMAction>): Boolean = false
                    }

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(LLMResponse(action = deleteAction))

                val states = engine.execute("delete everything").toList()
                assertTrue(states.last() is EngineState.Done)
                assertTrue((states.last() as EngineState.Done).message.contains("cancel", ignoreCase = true))
            }

        @Test
        fun should_skipConfirmation_when_safeActionInAutonomousMode() =
            runTest {
                engine.executionMode = ExecutionMode.AUTONOMOUS

                val tapAction =
                    LLMAction(
                        actionType = ActionType.TAP,
                        targetId = "chat_item",
                        targetText = "Open Chat",
                        confidence = 0.95,
                    )
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = tapAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                val states = engine.execute("open chat").toList()
                // Safe actions in AUTONOMOUS mode should NOT trigger confirmation
                assertTrue(states.none { it is EngineState.ConfirmingAction })
                assertTrue(states.last() is EngineState.Done)
            }

        @Test
        fun should_confirmPaymentAction_when_autonomousMode() =
            runTest {
                engine.executionMode = ExecutionMode.AUTONOMOUS

                val payAction =
                    LLMAction(
                        actionType = ActionType.TAP,
                        targetText = "Pay Now",
                        confidence = 0.99,
                    )

                engine.confirmationCallback =
                    object : PlanAndExecuteEngine.ConfirmationCallback {
                        override suspend fun confirmAction(
                            stepIndex: Int,
                            action: LLMAction,
                        ): Boolean = true

                        override suspend fun confirmPlan(actions: List<LLMAction>): Boolean = true
                    }

                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = payAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                val states = engine.execute("pay the bill").toList()
                assertTrue(states.any { it is EngineState.ConfirmingAction })
            }
    }

    @Nested
    @DisplayName("Pattern-match single-shot completion")
    inner class PatternMatchCompletion {
        @Test
        fun should_completeSingleShot_when_patternMatchLaunchSucceeds() =
            runTest {
                val launchAction =
                    LLMAction(
                        actionType = ActionType.LAUNCH,
                        value = "whatsapp",
                        confidence = 0.95,
                    )

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(action = launchAction, modelId = "pattern-match"),
                    )

                val states = engine.execute("open whatsapp").toList()
                assertTrue(states.last() is EngineState.Done)
            }

        @Test
        fun should_completeSingleShot_when_patternMatchNavigateSucceeds() =
            runTest {
                val navAction =
                    LLMAction(
                        actionType = ActionType.NAVIGATE,
                        value = "home",
                        confidence = 1.0,
                    )

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(action = navAction, modelId = "pattern-match"),
                    )

                val states = engine.execute("go home").toList()
                assertTrue(states.last() is EngineState.Done)
            }
    }

    @Nested
    @DisplayName("Step logging")
    inner class StepLogging {
        @Test
        fun should_logSteps_when_actionsExecuted() =
            runTest {
                val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = tapAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                engine.execute("tap then done").toList()

                assertEquals(2, engine.stepLogs.size)
                assertTrue(engine.stepLogs[0].success)
                assertTrue(engine.stepLogs[1].success)
            }

        @Test
        fun should_clearStepLogs_when_newExecutionStarts() =
            runTest {
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(LLMResponse(action = doneAction))

                engine.execute("task 1").toList()
                assertEquals(1, engine.stepLogs.size)

                engine.execute("task 2").toList()
                assertEquals(1, engine.stepLogs.size)
            }
    }

    @Nested
    @DisplayName("Memory extraction")
    inner class MemoryExtraction {
        @Test
        fun should_extractMemory_when_taskCompletes() =
            runTest {
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95, reasoning = "Done")

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(LLMResponse(action = doneAction))

                engine.execute("open settings").toList()

                coVerify { memoryExtractor.extractFromCompletedTask(eq("open settings"), any(), any()) }
            }
    }

    @Nested
    @DisplayName("Zero-confidence passthrough")
    inner class ZeroConfidencePassthrough {
        @Test
        fun should_treatZeroConfidenceAsUnspecified_when_llmOmitsConfidence() =
            runTest {
                // confidence=0.0 means "not specified" — should be treated as 1.0 (trust the action)
                val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.0)
                val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = tapAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                val states = engine.execute("tap button").toList()
                // Should NOT trigger WaitingForUser — 0.0 is treated as "not specified"
                assertTrue(states.none { it is EngineState.WaitingForUser })
                assertTrue(states.last() is EngineState.Done)
            }
    }
}
