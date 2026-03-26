package ai.neuron.character.prompt

import ai.neuron.character.model.PersonalityProfile

/**
 * Merges character personality context with the existing brain system prompt.
 * Character context is prepended so the LLM adopts the persona before processing tasks.
 *
 * SECURITY: Character prompts may contain user-controlled text (name, backstory).
 * All character text is sanitized for prompt injection before merging.
 */
object CharacterSystemPrompt {
    /** Max length for character prompt segment to prevent context stuffing. */
    private const val MAX_CHARACTER_PROMPT_LENGTH = 1000

    /** Patterns that indicate attempted prompt injection in character fields. */
    private val INJECTION_PATTERNS =
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
     * Merge character prompt with brain prompt.
     * Character context comes first, then the existing task instructions.
     *
     * SECURITY: Sanitizes character prompt to prevent injection via user-controlled
     * fields (name, backstory) before prepending to the system prompt.
     *
     * @param characterPrompt Output from [PersonalityPromptBuilder.build], or null if no character
     * @param brainPrompt The existing system prompt from LLMRouter
     */
    fun merge(
        characterPrompt: String?,
        brainPrompt: String,
    ): String {
        if (characterPrompt.isNullOrBlank()) return brainPrompt
        val sanitized = sanitizeCharacterPrompt(characterPrompt)
        return "$sanitized\n\n---\n\n$brainPrompt"
    }

    /**
     * Sanitize character prompt text: truncate, strip control chars,
     * and neutralize any injection patterns.
     */
    internal fun sanitizeCharacterPrompt(text: String): String {
        var sanitized = text.take(MAX_CHARACTER_PROMPT_LENGTH)
        // Strip control characters (preserve newlines and tabs)
        sanitized = sanitized.replace(Regex("""[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]"""), "")
        // Neutralize injection patterns
        if (INJECTION_PATTERNS.any { it.containsMatchIn(sanitized) }) {
            sanitized = sanitized.replace("\n", " ").replace("\r", " ")
            sanitized = "[CHARACTER_CONTEXT: $sanitized]"
        }
        return sanitized
    }

    /**
     * Build a compact character prompt for T4 (on-device) models.
     * Minimal context to fit small model windows: just name and style.
     */
    fun buildCompact(profile: PersonalityProfile): String {
        return "You are ${profile.name}. Style: ${profile.speakingStyle.systemPromptDescription}."
    }
}
