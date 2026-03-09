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
            |Given a UI tree (JSON) and a user command, output a SINGLE next action as JSON.
            |
            |Action schema: {"action_type": "tap|type|swipe|launch|navigate|wait|done|error", "target_id": "node_resource_id", "target_text": "visible_text", "value": "text_or_package", "confidence": 0.0-1.0, "reasoning": "why"}
            |
            |RULES:
            |1. To open an app, ALWAYS use action_type "launch" with value set to the Android package name (e.g. "com.android.chrome", "com.android.settings"). NEVER use "tap" to open apps.
            |2. For "tap", target_id MUST match an exact id from the UI tree. If no matching node exists, lower your confidence.
            |3. For "type", target_id must be an editable field's id, and value is the text to type.
            |4. For "navigate", value is one of: "home", "back", "recents", "notifications".
            |5. Output only the JSON object. No markdown, no explanation outside the JSON.
            |6. If the task appears complete based on the UI tree, use action_type "done".
            |7. ALWAYS include "confidence" (0.0-1.0) in your response. Set 0.9+ when certain, 0.7-0.9 when likely correct, below 0.7 only when unsure.
            |8. Check the "package_name" field in the UI tree. If the requested app is already in the foreground, do NOT launch it again — proceed to the next step of the task.
            |9. Analyze what's visible in the UI tree to determine what step of the task has already been completed.
            |10. After typing text in a search bar or URL bar, you MUST submit it: use action_type "navigate" with value "enter" to press Enter, OR use "tap" on a search suggestion/button. Do NOT just type and stop — always follow TYPE with a submit action.
            |11. To open the notification shade, use action_type "navigate" with value "notifications". Do NOT use "swipe" for this.
        """.trimMargin()
    }

    private fun buildUserMessage(command: String, uiTree: UITree): String {
        return """Command: $command
            |
            |Current foreground app: ${uiTree.packageName}
            |
            |UI Tree:
            |${uiTree.toJson()}
        """.trimMargin()
    }
}
