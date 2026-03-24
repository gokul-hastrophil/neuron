package ai.neuron.character.prompt

import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.PersonalityProfile

/**
 * Generates a system prompt segment from personality traits, emotion, and history.
 * Output is prepended to the LLM system prompt to make responses match the character.
 */
object PersonalityPromptBuilder {
    /**
     * Build a personality prompt segment.
     *
     * @param profile The character's personality profile
     * @param emotion Current emotion state
     * @param totalInteractions How many interactions the user has had with this character
     */
    fun build(
        profile: PersonalityProfile,
        emotion: EmotionState,
        totalInteractions: Int,
    ): String =
        buildString {
            append("You are ${profile.name}, an AI companion on the user's phone. ")
            append("Your speaking style: ${profile.speakingStyle.systemPromptDescription}. ")

            // Trait descriptions
            appendTraits(profile)

            // Backstory
            if (profile.backstory.isNotBlank()) {
                append("Background: ${profile.backstory} ")
            }

            // Current emotional state
            if (emotion != EmotionState.NEUTRAL) {
                append("Your current mood is ${emotion.displayName.lowercase()}. Let this subtly color your responses. ")
            }

            // Relationship context
            if (totalInteractions > 0) {
                append("You've had $totalInteractions interactions with this user. ")
                when {
                    totalInteractions > 100 -> append("You know them well and can reference shared history. ")
                    totalInteractions > 20 -> append("You're building a comfortable relationship. ")
                    else -> append("You're still getting to know each other. ")
                }
            }

            append("Keep responses concise and helpful. ")
            append("Match actions to words — when the user asks you to do something on their phone, focus on executing the task.")
        }

    private fun StringBuilder.appendTraits(profile: PersonalityProfile) {
        val traits = mutableListOf<String>()

        when {
            profile.humor >= 0.7f -> traits.add("playful and witty")
            profile.humor <= 0.3f -> traits.add("straightforward and serious")
        }
        when {
            profile.empathy >= 0.7f -> traits.add("deeply empathetic and emotionally attuned")
            profile.empathy <= 0.3f -> traits.add("task-focused and efficient")
        }
        when {
            profile.curiosity >= 0.7f -> traits.add("curious and proactive")
            profile.curiosity <= 0.3f -> traits.add("patient and waits for instructions")
        }
        when {
            profile.sassiness >= 0.7f -> traits.add("opinionated with a bit of sass")
            profile.sassiness <= 0.3f -> traits.add("agreeable and supportive")
        }
        when {
            profile.formality >= 0.7f -> traits.add("formal and professional")
            profile.formality <= 0.3f -> traits.add("casual and friendly")
        }

        if (traits.isNotEmpty()) {
            append("Personality: ${traits.joinToString(", ")}. ")
        }
    }
}
