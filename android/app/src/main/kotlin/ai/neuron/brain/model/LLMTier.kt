package ai.neuron.brain.model

/**
 * LLM routing tiers with latency budgets and model mappings.
 */
enum class LLMTier(
    val latencyBudgetMs: Long,
    val modelId: String,
    val isOnDevice: Boolean,
) {
    T0(latencyBudgetMs = 50, modelId = "porcupine", isOnDevice = true),
    T1(latencyBudgetMs = 500, modelId = "gemma-3n", isOnDevice = true),
    T2(latencyBudgetMs = 2_000, modelId = "gemini-2.5-flash", isOnDevice = false),
    T3(latencyBudgetMs = 5_000, modelId = "claude-sonnet-4-5", isOnDevice = false),
    T4(latencyBudgetMs = 500, modelId = "gemma-3n", isOnDevice = true),
}
