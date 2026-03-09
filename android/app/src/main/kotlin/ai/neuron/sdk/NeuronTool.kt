package ai.neuron.sdk

data class NeuronTool(
    val name: String,
    val description: String,
    val parameters: Map<String, String>,
    val execute: suspend (params: Map<String, String>) -> String,
)
