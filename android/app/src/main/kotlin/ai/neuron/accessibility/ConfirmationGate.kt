package ai.neuron.accessibility

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Determines whether an LLM-generated action requires user confirmation
 * before execution. Irreversible actions (send, delete, pay, transfer)
 * and low-confidence actions always require confirmation.
 */
@Singleton
class ConfirmationGate @Inject constructor() {

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.7

        private val DANGEROUS_KEYWORDS = listOf(
            "send", "delete", "remove", "pay", "transfer",
            "confirm", "purchase", "buy", "submit", "uninstall",
        )
    }

    fun requiresConfirmation(action: LLMAction): Boolean {
        // Explicit flag from LLM
        if (action.requiresConfirmation) return true

        // Sensitive actions always need confirmation
        if (action.sensitive) return true

        // Low confidence — ask the user
        if (action.confidence < CONFIDENCE_THRESHOLD) return true

        // Check if tapping a dangerous button
        if (action.actionType == ActionType.TAP) {
            val text = action.targetText?.lowercase() ?: return false
            return DANGEROUS_KEYWORDS.any { keyword -> text.contains(keyword) }
        }

        return false
    }
}
