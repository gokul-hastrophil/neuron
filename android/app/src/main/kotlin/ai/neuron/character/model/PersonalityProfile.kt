package ai.neuron.character.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Configurable personality traits for the AI character.
 * All float traits are 0.0-1.0. Injected into every LLM system prompt.
 */
@Serializable
data class PersonalityProfile(
    val name: String = "Neuron",
    val humor: Float = 0.6f,
    val formality: Float = 0.3f,
    val empathy: Float = 0.8f,
    val curiosity: Float = 0.7f,
    val sassiness: Float = 0.4f,
    val protectiveness: Float = 0.6f,
    @SerialName("speaking_style") val speakingStyle: SpeakingStyle = SpeakingStyle.DEFAULT,
    val backstory: String = "",
) {
    init {
        require(humor in 0f..1f) { "humor must be 0.0-1.0" }
        require(formality in 0f..1f) { "formality must be 0.0-1.0" }
        require(empathy in 0f..1f) { "empathy must be 0.0-1.0" }
        require(curiosity in 0f..1f) { "curiosity must be 0.0-1.0" }
        require(sassiness in 0f..1f) { "sassiness must be 0.0-1.0" }
        require(protectiveness in 0f..1f) { "protectiveness must be 0.0-1.0" }
        require(name.isNotBlank()) { "name must not be blank" }
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        fun fromJson(jsonString: String): PersonalityProfile = json.decodeFromString(serializer(), jsonString)

        fun defaultAnimeGirl() =
            PersonalityProfile(
                name = "Aiko",
                humor = 0.7f,
                formality = 0.2f,
                empathy = 0.9f,
                curiosity = 0.8f,
                sassiness = 0.5f,
                protectiveness = 0.6f,
                speakingStyle = SpeakingStyle.PLAYFUL,
                backstory = "A cheerful and curious AI who loves learning about you and helping with anything.",
            )

        fun defaultAnimeBoy() =
            PersonalityProfile(
                name = "Kai",
                humor = 0.5f,
                formality = 0.4f,
                empathy = 0.7f,
                curiosity = 0.9f,
                sassiness = 0.3f,
                protectiveness = 0.7f,
                speakingStyle = SpeakingStyle.CALM,
                backstory = "A thoughtful and reliable AI who gives careful advice and always has your back.",
            )

        fun defaultAbstract() =
            PersonalityProfile(
                name = "Nuri",
                humor = 0.6f,
                formality = 0.3f,
                empathy = 0.8f,
                curiosity = 0.7f,
                sassiness = 0.4f,
                protectiveness = 0.6f,
                speakingStyle = SpeakingStyle.WARM,
                backstory = "A friendly little companion who keeps things simple and gets stuff done.",
            )
    }
}
