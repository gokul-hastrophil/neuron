package ai.neuron.character.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Defines which rendering engine a character type uses.
 */
@Serializable
enum class RendererType {
    @SerialName("live2d")
    LIVE2D,

    @SerialName("compose")
    COMPOSE,
}

/**
 * Available character types the user can select.
 * Each type maps to a renderer, default personality, default voice, and asset path.
 */
@Serializable
enum class CharacterType(
    val displayName: String,
    val tagline: String,
    val modelPath: String,
    val rendererType: RendererType,
    @kotlinx.serialization.Transient val defaultVoiceProfile: VoiceProfile = VoiceProfile.CALM,
    @kotlinx.serialization.Transient val defaultPersonality: PersonalityProfile = PersonalityProfile(),
) {
    @SerialName("anime_girl")
    ANIME_GIRL(
        displayName = "Anime Girl",
        tagline = "Cheerful, expressive, and full of energy",
        modelPath = "characters/anime_girl/model.moc3",
        rendererType = RendererType.LIVE2D,
        defaultVoiceProfile = VoiceProfile.BRIGHT,
        defaultPersonality = PersonalityProfile.defaultAnimeGirl(),
    ),

    @SerialName("anime_boy")
    ANIME_BOY(
        displayName = "Anime Boy",
        tagline = "Thoughtful, reliable, and always composed",
        modelPath = "characters/anime_boy/model.moc3",
        rendererType = RendererType.LIVE2D,
        defaultVoiceProfile = VoiceProfile.DEEP,
        defaultPersonality = PersonalityProfile.defaultAnimeBoy(),
    ),

    @SerialName("abstract_cute")
    ABSTRACT_CUTE(
        displayName = "Cute Blob",
        tagline = "Simple, friendly, and adorable",
        modelPath = "characters/abstract_cute/",
        rendererType = RendererType.COMPOSE,
        defaultVoiceProfile = VoiceProfile.CALM,
        defaultPersonality = PersonalityProfile.defaultAbstract(),
    ),

    @SerialName("abstract_pixel")
    ABSTRACT_PIXEL(
        displayName = "Pixel Pal",
        tagline = "Retro vibes with a modern mind",
        modelPath = "characters/abstract_pixel/",
        rendererType = RendererType.COMPOSE,
        defaultVoiceProfile = VoiceProfile.CALM,
        defaultPersonality =
            PersonalityProfile.defaultAbstract().copy(
                name = "Pix",
                speakingStyle = SpeakingStyle.GEN_Z,
                backstory = "A pixelated companion from the future who keeps things fun and efficient.",
            ),
    ),
    ;

    companion object {
        val DEFAULT = ANIME_GIRL

        fun fromString(value: String): CharacterType = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DEFAULT
    }
}
