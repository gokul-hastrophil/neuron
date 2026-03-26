package ai.neuron.brain.client

import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import android.util.Log
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMClientManager
    @Inject
    constructor(
        private val proxyClient: LlmProxyClient,
    ) {
        companion object {
            private const val TAG = "NeuronLLMClientManager"
            private const val PROXY_TIMEOUT_MS = 30_000L
        }

        suspend fun generate(
            tier: LLMTier,
            systemPrompt: String,
            userMessage: String,
        ): NeuronResult<LLMResponse> {
            if (tier != LLMTier.T2 && tier != LLMTier.T3) {
                return NeuronResult.Error("Tier $tier not supported by cloud proxy")
            }

            return callWithTimeout(PROXY_TIMEOUT_MS) {
                proxyClient.generate(tier, systemPrompt, userMessage)
            }
        }

        private suspend fun callWithTimeout(
            timeoutMs: Long,
            block: suspend () -> NeuronResult<LLMResponse>,
        ): NeuronResult<LLMResponse> =
            try {
                withTimeout(timeoutMs) { block() }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "LLM proxy call timed out after ${timeoutMs}ms")
                NeuronResult.Error("LLM proxy call timed out after ${timeoutMs}ms", e)
            }
    }
