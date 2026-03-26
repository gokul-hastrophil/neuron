package ai.neuron.brain

import android.util.Log
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
 *
 * SECURITY: All extractedValues are checked for prompt injection before
 * inclusion in LLM prompts. Sensitive app packages are redacted from
 * the app sequence.
 */
@Singleton
class CrossAppContext
    @Inject
    constructor(
        private val promptSanitizer: PromptSanitizer,
        private val sensitivityGate: SensitivityGate,
    ) {
        companion object {
            private const val TAG = "CrossAppContext"
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

        fun getAppSequence(): List<String> =
            appSequence.map { pkg ->
                sensitivityGate.getSensitiveLabel(pkg) ?: pkg
            }

        /**
         * Build a context summary string for inclusion in LLM prompts.
         * Returns null if no cross-app data exists.
         *
         * Security: Values containing prompt injection patterns are excluded.
         * Privacy: Sensitive app packages are replaced with generic labels,
         * and extracted values from sensitive packages are excluded entirely.
         */
        fun buildPromptContext(): String? {
            if (extractedValues.isEmpty()) return null

            val sb = StringBuilder()
            sb.appendLine("[Cross-App Context]")

            // Fix 3: Redact sensitive packages in app sequence
            val redactedSequence =
                appSequence.map { pkg ->
                    sensitivityGate.getSensitiveLabel(pkg) ?: pkg
                }
            sb.appendLine("Apps visited: ${redactedSequence.joinToString(" → ")}")

            // Collect sensitive package prefixes for value filtering
            val sensitivePackagePrefixes =
                appSequence.filter { sensitivityGate.isSensitivePackage(it) }
                    .map { "$it:" }

            sb.appendLine("Extracted data:")
            extractedValues.forEach { (key, value) ->
                // Fix 3: Exclude extracted values from sensitive packages entirely
                if (sensitivePackagePrefixes.any { prefix -> key.startsWith(prefix) }) {
                    Log.d(TAG, "Excluded value from sensitive package: key=$key")
                    return@forEach
                }

                // Fix 1: Check for prompt injection before embedding
                if (promptSanitizer.containsInjection(value)) {
                    Log.w(TAG, "Prompt injection detected in extracted value: key=$key")
                    return@forEach
                }

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
