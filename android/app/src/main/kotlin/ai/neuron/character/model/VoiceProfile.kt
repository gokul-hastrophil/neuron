package ai.neuron.character.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VoiceGender {
    @SerialName("female")
    FEMALE,

    @SerialName("male")
    MALE,

    @SerialName("neutral")
    NEUTRAL,
}

@Serializable
enum class VoiceWarmth {
    @SerialName("cold")
    COLD,

    @SerialName("neutral")
    NEUTRAL,

    @SerialName("warm")
    WARM,
}

/**
 * Defines voice characteristics applied to the TTS engine.
 * Pitch and speechRate are multipliers: 1.0 = default, range 0.5-2.0.
 */
@Serializable
data class VoiceProfile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val pitch: Float = 1.0f,
    @SerialName("speech_rate") val speechRate: Float = 1.0f,
    val warmth: VoiceWarmth = VoiceWarmth.NEUTRAL,
    val gender: VoiceGender = VoiceGender.FEMALE,
) {
    init {
        require(pitch in 0.5f..2.0f) { "pitch must be 0.5-2.0" }
        require(speechRate in 0.5f..2.0f) { "speechRate must be 0.5-2.0" }
    }

    companion object {
        val BRIGHT =
            VoiceProfile(
                id = "bright",
                displayName = "Bright",
                pitch = 1.2f,
                speechRate = 1.05f,
                warmth = VoiceWarmth.WARM,
                gender = VoiceGender.FEMALE,
            )

        val CALM =
            VoiceProfile(
                id = "calm",
                displayName = "Calm",
                pitch = 1.0f,
                speechRate = 0.9f,
                warmth = VoiceWarmth.NEUTRAL,
                gender = VoiceGender.FEMALE,
            )

        val DEEP =
            VoiceProfile(
                id = "deep",
                displayName = "Deep",
                pitch = 0.8f,
                speechRate = 0.95f,
                warmth = VoiceWarmth.WARM,
                gender = VoiceGender.MALE,
            )

        val DEFAULTS = listOf(BRIGHT, CALM, DEEP)

        fun fromId(id: String): VoiceProfile = DEFAULTS.firstOrNull { it.id == id } ?: CALM
    }
}
