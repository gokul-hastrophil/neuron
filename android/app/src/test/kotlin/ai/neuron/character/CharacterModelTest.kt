package ai.neuron.character

import ai.neuron.character.model.CharacterState
import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.PersonalityProfile
import ai.neuron.character.model.RendererType
import ai.neuron.character.model.SpeakingStyle
import ai.neuron.character.model.TtsEmotion
import ai.neuron.character.model.VoiceGender
import ai.neuron.character.model.VoiceProfile
import ai.neuron.character.model.VoiceWarmth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CharacterModelTest {
    // ─── EmotionState ───

    @Test
    fun should_haveEightEmotionStates_when_enumEntriesListed() {
        assertEquals(8, EmotionState.entries.size)
    }

    @Test
    fun should_returnNeutral_when_defaultRequested() {
        assertEquals(EmotionState.NEUTRAL, EmotionState.DEFAULT)
    }

    @Test
    fun should_parseFromString_when_validName() {
        assertEquals(EmotionState.HAPPY, EmotionState.fromString("happy"))
        assertEquals(EmotionState.HAPPY, EmotionState.fromString("HAPPY"))
        assertEquals(EmotionState.THINKING, EmotionState.fromString("Thinking"))
    }

    @Test
    fun should_returnNeutral_when_invalidStringParsed() {
        assertEquals(EmotionState.NEUTRAL, EmotionState.fromString("nonexistent"))
        assertEquals(EmotionState.NEUTRAL, EmotionState.fromString(""))
    }

    @Test
    fun should_haveUniqueAnimationKeys_when_allStatesChecked() {
        val keys = EmotionState.entries.map { it.animationKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun should_haveNonEmptyDisplayNames_when_allStatesChecked() {
        EmotionState.entries.forEach { state ->
            assertTrue(state.displayName.isNotBlank(), "${state.name} displayName is blank")
        }
    }

    // ─── TtsEmotion ───

    @Test
    fun should_generateEmptySsml_when_noShifts() {
        val neutral = TtsEmotion(1.0f, 1.0f, 1.0f)
        assertEquals("", neutral.toSsmlProsody())
    }

    @Test
    fun should_generatePitchSsml_when_pitchShifted() {
        val happy = TtsEmotion(pitchShift = 1.10f, rateShift = 1.0f, volumeShift = 1.0f)
        val ssml = happy.toSsmlProsody()
        assertTrue(ssml.contains("pitch=\"+10%\""), "Expected pitch +10%, got: $ssml")
    }

    @Test
    fun should_generateNegativeSsml_when_pitchLowered() {
        val sad = TtsEmotion(pitchShift = 0.90f, rateShift = 0.85f, volumeShift = 0.9f)
        val ssml = sad.toSsmlProsody()
        assertTrue(ssml.contains("pitch=\"-10%\""), "Expected pitch -10%, got: $ssml")
        assertTrue(ssml.contains("rate=\"-15%\""), "Expected rate -15%, got: $ssml")
    }

    // ─── PersonalityProfile ───

    @Test
    fun should_serializeAndDeserialize_when_personalityRoundTripped() {
        val profile = PersonalityProfile.defaultAnimeGirl()
        val json = profile.toJson()
        val restored = PersonalityProfile.fromJson(json)
        assertEquals(profile.name, restored.name)
        assertEquals(profile.humor, restored.humor)
        assertEquals(profile.speakingStyle, restored.speakingStyle)
        assertEquals(profile.empathy, restored.empathy)
    }

    @Test
    fun should_throwException_when_traitOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) {
            PersonalityProfile(humor = 1.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersonalityProfile(empathy = -0.1f)
        }
    }

    @Test
    fun should_throwException_when_nameBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            PersonalityProfile(name = "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersonalityProfile(name = "   ")
        }
    }

    @Test
    fun should_haveDistinctDefaults_when_factoryMethodsCalled() {
        val girl = PersonalityProfile.defaultAnimeGirl()
        val boy = PersonalityProfile.defaultAnimeBoy()
        val abstractProfile = PersonalityProfile.defaultAbstract()
        assertTrue(girl.name != boy.name)
        assertTrue(girl.speakingStyle != boy.speakingStyle)
        assertNotNull(abstractProfile.backstory)
    }

    // ─── SpeakingStyle ───

    @Test
    fun should_haveSixStyles_when_enumEntriesListed() {
        assertEquals(6, SpeakingStyle.entries.size)
    }

    @Test
    fun should_haveNonEmptySampleGreetings_when_allStylesChecked() {
        SpeakingStyle.entries.forEach { style ->
            assertTrue(style.sampleGreeting.isNotBlank(), "${style.name} sampleGreeting is blank")
            assertTrue(style.systemPromptDescription.isNotBlank(), "${style.name} systemPromptDescription is blank")
        }
    }

    @Test
    fun should_returnWarm_when_defaultRequested() {
        assertEquals(SpeakingStyle.WARM, SpeakingStyle.DEFAULT)
    }

    // ─── CharacterType ───

    @Test
    fun should_haveFourTypes_when_enumEntriesListed() {
        assertEquals(4, CharacterType.entries.size)
    }

    @Test
    fun should_useLive2D_when_animeTypeSelected() {
        assertEquals(RendererType.LIVE2D, CharacterType.ANIME_GIRL.rendererType)
        assertEquals(RendererType.LIVE2D, CharacterType.ANIME_BOY.rendererType)
    }

    @Test
    fun should_useCompose_when_abstractTypeSelected() {
        assertEquals(RendererType.COMPOSE, CharacterType.ABSTRACT_CUTE.rendererType)
        assertEquals(RendererType.COMPOSE, CharacterType.ABSTRACT_PIXEL.rendererType)
    }

    @Test
    fun should_haveDefaultPersonality_when_typeChecked() {
        CharacterType.entries.forEach { type ->
            assertNotNull(type.defaultPersonality, "${type.name} has null defaultPersonality")
            assertTrue(type.defaultPersonality.name.isNotBlank(), "${type.name} defaultPersonality.name is blank")
        }
    }

    @Test
    fun should_parseFromString_when_validTypeName() {
        assertEquals(CharacterType.ANIME_GIRL, CharacterType.fromString("ANIME_GIRL"))
        assertEquals(CharacterType.ABSTRACT_PIXEL, CharacterType.fromString("abstract_pixel"))
    }

    @Test
    fun should_returnDefault_when_invalidTypeString() {
        assertEquals(CharacterType.DEFAULT, CharacterType.fromString("nonexistent"))
    }

    // ─── VoiceProfile ───

    @Test
    fun should_haveThreeDefaults_when_defaultsListed() {
        assertEquals(3, VoiceProfile.DEFAULTS.size)
    }

    @Test
    fun should_lookupById_when_validId() {
        assertEquals(VoiceProfile.BRIGHT, VoiceProfile.fromId("bright"))
        assertEquals(VoiceProfile.DEEP, VoiceProfile.fromId("deep"))
    }

    @Test
    fun should_returnCalm_when_invalidId() {
        assertEquals(VoiceProfile.CALM, VoiceProfile.fromId("nonexistent"))
    }

    @Test
    fun should_throwException_when_pitchOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) {
            VoiceProfile(id = "test", displayName = "Test", pitch = 3.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoiceProfile(id = "test", displayName = "Test", pitch = 0.1f)
        }
    }

    @Test
    fun should_haveCorrectGender_when_defaultsChecked() {
        assertEquals(VoiceGender.FEMALE, VoiceProfile.BRIGHT.gender)
        assertEquals(VoiceGender.MALE, VoiceProfile.DEEP.gender)
    }

    @Test
    fun should_haveWarmth_when_defaultsChecked() {
        assertEquals(VoiceWarmth.WARM, VoiceProfile.BRIGHT.warmth)
        assertEquals(VoiceWarmth.NEUTRAL, VoiceProfile.CALM.warmth)
    }

    // ─── CharacterState (Room entity) ───

    @Test
    fun should_resolveCharacterType_when_stateCreated() {
        val state = CharacterState(selectedType = "ANIME_BOY")
        assertEquals(CharacterType.ANIME_BOY, state.getCharacterType())
    }

    @Test
    fun should_resolveEmotion_when_stateCreated() {
        val state = CharacterState(currentEmotion = "EXCITED")
        assertEquals(EmotionState.EXCITED, state.getEmotionState())
    }

    @Test
    fun should_fallbackToDefault_when_invalidPersonalityJson() {
        val state = CharacterState(personalityJson = "not valid json", name = "TestName")
        val profile = state.getPersonalityProfile()
        assertEquals("TestName", profile.name)
    }

    @Test
    fun should_deserializePersonality_when_validJson() {
        val original = PersonalityProfile.defaultAnimeGirl()
        val state =
            CharacterState(
                personalityJson = original.toJson(),
                name = original.name,
            )
        val restored = state.getPersonalityProfile()
        assertEquals(original.humor, restored.humor)
        assertEquals(original.speakingStyle, restored.speakingStyle)
    }

    @Test
    fun should_resolveVoiceProfile_when_stateCreated() {
        val state = CharacterState(voiceProfileId = "bright")
        assertEquals(VoiceProfile.BRIGHT, state.getVoiceProfile())
    }
}
