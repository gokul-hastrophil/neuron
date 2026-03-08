package ai.neuron.brain.model

import kotlinx.serialization.Serializable

@Serializable
data class StepLog(
    val stepIndex: Int,
    val uiTreeHash: Int,
    val llmTier: String? = null,
    val action: LLMAction,
    val success: Boolean,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
)
