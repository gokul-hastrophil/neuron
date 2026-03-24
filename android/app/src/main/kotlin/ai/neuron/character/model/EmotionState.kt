package ai.neuron.character.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the character's current emotional state.
 * Used by both the character renderer (visual expression) and the LLM prompt builder (response tone).
 * Each state maps to: visual animation parameters, SSML prosody tags, and a UI color tint.
 */
@Serializable
enum class EmotionState(
    val displayName: String,
    val emoji: String,
    val colorTint: Long,
    val animationKey: String,
    val ttsEmotionTag: TtsEmotion,
) {
    @SerialName("happy")
    HAPPY(
        displayName = "Happy",
        emoji = "\uD83D\uDE0A",
        colorTint = 0xFFFFC107,
        animationKey = "happy",
        ttsEmotionTag = TtsEmotion(pitchShift = 1.10f, rateShift = 1.05f, volumeShift = 1.0f),
    ),

    @SerialName("sad")
    SAD(
        displayName = "Sad",
        emoji = "\uD83D\uDE1E",
        colorTint = 0xFF5C6BC0,
        animationKey = "sad",
        ttsEmotionTag = TtsEmotion(pitchShift = 0.90f, rateShift = 0.85f, volumeShift = 0.9f),
    ),

    @SerialName("thinking")
    THINKING(
        displayName = "Thinking",
        emoji = "\uD83E\uDD14",
        colorTint = 0xFF78909C,
        animationKey = "thinking",
        ttsEmotionTag = TtsEmotion(pitchShift = 0.95f, rateShift = 0.80f, volumeShift = 0.95f),
    ),

    @SerialName("excited")
    EXCITED(
        displayName = "Excited",
        emoji = "\uD83E\uDD29",
        colorTint = 0xFFFF5722,
        animationKey = "excited",
        ttsEmotionTag = TtsEmotion(pitchShift = 1.20f, rateShift = 1.10f, volumeShift = 1.10f),
    ),

    @SerialName("concerned")
    CONCERNED(
        displayName = "Concerned",
        emoji = "\uD83D\uDE1F",
        colorTint = 0xFFFF9800,
        animationKey = "concerned",
        ttsEmotionTag = TtsEmotion(pitchShift = 0.95f, rateShift = 0.90f, volumeShift = 0.95f),
    ),

    @SerialName("playful")
    PLAYFUL(
        displayName = "Playful",
        emoji = "\uD83D\uDE1C",
        colorTint = 0xFFE91E63,
        animationKey = "playful",
        ttsEmotionTag = TtsEmotion(pitchShift = 1.15f, rateShift = 1.08f, volumeShift = 1.05f),
    ),

    @SerialName("focused")
    FOCUSED(
        displayName = "Focused",
        emoji = "\uD83E\uDDD0",
        colorTint = 0xFF2196F3,
        animationKey = "focused",
        ttsEmotionTag = TtsEmotion(pitchShift = 1.0f, rateShift = 0.95f, volumeShift = 1.0f),
    ),

    @SerialName("neutral")
    NEUTRAL(
        displayName = "Neutral",
        emoji = "\uD83D\uDE10",
        colorTint = 0xFF9E9E9E,
        animationKey = "neutral",
        ttsEmotionTag = TtsEmotion(pitchShift = 1.0f, rateShift = 1.0f, volumeShift = 1.0f),
    ),
    ;

    companion object {
        val DEFAULT = NEUTRAL

        fun fromString(value: String): EmotionState = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NEUTRAL
    }
}

/**
 * SSML prosody parameters for emotional TTS.
 * Values are multipliers: 1.0 = normal, >1.0 = higher/faster/louder, <1.0 = lower/slower/quieter.
 */
@Serializable
data class TtsEmotion(
    val pitchShift: Float = 1.0f,
    val rateShift: Float = 1.0f,
    val volumeShift: Float = 1.0f,
) {
    fun toSsmlProsody(): String {
        val pitchPct = kotlin.math.round((pitchShift - 1.0f) * 100).toInt()
        val ratePct = kotlin.math.round((rateShift - 1.0f) * 100).toInt()
        val volPct = kotlin.math.round((volumeShift - 1.0f) * 100).toInt()
        val attrs =
            buildList {
                if (pitchPct != 0) add("pitch=\"${if (pitchPct > 0) "+" else ""}$pitchPct%\"")
                if (ratePct != 0) add("rate=\"${if (ratePct > 0) "+" else ""}$ratePct%\"")
                if (volPct != 0) add("volume=\"${if (volPct > 0) "+" else ""}$volPct%\"")
            }
        return if (attrs.isEmpty()) "" else attrs.joinToString(" ")
    }
}
