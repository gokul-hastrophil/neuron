package ai.neuron.brain.client

import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMClientManager @Inject constructor(
    private val geminiClient: GeminiFlashClient,
    private val qwenClient: NvidiaQwenClient,
) {
    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 500L
        private const val BACKOFF_MULTIPLIER = 2.0
    }

    suspend fun generate(
        tier: LLMTier,
        systemPrompt: String,
        userMessage: String,
    ): NeuronResult<LLMResponse> {
        val timeoutMs = tier.latencyBudgetMs

        return withRetry(MAX_RETRIES) {
            try {
                withTimeout(timeoutMs) {
                    when (tier) {
                        LLMTier.T2 -> geminiClient.generate(systemPrompt, userMessage)
                        LLMTier.T3 -> qwenClient.generate(systemPrompt, userMessage)
                        else -> NeuronResult.Error("Tier $tier not supported by cloud clients")
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                NeuronResult.Error("LLM call timed out after ${timeoutMs}ms for tier $tier", e)
            }
        }
    }

    private suspend fun withRetry(
        maxRetries: Int,
        block: suspend () -> NeuronResult<LLMResponse>,
    ): NeuronResult<LLMResponse> {
        var lastError: NeuronResult.Error? = null
        var delayMs = INITIAL_DELAY_MS

        repeat(maxRetries) { attempt ->
            val result = block()
            if (result is NeuronResult.Success) return result

            lastError = result as NeuronResult.Error
            if (attempt < maxRetries - 1) {
                delay(delayMs)
                delayMs = (delayMs * BACKOFF_MULTIPLIER).toLong()
            }
        }

        return lastError ?: NeuronResult.Error("All $maxRetries retries exhausted")
    }
}
