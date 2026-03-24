package ai.neuron.character.voice

import ai.neuron.character.model.EmotionState

/**
 * Builds SSML markup for emotional text-to-speech.
 * Maps [EmotionState] to prosody tags (pitch, rate, volume).
 */
object SsmlBuilder {
    /**
     * Build an SSML string for the given text and emotion.
     * The output is a valid `<speak>` document with optional `<prosody>` and `<break>` tags.
     */
    fun build(
        text: String,
        emotion: EmotionState,
    ): String {
        val escaped = escapeXml(text)
        val prosody = emotion.ttsEmotionTag.toSsmlProsody()

        return buildString {
            append("<speak>")

            // Thinking gets a leading pause for natural "hmm" effect
            if (emotion == EmotionState.THINKING) {
                append("<break time=\"400ms\"/>")
            }

            if (prosody.isNotEmpty()) {
                append("<prosody $prosody>")
                append(escaped)
                append("</prosody>")
            } else {
                append(escaped)
            }

            append("</speak>")
        }
    }

    private fun escapeXml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
