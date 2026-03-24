package ai.neuron.character.prompt

import ai.neuron.character.model.EmotionState

/**
 * Extracts emotional tone from LLM response text using keyword-based heuristics.
 * Used to update the character's emotion after each LLM interaction.
 *
 * Priority order (highest to lowest):
 * 1. CONCERNED — apology/negative words
 * 2. THINKING — uncertainty/deliberation words
 * 3. EXCITED — exclamation marks + positive energy
 * 4. HAPPY — positive words
 * 5. NEUTRAL — no signal detected
 */
object ResponseEmotionExtractor {
    private val CONCERNED_PATTERNS =
        listOf(
            "sorry", "unfortunately", "apologize", "can't", "cannot", "unable",
            "failed", "error", "problem", "issue", "wrong",
        )

    private val THINKING_PATTERNS =
        listOf(
            "hmm", "let me think", "not sure", "perhaps", "maybe",
            "considering", "wondering", "it depends", "tricky",
        )

    private val HAPPY_PATTERNS =
        listOf(
            "great", "perfect", "excellent", "awesome", "wonderful",
            "fantastic", "well done", "good job", "nice", "working perfectly",
        )

    fun extract(text: String): EmotionState {
        if (text.isBlank()) return EmotionState.NEUTRAL

        val lower = text.lowercase()

        // Priority 1: Concerned (apology/error signals)
        if (CONCERNED_PATTERNS.any { lower.contains(it) }) {
            return EmotionState.CONCERNED
        }

        // Priority 2: Thinking (uncertainty signals)
        if (THINKING_PATTERNS.any { lower.contains(it) }) {
            return EmotionState.THINKING
        }

        // Priority 3: Excited (multiple exclamation marks + energy)
        val exclamationCount = text.count { it == '!' }
        if (exclamationCount >= 2) {
            return EmotionState.EXCITED
        }

        // Priority 4: Happy (positive words)
        if (HAPPY_PATTERNS.any { lower.contains(it) }) {
            return EmotionState.HAPPY
        }

        return EmotionState.NEUTRAL
    }
}
