package ai.neuron.character.prompt

import ai.neuron.character.model.PersonalityProfile

/**
 * Merges character personality context with the existing brain system prompt.
 * Character context is prepended so the LLM adopts the persona before processing tasks.
 */
object CharacterSystemPrompt {
    /**
     * Merge character prompt with brain prompt.
     * Character context comes first, then the existing task instructions.
     *
     * @param characterPrompt Output from [PersonalityPromptBuilder.build], or null if no character
     * @param brainPrompt The existing system prompt from LLMRouter
     */
    fun merge(
        characterPrompt: String?,
        brainPrompt: String,
    ): String {
        if (characterPrompt.isNullOrBlank()) return brainPrompt
        return "$characterPrompt\n\n---\n\n$brainPrompt"
    }

    /**
     * Build a compact character prompt for T4 (on-device) models.
     * Minimal context to fit small model windows: just name and style.
     */
    fun buildCompact(profile: PersonalityProfile): String {
        return "You are ${profile.name}. Style: ${profile.speakingStyle.systemPromptDescription}."
    }
}
