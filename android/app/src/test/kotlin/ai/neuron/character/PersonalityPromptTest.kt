package ai.neuron.character

import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.PersonalityProfile
import ai.neuron.character.model.SpeakingStyle
import ai.neuron.character.prompt.CharacterSystemPrompt
import ai.neuron.character.prompt.PersonalityPromptBuilder
import ai.neuron.character.prompt.ResponseEmotionExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PersonalityPromptTest {
    // ─── PersonalityPromptBuilder ───

    @Nested
    @DisplayName("PersonalityPromptBuilder")
    inner class PromptBuilder {
        @Test
        fun should_includeName_when_profileProvided() {
            val profile = PersonalityProfile.defaultAnimeGirl()
            val prompt = PersonalityPromptBuilder.build(profile, EmotionState.NEUTRAL, 10)
            assertTrue(prompt.contains(profile.name), "Should contain character name '${profile.name}'")
        }

        @Test
        fun should_includeSpeakingStyle_when_profileProvided() {
            val profile =
                PersonalityProfile(
                    name = "TestBot",
                    speakingStyle = SpeakingStyle.GEN_Z,
                )
            val prompt = PersonalityPromptBuilder.build(profile, EmotionState.NEUTRAL, 0)
            assertTrue(
                prompt.contains(SpeakingStyle.GEN_Z.systemPromptDescription),
                "Should contain speaking style description",
            )
        }

        @Test
        fun should_includeEmotion_when_notNeutral() {
            val profile = PersonalityProfile.defaultAnimeGirl()
            val prompt = PersonalityPromptBuilder.build(profile, EmotionState.HAPPY, 5)
            assertTrue(prompt.contains("happy", ignoreCase = true), "Should mention happy emotion")
        }

        @Test
        fun should_includeInteractionCount_when_nonZero() {
            val profile = PersonalityProfile.defaultAnimeGirl()
            val prompt = PersonalityPromptBuilder.build(profile, EmotionState.NEUTRAL, 42)
            assertTrue(prompt.contains("42"), "Should mention interaction count")
        }

        @Test
        fun should_includeTraitDescriptions_when_highValues() {
            val profile =
                PersonalityProfile(
                    name = "EmpathBot",
                    empathy = 0.95f,
                    humor = 0.1f,
                )
            val prompt = PersonalityPromptBuilder.build(profile, EmotionState.NEUTRAL, 0)
            assertTrue(prompt.contains("empathetic", ignoreCase = true) || prompt.contains("empathy", ignoreCase = true))
        }

        @Test
        fun should_beNonEmpty_when_defaultProfile() {
            val prompt =
                PersonalityPromptBuilder.build(
                    PersonalityProfile(),
                    EmotionState.NEUTRAL,
                    0,
                )
            assertTrue(prompt.isNotBlank(), "Default profile should produce non-empty prompt")
        }
    }

    // ─── CharacterSystemPrompt ───

    @Nested
    @DisplayName("CharacterSystemPrompt")
    inner class SystemPrompt {
        @Test
        fun should_prependCharacterContext_when_merged() {
            val characterPrompt = "You are Aiko, a playful companion."
            val brainPrompt = "You are Neuron, an AI agent."
            val merged = CharacterSystemPrompt.merge(characterPrompt, brainPrompt)
            assertTrue(
                merged.indexOf(characterPrompt) < merged.indexOf(brainPrompt),
                "Character context should come before brain prompt",
            )
        }

        @Test
        fun should_containBoth_when_merged() {
            val characterPrompt = "CHARACTER_CONTEXT"
            val brainPrompt = "BRAIN_CONTEXT"
            val merged = CharacterSystemPrompt.merge(characterPrompt, brainPrompt)
            assertTrue(merged.contains("CHARACTER_CONTEXT"))
            assertTrue(merged.contains("BRAIN_CONTEXT"))
        }

        @Test
        fun should_returnBrainOnly_when_noCharacter() {
            val merged = CharacterSystemPrompt.merge(null, "BRAIN_CONTEXT")
            assertEquals("BRAIN_CONTEXT", merged)
        }

        @Test
        fun should_returnBrainOnly_when_emptyCharacter() {
            val merged = CharacterSystemPrompt.merge("", "BRAIN_CONTEXT")
            assertEquals("BRAIN_CONTEXT", merged)
        }

        @Test
        fun should_buildCompact_when_t4Mode() {
            val profile = PersonalityProfile.defaultAnimeGirl()
            val compact = CharacterSystemPrompt.buildCompact(profile)
            assertTrue(compact.contains(profile.name), "Compact prompt should have name")
            assertTrue(compact.length < 200, "Compact prompt should be short for T4 context, was ${compact.length}")
        }
    }

    // ─── ResponseEmotionExtractor ───

    @Nested
    @DisplayName("ResponseEmotionExtractor")
    inner class EmotionExtractor {
        @Test
        fun should_detectExcited_when_exclamationMarks() {
            val emotion = ResponseEmotionExtractor.extract("That's amazing!! I love it!")
            assertEquals(EmotionState.EXCITED, emotion)
        }

        @Test
        fun should_detectConcerned_when_apologeticWords() {
            val emotion = ResponseEmotionExtractor.extract("I'm sorry, unfortunately that didn't work.")
            assertEquals(EmotionState.CONCERNED, emotion)
        }

        @Test
        fun should_detectThinking_when_uncertainWords() {
            val emotion = ResponseEmotionExtractor.extract("Hmm, let me think about that... I'm not sure yet.")
            assertEquals(EmotionState.THINKING, emotion)
        }

        @Test
        fun should_detectHappy_when_positiveWords() {
            val emotion = ResponseEmotionExtractor.extract("Great job! Everything is working perfectly.")
            assertEquals(EmotionState.HAPPY, emotion)
        }

        @Test
        fun should_returnNeutral_when_plainText() {
            val emotion = ResponseEmotionExtractor.extract("The file has been saved.")
            assertEquals(EmotionState.NEUTRAL, emotion)
        }

        @Test
        fun should_returnNeutral_when_emptyText() {
            val emotion = ResponseEmotionExtractor.extract("")
            assertEquals(EmotionState.NEUTRAL, emotion)
        }

        @Test
        fun should_prioritizeStrongestSignal_when_mixedSignals() {
            // Apology words are stronger signal than exclamation marks
            val emotion = ResponseEmotionExtractor.extract("Sorry! I unfortunately couldn't do that!")
            assertEquals(EmotionState.CONCERNED, emotion)
        }
    }
}
