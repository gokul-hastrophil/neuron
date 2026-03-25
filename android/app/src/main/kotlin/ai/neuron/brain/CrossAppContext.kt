package ai.neuron.brain

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists extracted data across app boundaries within a single multi-app task.
 * Stores key-value pairs from ScreenExtractor that survive app switches,
 * allowing the LLM to reference data from previous apps when planning
 * actions in the new app.
 *
 * PRIVACY: Never stores data from sensitive apps (checked by SensitivityGate).
 * Cleared after every task completion.
 */
@Singleton
class CrossAppContext
    @Inject
    constructor() {
        companion object {
            const val MAX_ENTRIES = 50
            const val MAX_VALUE_LENGTH = 2000
        }

        private val extractedValues = mutableMapOf<String, String>()
        private val appSequence = mutableListOf<String>()

        /**
         * Store an extracted value from the current screen.
         * Keys are namespaced by source app: "com.whatsapp:contact_name".
         */
        fun putValue(
            key: String,
            value: String,
        ) {
            if (extractedValues.size >= MAX_ENTRIES) return
            extractedValues[key] = value.take(MAX_VALUE_LENGTH)
        }

        fun getValue(key: String): String? = extractedValues[key]

        fun getAllValues(): Map<String, String> = extractedValues.toMap()

        /**
         * Record an app in the cross-app sequence.
         * Only adds if different from the last recorded app.
         */
        fun recordAppSwitch(packageName: String) {
            if (appSequence.lastOrNull() != packageName) {
                appSequence.add(packageName)
            }
        }

        fun getAppSequence(): List<String> = appSequence.toList()

        /**
         * Build a context summary string for inclusion in LLM prompts.
         * Returns null if no cross-app data exists.
         */
        fun buildPromptContext(): String? {
            if (extractedValues.isEmpty()) return null

            val sb = StringBuilder()
            sb.appendLine("[Cross-App Context]")
            sb.appendLine("Apps visited: ${appSequence.joinToString(" → ")}")
            sb.appendLine("Extracted data:")
            extractedValues.forEach { (key, value) ->
                sb.appendLine("  $key = $value")
            }
            return sb.toString()
        }

        fun clear() {
            extractedValues.clear()
            appSequence.clear()
        }

        fun isEmpty(): Boolean = extractedValues.isEmpty() && appSequence.isEmpty()
    }
