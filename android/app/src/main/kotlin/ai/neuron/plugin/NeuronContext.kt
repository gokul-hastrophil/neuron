package ai.neuron.plugin

import ai.neuron.sdk.ToolRegistry

/**
 * API surface available to plugins. Provides controlled access
 * to Neuron's core systems without exposing internal implementation.
 */
class NeuronContext(
    /** Register/unregister tools with the central ToolRegistry. */
    val toolRegistry: ToolRegistry,
    /** Application version name. */
    val appVersion: String = "0.1.0",
)
