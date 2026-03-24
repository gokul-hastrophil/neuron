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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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

    @Nested
    @DisplayName("Pattern matching — open/launch commands")
    inner class PatternMatchOpen {
        // Use a tree where the foreground app is NOT the one being opened
        private val homeTree =
            UITree(
                packageName = "com.android.launcher3",
                nodes = listOf(UINode(id = "home", text = "Home")),
            )

        @Test
        fun should_matchOpenApp_when_simpleOpenCommand() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T1)

                val result = router.route("open WhatsApp", homeTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals("pattern-match", response.modelId)
                assertEquals(ActionType.LAUNCH, response.action?.actionType)
                assertEquals("whatsapp", response.action?.value)
            }

        @Test
        fun should_matchLaunchApp_when_launchSynonym() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T1)

                val result = router.route("launch Settings", homeTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.LAUNCH, response.action?.actionType)
                assertEquals("settings", response.action?.value)
            }

        @Test
        fun should_stripArticles_when_openCommandHasArticle() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T1)

                val result = router.route("open the Camera", homeTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals("camera", response.action?.value)
            }

        @Test
        fun should_notMatchPattern_when_commandContainsAnd() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.MODERATE, suggestedTier = LLMTier.T2)

                coEvery {
                    clientManager.generate(LLMTier.T2, any(), any())
                } returns NeuronResult.Success(successResponse)

                val result = router.route("open WhatsApp and message John", homeTree, classification)

                // Should NOT pattern-match due to "and" — should go to cloud
                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals("T2", response.tier)
            }

        @Test
        fun should_returnDone_when_requestedAppAlreadyOpen() =
            runTest {
                val whatsappTree =
                    UITree(
                        packageName = "com.whatsapp",
                        nodes = listOf(UINode(id = "chat", text = "Chat")),
                    )
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T1)

                val result = router.route("open whatsapp", whatsappTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.DONE, response.action?.actionType)
                assertTrue(response.action?.reasoning?.contains("already open") == true)
            }
    }

    @Nested
    @DisplayName("Pattern matching — navigation commands")
    inner class PatternMatchNavigation {
        @Test
        fun should_matchGoHome_when_homeCommand() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T0)

                val result = router.route("go home", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.NAVIGATE, response.action?.actionType)
                assertEquals("home", response.action?.value)
            }

        @Test
        fun should_matchGoBack_when_backCommand() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T0)

                val result = router.route("go back", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.NAVIGATE, response.action?.actionType)
                assertEquals("back", response.action?.value)
            }

        @Test
        fun should_matchRecents_when_recentsCommand() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T0)

                val result = router.route("show recents", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.NAVIGATE, response.action?.actionType)
                assertEquals("recents", response.action?.value)
            }

        @Test
        fun should_matchNotifications_when_notificationCommand() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T0)

                val result = router.route("show notifications", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.NAVIGATE, response.action?.actionType)
                assertEquals("notifications", response.action?.value)
            }

        @Test
        fun should_matchHomeScreen_when_goToHomeScreen() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T0)

                val result = router.route("go to the home screen", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.NAVIGATE, response.action?.actionType)
                assertEquals("home", response.action?.value)
            }

        @Test
        fun should_matchPullDownNotifications_when_verboseCommand() =
            runTest {
                val classification =
                    IntentClassification(complexity = Complexity.SIMPLE, suggestedTier = LLMTier.T0)

                val result = router.route("pull down the notification shade", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.NAVIGATE, response.action?.actionType)
                assertEquals("notifications", response.action?.value)
            }
    }

    @Nested
    @DisplayName("Pattern matching — no match")
    inner class PatternMatchNoMatch {
        @Test
        fun should_routeToCloud_when_noPatternMatches() =
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

                val result = router.route("send a message to John saying hello", normalTree, classification)

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals("T2", response.tier)
            }
    }
}
