package ai.neuron.brain.model

import kotlinx.serialization.Serializable

@Serializable
data class IntentClassification(
    val complexity: Complexity,
    val domain: String = "general",
    val estimatedSteps: Int = 1,
    val suggestedTier: LLMTier = LLMTier.T1,
)

@Serializable
enum class Complexity {
    SIMPLE,
    MODERATE,
    COMPLEX,
    ASK_USER,
}
