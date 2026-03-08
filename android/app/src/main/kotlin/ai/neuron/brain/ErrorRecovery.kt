package ai.neuron.brain

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorRecovery @Inject constructor() {

    sealed class RecoveryStrategy {
        data class ScrollAndRetry(val direction: String = "down") : RecoveryStrategy()
        data class RetryWithSimplifiedPrompt(val simplifiedCommand: String) : RecoveryStrategy()
        data class RelaunchApp(val packageName: String) : RecoveryStrategy()
        data class ShowError(val message: String, val suggestion: String) : RecoveryStrategy()
        data object GiveUp : RecoveryStrategy()
    }

    fun suggestRecovery(
        errorMessage: String,
        failedAction: LLMAction?,
        attemptCount: Int,
    ): RecoveryStrategy {
        if (attemptCount >= 3) return RecoveryStrategy.GiveUp

        return when {
            errorMessage.contains("not found", ignoreCase = true) ||
                errorMessage.contains("no matching", ignoreCase = true) ->
                RecoveryStrategy.ScrollAndRetry()

            errorMessage.contains("parse", ignoreCase = true) ||
                errorMessage.contains("json", ignoreCase = true) ||
                errorMessage.contains("malformed", ignoreCase = true) ->
                RecoveryStrategy.RetryWithSimplifiedPrompt(
                    simplifiedCommand = "Retry the previous action with simpler instructions",
                )

            errorMessage.contains("crash", ignoreCase = true) ||
                errorMessage.contains("stopped", ignoreCase = true) ->
                RecoveryStrategy.RelaunchApp(
                    packageName = failedAction?.value ?: "",
                )

            errorMessage.contains("timeout", ignoreCase = true) ||
                errorMessage.contains("network", ignoreCase = true) ->
                RecoveryStrategy.ShowError(
                    message = "Network error",
                    suggestion = "Check your internet connection and try again",
                )

            else -> RecoveryStrategy.ShowError(
                message = errorMessage,
                suggestion = "Try rephrasing your command",
            )
        }
    }

    fun recoveryAction(strategy: RecoveryStrategy): LLMAction? = when (strategy) {
        is RecoveryStrategy.ScrollAndRetry -> LLMAction(
            actionType = ActionType.SWIPE,
            value = strategy.direction,
            confidence = 0.8,
            reasoning = "Scrolling to find element",
        )
        is RecoveryStrategy.RelaunchApp -> LLMAction(
            actionType = ActionType.LAUNCH,
            value = strategy.packageName,
            confidence = 0.9,
            reasoning = "Relaunching app after crash",
        )
        else -> null
    }
}
