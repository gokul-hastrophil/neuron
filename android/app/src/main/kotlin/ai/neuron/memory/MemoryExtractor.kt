package ai.neuron.memory

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.StepLog
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExtractor
    @Inject
    constructor(
        private val longTermMemory: LongTermMemory,
    ) {
        companion object {
            private const val TAG = "NeuronMemoryExtractor"
        }

        suspend fun extractFromCompletedTask(
            command: String,
            steps: List<StepLog>,
            totalDurationMs: Long,
        ) {
            if (steps.isEmpty()) return

            extractAppPreference(command, steps)
            extractWorkflow(command, steps, totalDurationMs)
        }

        private suspend fun extractAppPreference(
            command: String,
            steps: List<StepLog>,
        ) {
            // If the task launched an app, remember that as the user's preferred app for this type of command
            val launchStep = steps.firstOrNull { it.action.actionType == ActionType.LAUNCH && it.success }
            if (launchStep != null) {
                val packageName = launchStep.action.value ?: return
                val taskKeyword = extractTaskKeyword(command)
                longTermMemory.savePreference(
                    category = "app_preference",
                    key = taskKeyword,
                    value = packageName,
                )
                Log.d(TAG, "Saved app preference: '$taskKeyword' → $packageName")
            }
        }

        private suspend fun extractWorkflow(
            command: String,
            steps: List<StepLog>,
            totalDurationMs: Long,
        ) {
            // Save the successful action sequence as a cached workflow
            val launchStep = steps.firstOrNull { it.action.actionType == ActionType.LAUNCH && it.success }
            val packageName = launchStep?.action?.value ?: "unknown"
            val taskType = extractTaskKeyword(command)

            val actionSequence =
                steps
                    .filter { it.success && it.action.actionType != ActionType.DONE }
                    .map { mapOf("action" to it.action.actionType.name, "value" to (it.action.value ?: "")) }

            val sequenceJson = Json.encodeToString(actionSequence)

            longTermMemory.saveWorkflow(
                packageName = packageName,
                taskType = taskType,
                actionSequenceJson = sequenceJson,
                latencyMs = totalDurationMs,
                success = true,
            )
            Log.d(TAG, "Saved workflow: $packageName/$taskType (${steps.size} steps, ${totalDurationMs}ms)")
        }

        internal fun extractTaskKeyword(command: String): String {
            val lower = command.lowercase().trim()
            // Remove common prefixes
            val cleaned =
                lower
                    .removePrefix("please ")
                    .removePrefix("can you ")
                    .removePrefix("hey neuron ")
                    .removePrefix("neuron ")
                    .trim()
            // Take first 3 meaningful words as the task key
            return cleaned.split("\\s+".toRegex())
                .filter { it.length > 2 }
                .take(3)
                .joinToString("_")
                .ifEmpty { cleaned.take(30) }
        }
    }
