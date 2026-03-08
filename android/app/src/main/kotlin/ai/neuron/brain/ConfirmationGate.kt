package ai.neuron.brain

import ai.neuron.brain.model.LLMAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfirmationGate @Inject constructor() {

    companion object {
        private val IRREVERSIBLE_KEYWORDS = listOf(
            "send", "pay", "delete", "remove", "post", "submit",
            "transfer", "purchase", "confirm", "publish", "share",
        )
    }

    fun requiresConfirmation(action: LLMAction): Boolean {
        if (action.requiresConfirmation) return true
        if (action.sensitive) return true

        val textToCheck = listOfNotNull(
            action.targetText,
            action.value,
            action.reasoning,
        ).joinToString(" ").lowercase()

        return IRREVERSIBLE_KEYWORDS.any { keyword ->
            textToCheck.contains(keyword)
        }
    }
}
