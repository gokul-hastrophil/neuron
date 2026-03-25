package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts visible text/data from the current UI tree before an app switch.
 * Stores results into CrossAppContext so the LLM has data from the previous
 * app when planning actions in the next one.
 *
 * Skips password fields and sensitive nodes entirely.
 */
@Singleton
class ScreenExtractor
    @Inject
    constructor(
        private val sensitivityGate: SensitivityGate,
    ) {
        companion object {
            const val MAX_EXTRACTED_TEXTS = 30
            private const val MIN_TEXT_LENGTH = 2
        }

        /**
         * Extract visible text content from the UI tree and store in context.
         * Returns the number of values extracted.
         *
         * @param uiTree Current screen's UI tree
         * @param context CrossAppContext to store values in
         * @return number of values extracted, or 0 if screen is sensitive
         */
        fun extractAndStore(
            uiTree: UITree,
            context: CrossAppContext,
        ): Int {
            // PRIVACY: never extract from sensitive screens
            if (sensitivityGate.isSensitive(uiTree)) return 0

            val packageName = uiTree.packageName
            context.recordAppSwitch(packageName)

            val texts = mutableListOf<Pair<String, String>>()
            uiTree.nodes.forEach { node ->
                collectTexts(node, packageName, texts)
            }

            val limited = texts.take(MAX_EXTRACTED_TEXTS)
            limited.forEach { (key, value) ->
                context.putValue(key, value)
            }
            return limited.size
        }

        private fun collectTexts(
            node: UINode,
            packageName: String,
            results: MutableList<Pair<String, String>>,
        ) {
            // Skip password fields entirely
            if (node.password) return

            // Extract meaningful text content
            node.text?.let { text ->
                if (text.length >= MIN_TEXT_LENGTH && results.size < MAX_EXTRACTED_TEXTS) {
                    val key = buildKey(packageName, node.id, "text", results.size)
                    results.add(key to text)
                }
            }

            node.desc?.let { desc ->
                if (desc.length >= MIN_TEXT_LENGTH && results.size < MAX_EXTRACTED_TEXTS) {
                    val key = buildKey(packageName, node.id, "desc", results.size)
                    results.add(key to desc)
                }
            }

            // Recurse into children
            node.children.forEach { child ->
                collectTexts(child, packageName, results)
            }
        }

        private fun buildKey(
            packageName: String,
            nodeId: String,
            field: String,
            index: Int,
        ): String {
            val safeId = nodeId.ifEmpty { "node_$index" }
            return "$packageName:$safeId:$field"
        }
    }
