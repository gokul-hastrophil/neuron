package ai.neuron.character

import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.VoiceProfile
import ai.neuron.character.voice.SpeakingState
import ai.neuron.character.voice.SsmlBuilder
import ai.neuron.character.voice.VoiceSynthesizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EmotionalTTSTest {
    // ─── SSML Generation ───

    @Nested
    @DisplayName("SSML builder")
    inner class SsmlBuilderTests {
        @Test
        fun should_wrapInSpeakTag_when_buildCalled() {
            val ssml = SsmlBuilder.build("Hello world", EmotionState.NEUTRAL)
            assertTrue(ssml.startsWith("<speak>"), "Should start with <speak>, got: $ssml")
            assertTrue(ssml.endsWith("</speak>"), "Should end with </speak>, got: $ssml")
        }

        @Test
        fun should_containText_when_buildCalled() {
            val ssml = SsmlBuilder.build("Hello world", EmotionState.NEUTRAL)
            assertTrue(ssml.contains("Hello world"), "Should contain the text")
        }

        @Test
        fun should_addProsody_when_emotionHasShifts() {
            val ssml = SsmlBuilder.build("I'm happy!", EmotionState.HAPPY)
            assertTrue(ssml.contains("<prosody"), "HAPPY should have prosody tags, got: $ssml")
            assertTrue(ssml.contains("pitch="), "HAPPY should shift pitch, got: $ssml")
        }

        @Test
        fun should_notAddProsody_when_neutralEmotion() {
            val ssml = SsmlBuilder.build("Just normal", EmotionState.NEUTRAL)
            assertFalse(ssml.contains("<prosody"), "NEUTRAL should not have prosody tags, got: $ssml")
        }

        @Test
        fun should_addBreakTag_when_thinkingEmotion() {
            val ssml = SsmlBuilder.build("Let me think", EmotionState.THINKING)
            assertTrue(ssml.contains("<break"), "THINKING should have pause, got: $ssml")
        }

        @Test
        fun should_escapeXml_when_textHasSpecialChars() {
            val ssml = SsmlBuilder.build("A & B <tag> \"quote\"", EmotionState.NEUTRAL)
            assertTrue(ssml.contains("&amp;"), "Should escape &")
            assertTrue(ssml.contains("&lt;"), "Should escape <")
        }

        @Test
        fun should_matchEmotionProsody_when_happyEmotion() {
            val emotion = EmotionState.HAPPY.ttsEmotionTag
            val prosody = emotion.toSsmlProsody()
            assertTrue(prosody.contains("pitch=\"+"), "HAPPY should have positive pitch")
        }

        @Test
        fun should_matchEmotionProsody_when_sadEmotion() {
            val emotion = EmotionState.SAD.ttsEmotionTag
            val prosody = emotion.toSsmlProsody()
            assertTrue(prosody.contains("pitch=\"-"), "SAD should have negative pitch")
            assertTrue(prosody.contains("rate=\"-"), "SAD should have slower rate")
        }
    }

    // ─── Voice Profile Mapping ───

    @Nested
    @DisplayName("Voice profile mapping")
    inner class VoiceProfileMapping {
        @Test
        fun should_havePitchInRange_when_defaultProfilesChecked() {
            VoiceProfile.DEFAULTS.forEach { profile ->
                assertTrue(profile.pitch in 0.5f..2.0f, "${profile.id} pitch out of range: ${profile.pitch}")
                assertTrue(profile.speechRate in 0.5f..2.0f, "${profile.id} rate out of range: ${profile.speechRate}")
            }
        }

        @Test
        fun should_haveDistinctPitches_when_defaultsCompared() {
            val pitches = VoiceProfile.DEFAULTS.map { it.pitch }.toSet()
            assertEquals(VoiceProfile.DEFAULTS.size, pitches.size, "Default profiles should have distinct pitches")
        }

        @Test
        fun should_combineProsody_when_profileAndEmotionApplied() {
            // Verify that emotion prosody modifies the base voice
            val profile = VoiceProfile.BRIGHT // pitch = 1.3
            val emotion = EmotionState.HAPPY.ttsEmotionTag // pitchShift = 1.10
            val effectivePitch = profile.pitch * emotion.pitchShift
            assertTrue(effectivePitch > profile.pitch, "HAPPY emotion should raise BRIGHT voice pitch further")
        }
    }

    // ─── VoiceSynthesizer State Machine ───

    @Nested
    @DisplayName("VoiceSynthesizer state machine")
    inner class StateMachine {
        @Test
        fun should_startIdle_when_created() {
            val synth = VoiceSynthesizer()
            assertEquals(SpeakingState.IDLE, synth.speakingState.value)
        }

        @Test
        fun should_transitionToSpeaking_when_speakStarted() {
            val synth = VoiceSynthesizer()
            synth.onSpeakStart("utt-1")
            assertEquals(SpeakingState.SPEAKING, synth.speakingState.value)
        }

        @Test
        fun should_transitionToDone_when_speakCompleted() {
            val synth = VoiceSynthesizer()
            synth.onSpeakStart("utt-1")
            synth.onSpeakDone("utt-1")
            assertEquals(SpeakingState.IDLE, synth.speakingState.value)
        }

        @Test
        fun should_returnToIdle_when_stopped() {
            val synth = VoiceSynthesizer()
            synth.onSpeakStart("utt-1")
            synth.stop()
            assertEquals(SpeakingState.IDLE, synth.speakingState.value)
        }

        @Test
        fun should_trackUtteranceId_when_speaking() {
            val synth = VoiceSynthesizer()
            synth.onSpeakStart("utt-42")
            assertEquals("utt-42", synth.currentUtteranceId)
        }

        @Test
        fun should_clearUtteranceId_when_done() {
            val synth = VoiceSynthesizer()
            synth.onSpeakStart("utt-42")
            synth.onSpeakDone("utt-42")
            assertEquals(null, synth.currentUtteranceId)
        }
    }

    // ─── SSML Prosody for All Emotions ───

    @Nested
    @DisplayName("All emotion SSML prosody")
    inner class AllEmotionProsody {
        @Test
        fun should_produceValidSsml_when_allEmotionsRendered() {
            EmotionState.entries.forEach { emotion ->
                val ssml = SsmlBuilder.build("Test", emotion)
                assertTrue(ssml.startsWith("<speak>"), "${emotion.name} SSML should start with <speak>")
                assertTrue(ssml.endsWith("</speak>"), "${emotion.name} SSML should end with </speak>")
                assertTrue(ssml.contains("Test"), "${emotion.name} SSML should contain text")
            }
        }
    }
}
