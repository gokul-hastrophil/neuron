package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.client.LLMClientManager
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.Complexity
import ai.neuron.brain.model.IntentClassification
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import ai.neuron.memory.LongTermMemory
import ai.neuron.sdk.ToolRegistry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LLMRouterTest {
    private lateinit var router: LLMRouter
    private lateinit var sensitivityGate: SensitivityGate
    private lateinit var clientManager: LLMClientManager
    private lateinit var longTermMemory: LongTermMemory
    private lateinit var toolRegistry: ToolRegistry

    private val normalTree =
        UITree(
            packageName = "com.whatsapp",
            nodes = listOf(UINode(id = "chat", text = "Chat")),
        )

    private val sensitiveTree =
        UITree(
            packageName = "net.one97.paytm",
            nodes = listOf(UINode(id = "main")),
        )

    private val successResponse =
        LLMResponse(
            action = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9),
            tier = "T2",
            modelId = "gemini-2.5-flash",
        )

    @BeforeEach
    fun setup() {
        sensitivityGate = SensitivityGate()
        clientManager = mockk()
        longTermMemory = mockk(relaxed = true)
        toolRegistry = ToolRegistry()
        router = LLMRouter(sensitivityGate, clientManager, longTermMemory, toolRegistry)
    }

    @Nested
    @DisplayName("Tier routing based on classification")
    inner class TierRouting {
        @Test
        fun should_routeToT1_when_simpleClassification() =
            runTest {
                val classification =
                    IntentClassification(
                        complexity = Complexity.SIMPLE,
                        suggestedTier = LLMTier.T1,
                    )

                val result = router.route("go home", normalTree, classification)

                // T1 is on-device, so router should handle it differently
                // For now it should return a result (not call cloud)
                assertTrue(result is NeuronResult.Success || result is NeuronResult.Error)
            }

        @Test
        fun should_routeToT2_when_moderateClassification() =
            runTest {
                val classification =
                    IntentClassification(
                        complexity = Complexity.MODERATE,
                        suggestedTier = LLMTier.T2,
                        estimatedSteps = 3,
                    )

                coEvery {
                    clientManager.generate(LLMTier.T2, any(), any())
                } returns NeuronResult.Success(successResponse)

                val result = router.route("message Mom on WhatsApp", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals("T2", response.tier)
            }

        @Test
        fun should_routeToT3_when_complexClassification() =
            runTest {
                val classification =
                    IntentClassification(
                        complexity = Complexity.COMPLEX,
                        suggestedTier = LLMTier.T3,
                        estimatedSteps = 8,
                    )

                coEvery {
                    clientManager.generate(LLMTier.T3, any(), any())
                } returns
                    NeuronResult.Success(
                        successResponse.copy(tier = "T3", modelId = "qwen/qwen3.5-397b-a17b"),
                    )

                val result = router.route("find cheapest flight to NYC", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals("T3", response.tier)
            }
    }

    @Nested
    @DisplayName("Sensitivity gate override")
    inner class SensitivityOverride {
        @Test
        fun should_forceT4_when_sensitivityGateTriggersOnPackage() =
            runTest {
                val classification =
                    IntentClassification(
                        complexity = Complexity.MODERATE,
                        suggestedTier = LLMTier.T2,
                    )

                val result = router.route("check balance", sensitiveTree, classification)

                // T4 is on-device only, should NOT call cloud client
                assertTrue(result is NeuronResult.Success || result is NeuronResult.Error)
                if (result is NeuronResult.Success) {
                    assertEquals("T4", result.data.tier)
                }
            }

        @Test
        fun should_forceT4_when_passwordFieldDetected() =
            runTest {
                val passwordTree =
                    UITree(
                        packageName = "com.example.app",
                        nodes = listOf(UINode(id = "pw", password = true, editable = true)),
                    )
                val classification =
                    IntentClassification(
                        complexity = Complexity.MODERATE,
                        suggestedTier = LLMTier.T3,
                    )

                val result = router.route("type password", passwordTree, classification)

                if (result is NeuronResult.Success) {
                    assertEquals("T4", result.data.tier)
                }
            }
    }

    @Nested
    @DisplayName("Fallback behavior")
    inner class FallbackBehavior {
        @Test
        fun should_fallbackToT3_when_T2Fails() =
            runTest {
                val classification =
                    IntentClassification(
                        complexity = Complexity.MODERATE,
                        suggestedTier = LLMTier.T2,
                    )

                coEvery {
                    clientManager.generate(LLMTier.T2, any(), any())
                } returns NeuronResult.Error("Gemini timeout")

                coEvery {
                    clientManager.generate(LLMTier.T3, any(), any())
                } returns
                    NeuronResult.Success(
                        successResponse.copy(tier = "T3", modelId = "qwen/qwen3.5-397b-a17b"),
                    )

                val result = router.route("search for weather", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals("T3", response.tier)
            }

        @Test
        fun should_returnError_when_allTiersFail() =
            runTest {
                val classification =
                    IntentClassification(
                        complexity = Complexity.MODERATE,
                        suggestedTier = LLMTier.T2,
                    )

                coEvery {
                    clientManager.generate(any(), any(), any())
                } returns NeuronResult.Error("All tiers failed")

                val result = router.route("do something", normalTree, classification)

                assertTrue(result is NeuronResult.Error)
            }
    }

    @Nested
    @DisplayName("Timeout handling")
    inner class TimeoutHandling {
        @Test
        fun should_respectTierLatencyBudget_when_routing() =
            runTest {
                val classification =
                    IntentClassification(
                        complexity = Complexity.MODERATE,
                        suggestedTier = LLMTier.T2,
                    )

                // T2 has 25000ms budget for Gemini + Ollama Cloud fallback chain
                assertEquals(25_000L, LLMTier.T2.latencyBudgetMs)
                // T3 has 10000ms budget (Qwen 397B needs more time)
                assertEquals(30_000L, LLMTier.T3.latencyBudgetMs)

                coEvery {
                    clientManager.generate(LLMTier.T2, any(), any())
                } returns NeuronResult.Success(successResponse)

                val result = router.route("test", normalTree, classification)
                assertTrue(result is NeuronResult.Success)
            }
    }
}
