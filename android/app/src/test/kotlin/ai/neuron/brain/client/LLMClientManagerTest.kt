package ai.neuron.brain.client

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LLMClientManagerTest {
    private lateinit var proxyClient: LlmProxyClient
    private lateinit var manager: LLMClientManager

    private val successResponse =
        LLMResponse(
            action = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9),
            tier = "T2",
            modelId = "gemini-2.5-flash",
        )

    @BeforeEach
    fun setup() {
        proxyClient = mockk()
        manager = LLMClientManager(proxyClient)
    }

    @Nested
    @DisplayName("Tier routing")
    inner class TierRouting {
        @Test
        fun should_delegateToProxy_when_T2Requested() =
            runTest {
                coEvery {
                    proxyClient.generate(LLMTier.T2, any(), any())
                } returns NeuronResult.Success(successResponse)

                val result = manager.generate(LLMTier.T2, "system", "user")

                assertTrue(result is NeuronResult.Success)
                assertEquals("T2", (result as NeuronResult.Success).data.tier)
            }

        @Test
        fun should_delegateToProxy_when_T3Requested() =
            runTest {
                coEvery {
                    proxyClient.generate(LLMTier.T3, any(), any())
                } returns NeuronResult.Success(successResponse.copy(tier = "T3"))

                val result = manager.generate(LLMTier.T3, "system", "user")

                assertTrue(result is NeuronResult.Success)
                assertEquals("T3", (result as NeuronResult.Success).data.tier)
            }

        @Test
        fun should_returnError_when_T0Requested() =
            runTest {
                val result = manager.generate(LLMTier.T0, "system", "user")

                assertTrue(result is NeuronResult.Error)
                assertTrue((result as NeuronResult.Error).message.contains("not supported"))
            }

        @Test
        fun should_returnError_when_T1Requested() =
            runTest {
                val result = manager.generate(LLMTier.T1, "system", "user")

                assertTrue(result is NeuronResult.Error)
            }

        @Test
        fun should_returnError_when_T4Requested() =
            runTest {
                val result = manager.generate(LLMTier.T4, "system", "user")

                assertTrue(result is NeuronResult.Error)
            }
    }

    @Nested
    @DisplayName("Error propagation")
    inner class ErrorPropagation {
        @Test
        fun should_propagateProxyError_when_proxyFails() =
            runTest {
                coEvery {
                    proxyClient.generate(LLMTier.T2, any(), any())
                } returns NeuronResult.Error("proxy unreachable")

                val result = manager.generate(LLMTier.T2, "system", "user")

                assertTrue(result is NeuronResult.Error)
                assertTrue((result as NeuronResult.Error).message.contains("proxy unreachable"))
            }
    }
}
