package ai.neuron.character

import ai.neuron.character.dao.CharacterDao
import ai.neuron.character.model.CharacterState
import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.PersonalityProfile
import ai.neuron.character.ui.CharacterViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterViewModelTest {
    private fun createDao(existing: CharacterState? = null): CharacterDao {
        val dao = mockk<CharacterDao>(relaxed = true)
        coEvery { dao.getActiveCharacter() } returns existing
        return dao
    }

    private companion object {
        const val PAST_DEBOUNCE_MS = 200L
    }

    // ─── Gallery State ───

    @Nested
    @DisplayName("Gallery state")
    inner class GalleryState {
        @Test
        fun should_listAllCharacterTypes_when_galleryLoaded() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                val vm = CharacterViewModel(engine)

                val types = vm.availableCharacters
                assertEquals(CharacterType.entries.size, types.size)
            }

        @Test
        fun should_haveNoSelection_when_noCharacterInDb() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                engine.initialize()
                advanceUntilIdle()

                val vm = CharacterViewModel(engine)
                assertEquals(null, vm.uiState.value.selectedType)
            }

        @Test
        fun should_showCurrentCharacter_when_existsInDb() =
            runTest {
                val existing = CharacterState(selectedType = "ANIME_BOY", name = "Kai")
                val engine = CharacterEngine(createDao(existing), this)
                engine.initialize()
                advanceUntilIdle()

                val vm = CharacterViewModel(engine)
                assertEquals(CharacterType.ANIME_BOY, vm.uiState.value.selectedType)
                assertEquals("Kai", vm.uiState.value.name)
            }
    }

    // ─── Character Selection ───

    @Nested
    @DisplayName("Character selection")
    inner class Selection {
        @Test
        fun should_updateUiState_when_characterSelected() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                val vm = CharacterViewModel(engine)

                vm.selectCharacter(CharacterType.ABSTRACT_CUTE)
                advanceUntilIdle()

                assertEquals(CharacterType.ABSTRACT_CUTE, vm.uiState.value.selectedType)
            }

        @Test
        fun should_useDefaultName_when_characterSelected() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                val vm = CharacterViewModel(engine)

                vm.selectCharacter(CharacterType.ANIME_GIRL)
                advanceUntilIdle()

                assertEquals(CharacterType.ANIME_GIRL.defaultPersonality.name, vm.uiState.value.name)
            }
    }

    // ─── Customization ───

    @Nested
    @DisplayName("Customization")
    inner class Customization {
        @Test
        fun should_updateName_when_nameChanged() =
            runTest {
                val existing = CharacterState(name = "Aiko")
                val engine = CharacterEngine(createDao(existing), this)
                engine.initialize()
                advanceUntilIdle()

                val vm = CharacterViewModel(engine)
                vm.updateName("Sakura")
                advanceUntilIdle()

                assertEquals("Sakura", vm.uiState.value.name)
            }

        @Test
        fun should_updatePersonalitySlider_when_humorChanged() =
            runTest {
                val existing =
                    CharacterState(
                        name = "Aiko",
                        personalityJson = PersonalityProfile.defaultAnimeGirl().toJson(),
                    )
                val engine = CharacterEngine(createDao(existing), this)
                engine.initialize()
                advanceUntilIdle()

                val vm = CharacterViewModel(engine)
                vm.updateHumor(0.9f)
                advanceUntilIdle()

                assertEquals(0.9f, vm.uiState.value.humor)
            }

        @Test
        fun should_clampSliderValue_when_outOfRange() =
            runTest {
                val existing =
                    CharacterState(
                        name = "Aiko",
                        personalityJson = PersonalityProfile.defaultAnimeGirl().toJson(),
                    )
                val engine = CharacterEngine(createDao(existing), this)
                engine.initialize()
                advanceUntilIdle()

                val vm = CharacterViewModel(engine)
                vm.updateHumor(1.5f) // over max
                advanceUntilIdle()

                assertEquals(1.0f, vm.uiState.value.humor)
            }
    }

    // ─── Emotion Display ───

    @Nested
    @DisplayName("Emotion display")
    inner class EmotionDisplay {
        @Test
        fun should_reflectEngineEmotion_when_emotionChanges() =
            runTest {
                val engine = CharacterEngine(createDao(), this)
                val vm = CharacterViewModel(engine)

                engine.setEmotion(EmotionState.HAPPY, "test")
                advanceTimeBy(PAST_DEBOUNCE_MS)

                assertEquals(EmotionState.HAPPY, vm.emotionState.value)
            }
    }
}
