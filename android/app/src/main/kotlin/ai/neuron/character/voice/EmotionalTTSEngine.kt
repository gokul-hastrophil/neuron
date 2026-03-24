package ai.neuron.character.voice

import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.VoiceProfile
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt singleton wrapping Android [TextToSpeech] with emotional prosody.
 * Builds SSML from [EmotionState] and applies [VoiceProfile] pitch/rate settings.
 *
 * Testable logic is delegated to [SsmlBuilder] (SSML generation)
 * and [VoiceSynthesizer] (state machine).
 */
@Singleton
class EmotionalTTSEngine
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var tts: TextToSpeech? = null
        private var initialized = false
        private val utteranceCounter = AtomicInteger(0)

        val synthesizer = VoiceSynthesizer()
        val speakingState: StateFlow<SpeakingState> = synthesizer.speakingState

        fun initialize() {
            tts =
                TextToSpeech(context) { status ->
                    initialized = status == TextToSpeech.SUCCESS
                    if (initialized) {
                        tts?.language = Locale.US
                        tts?.setOnUtteranceProgressListener(utteranceListener)
                    } else {
                        Log.e(TAG, "TTS initialization failed with status: $status")
                    }
                }
        }

        /**
         * Speak text with emotion and voice profile applied.
         * Builds SSML with prosody tags and sets TTS pitch/rate from profile.
         */
        fun speak(
            text: String,
            emotion: EmotionState,
            voiceProfile: VoiceProfile,
        ) {
            val engine = tts ?: return
            if (!initialized) return

            // Apply voice profile base settings
            engine.setPitch(voiceProfile.pitch)
            engine.setSpeechRate(voiceProfile.speechRate)

            // Build SSML with emotion prosody
            val ssml = SsmlBuilder.build(text, emotion)
            val utteranceId = "neuron_tts_${utteranceCounter.incrementAndGet()}"

            val params =
                Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }

            engine.speak(ssml, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }

        fun stop() {
            tts?.stop()
            synthesizer.stop()
        }

        fun shutdown() {
            stop()
            tts?.shutdown()
            tts = null
            initialized = false
        }

        private val utteranceListener =
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    synthesizer.onSpeakStart(utteranceId)
                }

                override fun onDone(utteranceId: String) {
                    synthesizer.onSpeakDone(utteranceId)
                }

                @Deprecated("Deprecated in API 21+", ReplaceWith("onError(utteranceId, errorCode)"))
                override fun onError(utteranceId: String) {
                    synthesizer.onSpeakDone(utteranceId)
                }

                override fun onError(
                    utteranceId: String,
                    errorCode: Int,
                ) {
                    Log.e(TAG, "TTS error for $utteranceId: code=$errorCode")
                    synthesizer.onSpeakDone(utteranceId)
                }
            }

        companion object {
            private const val TAG = "EmotionalTTS"
        }
    }
