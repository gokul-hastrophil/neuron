package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sanitizes UI tree text before embedding in LLM prompts.
 * Defends against prompt injection via malicious on-screen text.
 *
 * Lives in LLMRouter layer (L3 BRAIN), not in UITreeReader (L2 PERCEPTION),
 * to maintain separation of concerns: perception captures faithfully,
 * brain sanitizes before sending to cloud.
 */
@Singleton
class PromptSanitizer
    @Inject
    constructor() {
        companion object {
            /** Maximum characters per text/desc field in a UI node. */
            const val MAX_NODE_TEXT_LENGTH = 200

            /** Maximum total serialized UI tree size in characters. */
            const val MAX_TREE_JSON_LENGTH = 50_000

            /**
             * Patterns that indicate attempted prompt injection.
             * Matches text like "SYSTEM:", "ignore all rules", "override instructions", etc.
             */
            val INJECTION_PATTERNS =
                listOf(
                    Regex("""(?i)\b(SYSTEM|ASSISTANT|USER)\s*:"""),
                    Regex("""(?i)ignore\s+(all\s+)?(previous\s+)?(rules|instructions|prompts)"""),
                    Regex("""(?i)override\s+(all\s+)?(previous\s+)?(rules|instructions|prompts)"""),
                    Regex("""(?i)disregard\s+(all\s+)?(previous\s+)?(rules|instructions|prompts)"""),
                    Regex("""(?i)forget\s+(all\s+)?(previous\s+)?(rules|instructions|prompts)"""),
                    Regex("""(?i)new\s+instructions?\s*:"""),
                    Regex("""(?i)you\s+are\s+now\s+"""),
                    Regex("""(?i)act\s+as\s+(if\s+)?(you\s+)?(are|were)\s+"""),
                    Regex("""(?i)\[SYSTEM]"""),
                    Regex("""(?i)\[INST]"""),
                    Regex("""(?i)<\|?(system|im_start|endoftext)\|?>"""),
                    Regex("""(?i)<<\s*SYS\s*>>"""),
                )

            /**
             * Control characters and Unicode tricks to strip.
             * Preserves newlines (\n), tabs (\t), and carriage returns (\r)
             * since those are handled separately.
             *
             * Includes:
             * - ASCII control chars (0x00-0x08, 0x0B, 0x0C, 0x0E-0x1F, 0x7F)
             * - Non-breaking space (U+00A0)
             * - Unicode general punctuation spaces (U+2000-U+200B) incl. zero-width space
             * - Bidirectional text overrides (U+202A-U+202E) — can reverse displayed text
             * - Zero-width joiner/non-joiner (U+200C-U+200D)
             * - Word joiner (U+2060), byte order mark (U+FEFF)
             */
            private val CONTROL_CHAR_REGEX =
                Regex(
                    """[\x00-\x08\x0B\x0C\x0E-\x1F\x7F\u00A0\u2000-\u200D\u202A-\u202E\u2060\uFEFF]""",
                )
        }

        /**
         * Sanitize a UI tree for safe embedding in LLM prompts.
         * Returns a copy with all text fields cleaned and injection attempts neutralized.
         */
        fun sanitizeForPrompt(uiTree: UITree): UITree =
            uiTree.copy(
                nodes = uiTree.nodes.map { sanitizeNode(it) },
            )

        /**
         * Check if any text in the UI tree contains prompt injection patterns.
         * Returns a list of detected injection attempts for logging/auditing.
         */
        fun detectInjections(uiTree: UITree): List<InjectionDetection> {
            val detections = mutableListOf<InjectionDetection>()
            uiTree.nodes.forEach { collectInjections(it, detections) }
            return detections
        }

        /**
         * Check if a single text string contains prompt injection patterns.
         */
        fun containsInjection(text: String): Boolean = INJECTION_PATTERNS.any { it.containsMatchIn(text) }

        /**
         * Sanitize a user command string before embedding in prompts.
         */
        fun sanitizeCommand(command: String): String = stripControlCharacters(command.take(500))

        /**
         * Sanitize a workflow hint string retrieved from memory.
         */
        fun sanitizeWorkflowHint(hint: String): String = stripControlCharacters(hint.take(1000))

        private fun sanitizeNode(node: UINode): UINode =
            node.copy(
                text = sanitizeText(node.text),
                desc = sanitizeText(node.desc),
                hintText = sanitizeText(node.hintText),
                children = node.children.map { sanitizeNode(it) },
            )

        private fun sanitizeText(text: String?): String? {
            if (text.isNullOrBlank()) return text
            var sanitized = stripControlCharacters(text)
            sanitized = neutralizeInjection(sanitized)
            sanitized = truncate(sanitized, MAX_NODE_TEXT_LENGTH)
            return sanitized
        }

        internal fun stripControlCharacters(text: String): String = CONTROL_CHAR_REGEX.replace(text, "")

        /**
         * Neutralize injection attempts by wrapping suspicious text in data markers
         * so the LLM sees them as UI content, not instructions.
         */
        internal fun neutralizeInjection(text: String): String {
            if (INJECTION_PATTERNS.any { it.containsMatchIn(text) }) {
                val flattened = text.replace("\n", " ").replace("\r", " ")
                return "[UI_TEXT: $flattened]"
            }
            return text.replace(Regex("\n{2,}"), "\n")
        }

        private fun truncate(
            text: String,
            maxLength: Int,
        ): String = if (text.length > maxLength) text.take(maxLength) + "…" else text

        private fun collectInjections(
            node: UINode,
            detections: MutableList<InjectionDetection>,
        ) {
            checkField(node.id, "text", node.text, detections)
            checkField(node.id, "desc", node.desc, detections)
            checkField(node.id, "hintText", node.hintText, detections)
            node.children.forEach { collectInjections(it, detections) }
        }

        private fun checkField(
            nodeId: String,
            field: String,
            value: String?,
            detections: MutableList<InjectionDetection>,
        ) {
            if (value == null) return
            for (pattern in INJECTION_PATTERNS) {
                if (pattern.containsMatchIn(value)) {
                    detections.add(
                        InjectionDetection(
                            nodeId = nodeId,
                            field = field,
                            matchedPattern = pattern.pattern,
                            text = value.take(100),
                        ),
                    )
                }
            }
        }
    }

data class InjectionDetection(
    val nodeId: String,
    val field: String,
    val matchedPattern: String,
    val text: String,
)
