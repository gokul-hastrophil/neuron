package ai.neuron.brain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Single action returned by the LLM, matching docs/architecture/action_schema.json.
 */
@Serializable
data class LLMAction(
    @SerialName("action_type") val actionType: ActionType,
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("target_text") val targetText: String? = null,
    val value: String? = null,
    val confidence: Double = 0.0,
    val reasoning: String? = null,
    @SerialName("requires_confirmation") val requiresConfirmation: Boolean = false,
    @SerialName("timeout_ms") val timeoutMs: Int? = null,
    @SerialName("retry_count") val retryCount: Int? = null,
    val sensitive: Boolean = false,
)

@Serializable
enum class ActionType {
    @SerialName("tap") TAP,
    @SerialName("type") TYPE,
    @SerialName("swipe") SWIPE,
    @SerialName("launch") LAUNCH,
    @SerialName("navigate") NAVIGATE,
    @SerialName("wait") WAIT,
    @SerialName("done") DONE,
    @SerialName("error") ERROR,
    @SerialName("confirm") CONFIRM,
}

/**
 * Multi-step plan returned by the LLM for complex tasks.
 */
@Serializable
data class NeuronActionPlan(
    val steps: List<LLMAction>,
    val goal: String? = null,
    @SerialName("estimated_steps") val estimatedSteps: Int? = null,
)

/**
 * Full LLM response wrapper including metadata.
 */
@Serializable
data class LLMResponse(
    val action: LLMAction? = null,
    val plan: NeuronActionPlan? = null,
    val tier: String? = null,
    @SerialName("model_id") val modelId: String? = null,
    @SerialName("latency_ms") val latencyMs: Long? = null,
    @SerialName("tokens_used") val tokensUsed: Int? = null,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

        fun fromJson(jsonString: String): LLMResponse = json.decodeFromString(jsonString)
    }

    fun toJson(): String = json.encodeToString(serializer(), this)
}
