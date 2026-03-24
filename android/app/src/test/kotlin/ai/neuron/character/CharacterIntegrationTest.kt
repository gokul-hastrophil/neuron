package ai.neuron.character

import ai.neuron.character.dao.CharacterDao
import ai.neuron.character.model.CharacterState
import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.PersonalityProfile
import ai.neuron.character.prompt.CharacterSystemPrompt
import ai.neuron.character.prompt.PersonalityPromptBuilder
import ai.neuron.character.prompt.ResponseEmotionExtractor
import ai.neuron.character.renderer.CharacterRendererFactory
import ai.neuron.character.renderer.ComposeCharacterRenderer
import ai.neuron.character.voice.SpeakingState
import ai.neuron.character.voice.SsmlBuilder
import ai.neuron.character.voice.VoiceSynthesizer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Integration tests verifying the end-to-end character system flow.
 * These test the full pipeline without Android framework dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterIntegrationTest {
    private fun createDao(existing: CharacterState? = null): CharacterDao {
        val dao = mockk<CharacterDao>(relaxed = true)
        coEvery { dao.getActiveCharacter() } returns existing
        return dao
    }

    // ─── Full Pipeline ───

    @Nested
    @DisplayName("Full pipeline")
    inner class FullPipeline {
        @Test
        fun should_produceCharacterPrompt_when_fullFlowExecuted() =
            runTest {
                // 1. Create engine with existing character
                val profile = PersonalityProfile.defaultAnimeGirl()
                val existing =
                    CharacterState(
                        selectedType = "ANIME_GIRL",
                        name = "Aiko",
                        personalityJson = profile.toJson(),
                        totalInteractions = 42,
                    )
                val engine = CharacterEngine(createDao(existing), this)
                engine.initialize()
                advanceTimeBy(200)

                // 2. Build character context for LLM
                val personality = engine.getPersonality()
                assertNotNull(personality)
                val characterPrompt =
                    PersonalityPromptBuilder.build(
                        personality!!,
                        engine.emotionState.value,
                        existing.totalInteractions,
                    )

                // 3. Merge with brain prompt
                val brainPrompt = "You are Neuron, an AI agent."
                val merged = CharacterSystemPrompt.merge(characterPrompt, brainPrompt)

                // Verify pipeline output
                assertTrue(merged.contains("Aiko"), "Merged prompt should contain character name")
                assertTrue(merged.contains("42"), "Merged prompt should contain interaction count")
                assertTrue(merged.contains("Neuron, an AI agent"), "Merged prompt should contain brain context")
                assertTrue(
                    merged.indexOf("Aiko") < merged.indexOf("Neuron, an AI agent"),
                    "Character context should precede brain context",
                )
            }

        @Test
        fun should_extractEmotionAndUpdateEngine_when_llmResponds() =
            runTest {
                val engine = CharacterEngine(createDao(CharacterState(name = "Aiko")), this)
                engine.initialize()
                advanceTimeBy(200)

                // Simulate LLM response
                val llmResponse = "That's amazing!! I found the perfect restaurant for you!"
                val detectedEmotion = ResponseEmotionExtractor.extract(llmResponse)

                assertEquals(EmotionState.EXCITED, detectedEmotion)

                // Update engine with detected emotion
                engine.setEmotion(detectedEmotion, "LLM response tone")
                advanceTimeBy(200)

                assertEquals(EmotionState.EXCITED, engine.emotionState.value)
            }

        @Test
        fun should_generateEmotionalSsml_when_ttsFlowExecuted() =
            runTest {
                val engine = CharacterEngine(createDao(CharacterState(name = "Aiko")), this)
                engine.initialize()
                advanceTimeBy(200)

                // Set emotion from LLM response
                engine.setEmotion(EmotionState.HAPPY, "good news")
                advanceTimeBy(200)

                // Generate SSML for TTS
                val ssml = SsmlBuilder.build("I found it!", engine.emotionState.value)
                assertTrue(ssml.contains("<prosody"), "Happy emotion should produce prosody tags")
                assertTrue(ssml.contains("pitch="), "Happy should shift pitch")
            }

        @Test
        fun should_syncRendererEmotion_when_emotionChanges() =
            runTest {
                val engine = CharacterEngine(createDao(), this)

                // Create renderer for character type
                val renderer = CharacterRendererFactory.create(CharacterType.ABSTRACT_CUTE)
                assertTrue(renderer is ComposeCharacterRenderer)

                // Set emotion on engine
                engine.setEmotion(EmotionState.PLAYFUL, "joke")
                advanceTimeBy(200)

                // Sync to renderer
                renderer.setEmotion(engine.emotionState.value)
                assertEquals(EmotionState.PLAYFUL, renderer.currentEmotion)
            }

        @Test
        fun should_trackSpeakingState_when_voiceFlowExecuted() {
            val synth = VoiceSynthesizer()

            // Start speaking
            synth.onSpeakStart("utt-1")
            assertEquals(SpeakingState.SPEAKING, synth.speakingState.value)

            // Done speaking
            synth.onSpeakDone("utt-1")
            assertEquals(SpeakingState.IDLE, synth.speakingState.value)
        }
    }

    // ─── Error Handling ───

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {
        @Test
        fun should_fallbackToComposeRenderer_when_live2dUnavailable() {
            val renderer = CharacterRendererFactory.create(CharacterType.ANIME_GIRL)
            // Live2D SDK not bundled — should get Compose fallback
            assertNotNull(renderer)
            assertTrue(
                renderer is ComposeCharacterRenderer,
                "Should fall back to ComposeCharacterRenderer when Live2D unavailable",
            )
        }

        @Test
        fun should_useDefaultPersonality_when_jsonCorrupted() =
            runTest {
                val existing =
                    CharacterState(
                        name = "Aiko",
                        selectedType = "ANIME_GIRL",
                        personalityJson = "{{corrupted json!!",
                    )
                val engine = CharacterEngine(createDao(existing), this)
                engine.initialize()
                advanceTimeBy(200)

                val personality = engine.getPersonality()
                assertNotNull(personality)
                // Should fall back to defaults with the name preserved
                assertEquals("Aiko", personality!!.name)
            }

        @Test
        fun should_handleEmptyResponse_when_emotionExtracted() {
            val emotion = ResponseEmotionExtractor.extract("")
            assertEquals(EmotionState.NEUTRAL, emotion)
        }

        @Test
        fun should_handleNullCharacter_when_promptBuilt() {
            val merged = CharacterSystemPrompt.merge(null, "Brain prompt")
            assertEquals("Brain prompt", merged)
        }

        @Test
        fun should_notCrashRenderer_when_disposedAndUsed() {
            val renderer = CharacterRendererFactory.create(CharacterType.ABSTRACT_PIXEL)
            renderer.dispose()
            // Operations after dispose should not crash
            renderer.setEmotion(EmotionState.HAPPY)
            renderer.setIdleAnimation(true)
        }
    }

    // ─── T4 Compact Prompt ───

    @Nested
    @DisplayName("T4 compact prompt")
    inner class T4Compact {
        @Test
        fun should_beShort_when_compactPromptBuilt() {
            val profile = PersonalityProfile.defaultAnimeGirl()
            val compact = CharacterSystemPrompt.buildCompact(profile)
            assertTrue(compact.length < 200, "Compact prompt should be < 200 chars, was ${compact.length}")
            assertTrue(compact.contains(profile.name))
        }
    }
}
