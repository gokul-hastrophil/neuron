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

class ExecutionModeTest {
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
            packageName = "com.example",
            nodes = listOf(UINode(id = "btn", text = "Button", clickable = true)),
        )

    private val tapAction = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)
    private val doneAction = LLMAction(actionType = ActionType.DONE, confidence = 0.95)

    @BeforeEach
    fun setup() {
        router = mockk()
        classifier = mockk()
        uiProvider = mockk()
        actionDispatcher = mockk()
        memoryExtractor = mockk(relaxed = true)
        confirmationGate = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)

        every { classifier.classify(any()) } returns
            IntentClassification(
                complexity = Complexity.MODERATE,
                suggestedTier = LLMTier.T2,
                estimatedSteps = 2,
            )
        coEvery { uiProvider.getCurrentUITree() } returns normalTree
        coEvery { actionDispatcher.dispatch(any()) } returns true

        engine = PlanAndExecuteEngine(router, classifier, uiProvider, actionDispatcher, memoryExtractor, confirmationGate, auditRepository)
    }

    @Nested
    @DisplayName("AUTONOMOUS mode")
    inner class AutonomousMode {
        @Test
        fun should_executeWithoutConfirmation_when_autonomous() =
            runTest {
                engine.executionMode = ExecutionMode.AUTONOMOUS

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = tapAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                val states = engine.execute("tap button").toList()

                // Should NOT have ConfirmingAction state
                val hasConfirming = states.any { it is EngineState.ConfirmingAction }
                assertTrue(!hasConfirming, "AUTONOMOUS mode should not emit ConfirmingAction")
                assertTrue(states.any { it is EngineState.Executing })
                assertTrue(states.last() is EngineState.Done)
            }
    }

    @Nested
    @DisplayName("SUPERVISED mode")
    inner class SupervisedMode {
        @Test
        fun should_emitConfirmingAction_when_supervised() =
            runTest {
                engine.executionMode = ExecutionMode.SUPERVISED
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

                val hasConfirming = states.any { it is EngineState.ConfirmingAction }
                assertTrue(hasConfirming, "SUPERVISED mode should emit ConfirmingAction")
                assertTrue(states.any { it is EngineState.Executing })
                assertTrue(states.last() is EngineState.Done)
            }

        @Test
        fun should_stopExecution_when_userRejectsAction() =
            runTest {
                engine.executionMode = ExecutionMode.SUPERVISED
                engine.confirmationCallback =
                    object : PlanAndExecuteEngine.ConfirmationCallback {
                        override suspend fun confirmAction(
                            stepIndex: Int,
                            action: LLMAction,
                        ): Boolean = false

                        override suspend fun confirmPlan(actions: List<LLMAction>): Boolean = true
                    }

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(action = tapAction),
                    )

                val states = engine.execute("tap button").toList()

                // Should stop with Done("User cancelled action")
                val lastState = states.last()
                assertTrue(lastState is EngineState.Done)
                assertEquals("User cancelled action", (lastState as EngineState.Done).message)
            }

        @Test
        fun should_passStepIndex_when_confirming() =
            runTest {
                engine.executionMode = ExecutionMode.SUPERVISED
                var capturedStepIndex = -1
                engine.confirmationCallback =
                    object : PlanAndExecuteEngine.ConfirmationCallback {
                        override suspend fun confirmAction(
                            stepIndex: Int,
                            action: LLMAction,
                        ): Boolean {
                            capturedStepIndex = stepIndex
                            return true
                        }

                        override suspend fun confirmPlan(actions: List<LLMAction>): Boolean = true
                    }

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = tapAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                engine.execute("tap button").toList()

                assertEquals(0, capturedStepIndex, "First step should have index 0")
            }

        @Test
        fun should_skipConfirmation_when_callbackIsNull() =
            runTest {
                engine.executionMode = ExecutionMode.SUPERVISED
                engine.confirmationCallback = null // No callback set

                coEvery { router.route(any(), any(), any()) } returnsMany
                    listOf(
                        NeuronResult.Success(LLMResponse(action = tapAction)),
                        NeuronResult.Success(LLMResponse(action = doneAction)),
                    )

                val states = engine.execute("tap button").toList()

                // Should still complete — null callback defaults to approved
                assertTrue(states.last() is EngineState.Done)
            }
    }

    @Nested
    @DisplayName("ExecutionMode enum")
    inner class ExecutionModeEnum {
        @Test
        fun should_haveThreeValues() {
            val values = ExecutionMode.entries
            assertEquals(3, values.size)
            assertTrue(ExecutionMode.AUTONOMOUS in values)
            assertTrue(ExecutionMode.SUPERVISED in values)
            assertTrue(ExecutionMode.PLAN_APPROVE in values)
        }

        @Test
        fun should_defaultToAutonomous_when_notSet() {
            val newEngine =
                PlanAndExecuteEngine(router, classifier, uiProvider, actionDispatcher, memoryExtractor, confirmationGate, auditRepository)
            assertEquals(ExecutionMode.AUTONOMOUS, newEngine.executionMode)
        }
    }

    @Nested
    @DisplayName("DONE action bypasses confirmation")
    inner class DoneActionBypass {
        @Test
        fun should_notConfirm_when_actionIsDone() =
            runTest {
                engine.executionMode = ExecutionMode.SUPERVISED
                var confirmCalled = false
                engine.confirmationCallback =
                    object : PlanAndExecuteEngine.ConfirmationCallback {
                        override suspend fun confirmAction(
                            stepIndex: Int,
                            action: LLMAction,
                        ): Boolean {
                            confirmCalled = true
                            return true
                        }

                        override suspend fun confirmPlan(actions: List<LLMAction>): Boolean = true
                    }

                coEvery { router.route(any(), any(), any()) } returns
                    NeuronResult.Success(
                        LLMResponse(action = doneAction),
                    )

                val states = engine.execute("done").toList()

                assertTrue(!confirmCalled, "DONE action should not trigger confirmation")
                assertTrue(states.last() is EngineState.Done)
            }
    }
}
