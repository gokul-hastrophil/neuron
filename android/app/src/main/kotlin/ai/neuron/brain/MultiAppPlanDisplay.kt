package ai.neuron.brain

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a human-readable summary of a multi-app task plan for user review
 * before execution. Shows apps involved, estimated steps, and data that will
 * be shared between apps.
 */
@Singleton
class MultiAppPlanDisplay
    @Inject
    constructor(
        private val appResolver: AppResolver,
    ) {
        /**
         * Summary of a multi-app plan for display to the user.
         */
        data class PlanSummary(
            val apps: List<String>,
            val totalSteps: Int,
            val stepDescriptions: List<String>,
            val dataTransfers: List<String>,
            val isMultiApp: Boolean,
        )

        /**
         * Analyze a list of planned actions and produce a user-facing summary.
         * Returns a PlanSummary describing what will happen.
         */
        fun summarize(actions: List<LLMAction>): PlanSummary {
            val apps = mutableListOf<String>()
            val descriptions = mutableListOf<String>()
            val transfers = mutableListOf<String>()

            for (action in actions) {
                // Detect app targets from LAUNCH actions
                if (action.actionType == ActionType.LAUNCH) {
                    val appName = action.value ?: "unknown app"
                    if (apps.lastOrNull() != appName) {
                        apps.add(appName)
                    }
                }

                // Build step description from reasoning or action type
                val desc = action.reasoning ?: describeAction(action)
                descriptions.add(desc)

                // Detect data transfers (TYPE actions that reference cross-app data)
                if (action.actionType == ActionType.TYPE && apps.size > 1) {
                    val value = action.value ?: ""
                    if (value.isNotBlank()) {
                        transfers.add("Text data from ${apps.dropLast(1).lastOrNull() ?: "previous app"} → ${apps.last()}")
                    }
                }
            }

            return PlanSummary(
                apps = apps,
                totalSteps = actions.size,
                stepDescriptions = descriptions,
                dataTransfers = transfers.distinct(),
                isMultiApp = apps.size > 1,
            )
        }

        /**
         * Format a PlanSummary as a human-readable string for overlay display.
         */
        fun formatForDisplay(summary: PlanSummary): String {
            val sb = StringBuilder()

            if (summary.isMultiApp) {
                sb.appendLine("Multi-app task: ${summary.apps.joinToString(" → ")}")
            } else if (summary.apps.isNotEmpty()) {
                sb.appendLine("App: ${summary.apps.first()}")
            }

            sb.appendLine("Steps: ${summary.totalSteps}")

            if (summary.dataTransfers.isNotEmpty()) {
                sb.appendLine("Data shared:")
                summary.dataTransfers.forEach { sb.appendLine("  • $it") }
            }

            return sb.toString().trimEnd()
        }

        private fun describeAction(action: LLMAction): String {
            return when (action.actionType) {
                ActionType.TAP -> "Tap on ${action.targetText ?: action.targetId ?: "element"}"
                ActionType.TYPE -> "Type text"
                ActionType.SWIPE -> "Swipe ${action.value ?: "down"}"
                ActionType.LAUNCH -> "Open ${action.value ?: "app"}"
                ActionType.NAVIGATE -> "Navigate ${action.value ?: "back"}"
                ActionType.DONE -> "Complete"
                ActionType.ERROR -> "Error"
                ActionType.CONFIRM -> "Confirm action"
                ActionType.WAIT -> "Wait"
                ActionType.TOOL_CALL -> "Use tool"
            }
        }
    }
