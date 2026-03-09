package ai.neuron.brain.client

import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import android.util.Log
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMClientManager @Inject constructor(
    private val geminiClient: GeminiFlashClient,
    private val qwenClient: NvidiaQwenClient,
    private val openRouterClient: OpenRouterClient,
    private val ollamaCloudClient: OllamaCloudClient,
) {
    companion object {
        private const val TAG = "NeuronLLMClientManager"
        /** Per-provider timeout — each provider gets its own budget, not shared. */
        private const val PROVIDER_TIMEOUT_MS = 25_000L
    }

    suspend fun generate(
        tier: LLMTier,
        systemPrompt: String,
        userMessage: String,
    ): NeuronResult<LLMResponse> = when (tier) {
        LLMTier.T2 -> generateT2WithFallback(systemPrompt, userMessage)
        LLMTier.T3 -> generateT3WithFallback(systemPrompt, userMessage)
        else -> NeuronResult.Error("Tier $tier not supported by cloud clients")
    }

    /** T2: Gemini (primary, 10s) → Ollama Cloud Qwen3-VL 235B (fallback, 25s). */
    private suspend fun generateT2WithFallback(
        systemPrompt: String,
        userMessage: String,
    ): NeuronResult<LLMResponse> {
        // Gemini is fast — give it 10s for the 4-model retry chain
        val geminiResult = callWithTimeout(10_000L) {
            geminiClient.generate(systemPrompt, userMessage)
        }
        if (geminiResult is NeuronResult.Success) return geminiResult
        Log.w(TAG, "Gemini failed: ${(geminiResult as NeuronResult.Error).message}, trying Ollama Cloud...")

        // Ollama 235B thinking model needs 15-20s — give it 25s
        return callWithTimeout(PROVIDER_TIMEOUT_MS) {
            ollamaCloudClient.generate(systemPrompt, userMessage)
        }
    }

    /** T3: Ollama Cloud (primary, 25s) → NVIDIA Qwen (fallback, 10s) → OpenRouter (last resort, 15s). */
    private suspend fun generateT3WithFallback(
        systemPrompt: String,
        userMessage: String,
    ): NeuronResult<LLMResponse> {
        val ollamaResult = callWithTimeout(PROVIDER_TIMEOUT_MS) {
            ollamaCloudClient.generate(systemPrompt, userMessage)
        }
        if (ollamaResult is NeuronResult.Success) return ollamaResult
        Log.w(TAG, "Ollama Cloud failed: ${(ollamaResult as NeuronResult.Error).message}, trying NVIDIA Qwen...")

        val qwenResult = callWithTimeout(10_000L) {
            qwenClient.generate(systemPrompt, userMessage)
        }
        if (qwenResult is NeuronResult.Success) return qwenResult
        Log.w(TAG, "NVIDIA Qwen failed: ${(qwenResult as NeuronResult.Error).message}, trying OpenRouter...")

        return callWithTimeout(15_000L) {
            openRouterClient.generate(systemPrompt, userMessage)
        }
    }

    private suspend fun callWithTimeout(
        timeoutMs: Long,
        block: suspend () -> NeuronResult<LLMResponse>,
    ): NeuronResult<LLMResponse> = try {
        withTimeout(timeoutMs) { block() }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        NeuronResult.Error("LLM call timed out after ${timeoutMs}ms", e)
    }
}
