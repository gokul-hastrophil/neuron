package ai.neuron.plugin

import ai.neuron.sdk.NeuronTool

/**
 * Wraps the Character Engine as a NeuronPlugin.
 * Demonstrates the plugin pattern and provides character-related tools.
 */
class CharacterPlugin : NeuronPlugin {
    override val id = "neuron.character"
    override val version = "1.0.0"
    override val displayName = "Character Engine"

    private var context: NeuronContext? = null
    private var loaded = false

    override fun onLoad(context: NeuronContext) {
        this.context = context
        loaded = true
    }

    override fun onUnload() {
        context = null
        loaded = false
    }

    override fun getTools(): List<NeuronTool> =
        listOf(
            NeuronTool(
                name = "set_emotion",
                description = "Set the character's current emotion state",
                parameters = mapOf("emotion" to "Emotion name: NEUTRAL, HAPPY, EXCITED, THINKING, CONCERNED, SURPRISED, FOCUSED, PLAYFUL"),
                execute = { params ->
                    val emotion = params["emotion"] ?: "NEUTRAL"
                    "Emotion set to $emotion"
                },
            ),
            NeuronTool(
                name = "get_character_info",
                description = "Get information about the active character",
                parameters = emptyMap(),
                execute = {
                    "Character plugin v$version loaded"
                },
            ),
        )

    override fun getSkillIds(): List<String> = listOf("character-interaction")

    fun isLoaded(): Boolean = loaded
}
