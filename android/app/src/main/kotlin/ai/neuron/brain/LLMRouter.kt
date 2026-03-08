package ai.neuron.brain

import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.client.LLMClientManager
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.IntentClassification
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMRouter @Inject constructor(
    private val sensitivityGate: SensitivityGate,
    private val clientManager: LLMClientManager,
) {
    companion object {
        private val FALLBACK_CHAIN = mapOf(
            LLMTier.T2 to LLMTier.T3,
        )
    }

    suspend fun route(
        command: String,
        uiTree: UITree,
        classification: IntentClassification,
    ): NeuronResult<LLMResponse> {
        val effectiveTier = if (sensitivityGate.isSensitive(uiTree)) {
            LLMTier.T4
        } else {
            classification.suggestedTier
        }

        return when (effectiveTier) {
            LLMTier.T0, LLMTier.T1, LLMTier.T4 -> handleOnDevice(command, uiTree, effectiveTier)
            LLMTier.T2, LLMTier.T3 -> handleCloud(command, uiTree, effectiveTier)
        }
    }

    private suspend fun handleOnDevice(
        command: String,
        uiTree: UITree,
        tier: LLMTier,
    ): NeuronResult<LLMResponse> {
        // On-device LLM (Gemma 3n) — placeholder until MediaPipe integration
        return NeuronResult.Success(
            LLMResponse(
                action = LLMAction(
                    actionType = ActionType.DONE,
                    reasoning = "On-device processing for tier $tier",
                    confidence = 0.5,
                ),
                tier = tier.name,
                modelId = tier.modelId,
            ),
        )
    }

    private suspend fun handleCloud(
        command: String,
        uiTree: UITree,
        tier: LLMTier,
    ): NeuronResult<LLMResponse> {
        val systemPrompt = buildSystemPrompt(command)
        val userMessage = buildUserMessage(command, uiTree)

        val result = clientManager.generate(tier, systemPrompt, userMessage)

        if (result is NeuronResult.Error) {
            val fallbackTier = FALLBACK_CHAIN[tier]
            if (fallbackTier != null) {
                return clientManager.generate(fallbackTier, systemPrompt, userMessage)
            }
        }

        return result
    }

    private fun buildSystemPrompt(command: String): String {
        return """You are Neuron, an AI agent that controls Android phones.
            |Given a UI tree (JSON) and a user command, output a single action as JSON.
            |Action schema: {"action_type": "tap|type|swipe|launch|navigate|wait|done|error", "target_id": "...", "target_text": "...", "value": "...", "confidence": 0.0-1.0, "reasoning": "..."}
        """.trimMargin()
    }

    private fun buildUserMessage(command: String, uiTree: UITree): String {
        return """Command: $command
            |
            |UI Tree:
            |${uiTree.toJson()}
        """.trimMargin()
    }
}
