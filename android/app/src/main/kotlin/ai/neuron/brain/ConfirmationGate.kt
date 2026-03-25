package ai.neuron.brain

import ai.neuron.brain.model.LLMAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canonical confirmation gate — enforced for ALL execution modes.
 * Irreversible actions (send, pay, delete, etc.) ALWAYS require user confirmation,
 * regardless of AUTONOMOUS vs SUPERVISED mode. This is a hard security rule.
 */
@Singleton
class ConfirmationGate
    @Inject
    constructor() {
        companion object {
            private val IRREVERSIBLE_KEYWORDS =
                listOf(
                    "send", "pay", "delete", "remove", "post", "submit",
                    "transfer", "purchase", "confirm", "publish", "share",
                    "uninstall", "buy",
                )

            private const val CONFIDENCE_THRESHOLD = 0.7
        }

        fun requiresConfirmation(action: LLMAction): Boolean {
            // Explicit flag from LLM
            if (action.requiresConfirmation) return true

            // Sensitive actions always need confirmation
            if (action.sensitive) return true

            // Low confidence — ask the user
            val effectiveConfidence = if (action.confidence == 0.0) 1.0 else action.confidence
            if (effectiveConfidence < CONFIDENCE_THRESHOLD) return true

            // Check all text fields for irreversible action keywords
            val textToCheck =
                listOfNotNull(
                    action.targetText,
                    action.value,
                    action.reasoning,
                ).joinToString(" ").lowercase()

            return IRREVERSIBLE_KEYWORDS.any { keyword ->
                textToCheck.contains(keyword)
            }
        }
    }
