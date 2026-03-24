package ai.neuron.plugin

import ai.neuron.sdk.NeuronTool

/**
 * Interface for Neuron plugins. Plugins extend Neuron's capabilities
 * without modifying core code. Each plugin has a lifecycle (load/unload)
 * and can register tools and skills.
 */
interface NeuronPlugin {
    /** Unique plugin identifier. */
    val id: String

    /** Plugin version string (semver). */
    val version: String

    /** Human-readable plugin name. */
    val displayName: String

    /**
     * Called when the plugin is loaded. Initialize resources here.
     * @param context Provides access to Neuron's APIs (ToolRegistry, Memory, etc.)
     */
    fun onLoad(context: NeuronContext)

    /**
     * Called when the plugin is unloaded. Clean up resources here.
     */
    fun onUnload()

    /**
     * Return tools this plugin provides. Called after onLoad().
     */
    fun getTools(): List<NeuronTool>

    /**
     * Return skill manifest IDs this plugin provides.
     */
    fun getSkillIds(): List<String> = emptyList()
}
