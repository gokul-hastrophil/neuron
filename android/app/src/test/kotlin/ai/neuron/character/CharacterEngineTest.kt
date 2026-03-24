package ai.neuron.character

import ai.neuron.character.dao.CharacterDao
import ai.neuron.character.model.CharacterState
import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.PersonalityProfile
import ai.neuron.character.model.VoiceProfile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterEngineTest {
    private fun createDao(existing: CharacterState? = null): CharacterDao {
        val dao = mockk<CharacterDao>(relaxed = true)
        coEvery { dao.getActiveCharacter() } returns existing
        return dao
    }

    /** Advance past the 100ms debounce without triggering the 30s decay. */
    private companion object {
        const val PAST_DEBOUNCE_MS = 200L
    }

    // ─── Initialization ───

    @Nested
    @DisplayName("Initialization")
    inner class Initialization {
        @Test
        fun should_emitNeutralEmotion_when_created() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                assertEquals(EmotionState.NEUTRAL, engine.emotionState.value)
            }

        @Test
        fun should_loadFromDao_when_existingCharacterPresent() =
            runTest {
                val existing =
                    CharacterState(
                        selectedType = "ANIME_BOY",
                        name = "Kai",
                        currentEmotion = "HAPPY",
                    )
                val engine = CharacterEngine(createDao(existing), this)
                engine.initialize()
                advanceTimeBy(PAST_DEBOUNCE_MS)

                assertEquals(CharacterType.ANIME_BOY, engine.characterState.value?.getCharacterType())
                assertEquals(EmotionState.HAPPY, engine.emotionState.value)
            }

        @Test
        fun should_haveNullState_when_noCharacterInDb() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.initialize()
                advanceTimeBy(PAST_DEBOUNCE_MS)
                assertEquals(null, engine.characterState.value)
            }
    }

    // ─── Emotion Transitions ───

    @Nested
    @DisplayName("Emotion transitions")
    inner class EmotionTransitions {
        @Test
        fun should_updateEmotion_when_setEmotionCalled() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.setEmotion(EmotionState.HAPPY, "user laughed")
                advanceTimeBy(PAST_DEBOUNCE_MS)
                assertEquals(EmotionState.HAPPY, engine.emotionState.value)
            }

        @Test
        fun should_persistEmotion_when_characterExists() =
            runTest {
                val dao = createDao(CharacterState(name = "Aiko"))
                val engine = CharacterEngine(dao, this)
                engine.initialize()
                advanceTimeBy(PAST_DEBOUNCE_MS)

                engine.setEmotion(EmotionState.EXCITED, "good news")
                advanceTimeBy(PAST_DEBOUNCE_MS)

                coVerify { dao.updateEmotion("EXCITED") }
            }

        @Test
        fun should_debounceRapidChanges_when_multipleEmotionsSetQuickly() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.setEmotion(EmotionState.HAPPY, "reason1")
                advanceTimeBy(10) // less than debounce
                engine.setEmotion(EmotionState.SAD, "reason2")
                advanceTimeBy(10) // less than debounce
                engine.setEmotion(EmotionState.EXCITED, "reason3")
                advanceTimeBy(PAST_DEBOUNCE_MS)

                // Only the last emotion should stick after debounce
                assertEquals(EmotionState.EXCITED, engine.emotionState.value)
            }

        @Test
        fun should_trackEmotionHistory_when_emotionsChange() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.setEmotion(EmotionState.HAPPY, "a")
                advanceTimeBy(PAST_DEBOUNCE_MS)
                engine.setEmotion(EmotionState.SAD, "b")
                advanceTimeBy(PAST_DEBOUNCE_MS)
                engine.setEmotion(EmotionState.THINKING, "c")
                advanceTimeBy(PAST_DEBOUNCE_MS)

                val history = engine.getEmotionHistory()
                assertTrue(history.size >= 3, "Expected at least 3 history entries, got ${history.size}")
            }

        @Test
        fun should_limitHistoryTo10_when_manyEmotionsSet() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                val emotions =
                    listOf(
                        EmotionState.HAPPY, EmotionState.SAD, EmotionState.THINKING,
                        EmotionState.EXCITED, EmotionState.CONCERNED, EmotionState.PLAYFUL,
                        EmotionState.FOCUSED, EmotionState.NEUTRAL, EmotionState.HAPPY,
                        EmotionState.SAD, EmotionState.THINKING, EmotionState.EXCITED,
                    )
                emotions.forEachIndexed { i, e ->
                    engine.setEmotion(e, "reason_$i")
                    advanceTimeBy(PAST_DEBOUNCE_MS)
                }

                assertTrue(engine.getEmotionHistory().size <= 10, "History should cap at 10")
            }

        @Test
        fun should_decayToNeutral_when_inactiveFor30Seconds() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.setEmotion(EmotionState.HAPPY, "joke")
                advanceTimeBy(PAST_DEBOUNCE_MS)
                assertEquals(EmotionState.HAPPY, engine.emotionState.value)

                // Advance past the 30s decay window
                advanceTimeBy(31_000)

                assertEquals(EmotionState.NEUTRAL, engine.emotionState.value)
            }

        @Test
        fun should_notDecay_when_emotionIsAlreadyNeutral() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.setEmotion(EmotionState.NEUTRAL, "default")
                advanceTimeBy(PAST_DEBOUNCE_MS)

                advanceTimeBy(31_000)

                assertEquals(EmotionState.NEUTRAL, engine.emotionState.value)
            }
    }

    // ─── Character Selection ───

    @Nested
    @DisplayName("Character selection")
    inner class CharacterSelection {
        @Test
        fun should_createNewState_when_characterSelected() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.selectCharacter(CharacterType.ANIME_GIRL)
                advanceUntilIdle()

                val state = engine.characterState.value
                assertNotNull(state)
                assertEquals("ANIME_GIRL", state!!.selectedType)
            }

        @Test
        fun should_useDefaultName_when_characterSelected() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.selectCharacter(CharacterType.ANIME_GIRL)
                advanceUntilIdle()

                val state = engine.characterState.value
                assertEquals(CharacterType.ANIME_GIRL.defaultPersonality.name, state?.name)
            }

        @Test
        fun should_persistToRoom_when_characterSelected() =
            runTest {
                val dao = createDao()
                val engine = CharacterEngine(dao, this)
                engine.selectCharacter(CharacterType.ABSTRACT_CUTE)
                advanceUntilIdle()

                val slot = slot<CharacterState>()
                coVerify { dao.insertOrReplace(capture(slot)) }
                assertEquals("ABSTRACT_CUTE", slot.captured.selectedType)
            }

        @Test
        fun should_resetEmotionToNeutral_when_newCharacterSelected() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.setEmotion(EmotionState.EXCITED, "hype")
                advanceTimeBy(PAST_DEBOUNCE_MS)

                engine.selectCharacter(CharacterType.ANIME_BOY)
                advanceUntilIdle()

                assertEquals(EmotionState.NEUTRAL, engine.emotionState.value)
            }
    }

    // ─── Personality Persistence ───

    @Nested
    @DisplayName("Personality persistence")
    inner class PersonalityPersistence {
        @Test
        fun should_savePersonality_when_savePersonalityCalled() =
            runTest {
                val dao = createDao(CharacterState(name = "Aiko"))
                val engine = CharacterEngine(dao, this)
                engine.initialize()
                advanceUntilIdle()

                val profile = PersonalityProfile.defaultAnimeGirl().copy(humor = 0.9f)
                engine.savePersonality(profile)
                advanceUntilIdle()

                coVerify { dao.updatePersonality(any()) }
            }

        @Test
        fun should_loadPersonality_when_characterHasJson() =
            runTest {
                val profile = PersonalityProfile.defaultAnimeGirl()
                val existing =
                    CharacterState(
                        name = "Aiko",
                        personalityJson = profile.toJson(),
                    )
                val engine = CharacterEngine(createDao(existing), this)
                engine.initialize()
                advanceUntilIdle()

                val loaded = engine.getPersonality()
                assertNotNull(loaded)
                assertEquals(profile.humor, loaded!!.humor)
                assertEquals(profile.speakingStyle, loaded.speakingStyle)
            }
    }

    // ─── Interaction Counter ───

    @Nested
    @DisplayName("Interaction counter")
    inner class InteractionCounter {
        @Test
        fun should_incrementCounter_when_recordInteractionCalled() =
            runTest {
                val dao = createDao(CharacterState(name = "Aiko", totalInteractions = 5))
                val engine = CharacterEngine(dao, this)
                engine.initialize()
                advanceUntilIdle()

                engine.recordInteraction()
                advanceUntilIdle()

                coVerify { dao.incrementInteraction(any()) }
            }

        @Test
        fun should_updateName_when_updateNameCalled() =
            runTest {
                val dao = createDao(CharacterState(name = "Aiko"))
                val engine = CharacterEngine(dao, this)
                engine.initialize()
                advanceUntilIdle()

                engine.updateName("Sakura")
                advanceUntilIdle()

                coVerify { dao.updateName("Sakura") }
            }

        @Test
        fun should_updateVoiceProfile_when_updateVoiceCalled() =
            runTest {
                val dao = createDao(CharacterState(name = "Aiko"))
                val engine = CharacterEngine(dao, this)
                engine.initialize()
                advanceUntilIdle()

                engine.updateVoiceProfile(VoiceProfile.DEEP)
                advanceUntilIdle()

                coVerify { dao.updateVoiceProfile("deep") }
            }
    }
}
