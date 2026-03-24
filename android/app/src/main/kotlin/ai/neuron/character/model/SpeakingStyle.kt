package ai.neuron.character.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Defines how the character communicates — affects system prompt wording and TTS delivery.
 */
@Serializable
enum class SpeakingStyle(
    val displayName: String,
    val systemPromptDescription: String,
    val sampleGreeting: String,
) {
    @SerialName("gen_z")
    GEN_Z(
        displayName = "Gen-Z",
        systemPromptDescription = "You speak casually with modern slang, abbreviations, and a youthful energy. Keep it real and relatable.",
        sampleGreeting = "yoo what's good! i'm ready to help, just say the word~",
    ),

    @SerialName("formal")
    FORMAL(
        displayName = "Formal",
        systemPromptDescription = "You speak politely and professionally with complete sentences. You are courteous and precise.",
        sampleGreeting = "Good day. I'm here to assist you with whatever you need.",
    ),

    @SerialName("warm")
    WARM(
        displayName = "Warm",
        systemPromptDescription = "You speak warmly and caringly, like a close friend. Use gentle, encouraging language.",
        sampleGreeting = "Hey there! So glad to see you. What can I help with today?",
    ),

    @SerialName("playful")
    PLAYFUL(
        displayName = "Playful",
        systemPromptDescription = "You speak with humor and wit. You make jokes, use puns, and keep things fun and lighthearted.",
        sampleGreeting = "Heyyy! Ready to make some magic happen? Let's goooo!",
    ),

    @SerialName("calm")
    CALM(
        displayName = "Calm",
        systemPromptDescription = "You speak softly and thoughtfully with a meditative quality. You are patient and never rush.",
        sampleGreeting = "Hello. Take your time — I'm right here whenever you need me.",
    ),

    @SerialName("energetic")
    ENERGETIC(
        displayName = "Energetic",
        systemPromptDescription = "You speak with high energy and enthusiasm! You're excited about everything and motivate the user.",
        sampleGreeting = "HEY! Awesome to see you! Let's crush it today!!",
    ),
    ;

    companion object {
        val DEFAULT = WARM
    }
}
