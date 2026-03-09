package ai.neuron.sdk

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry @Inject constructor() {

    private val tools = mutableMapOf<String, NeuronTool>()

    fun register(tool: NeuronTool) {
        require(tool.name !in tools) { "Tool '${tool.name}' is already registered" }
        tools[tool.name] = tool
    }

    fun unregister(name: String): Boolean = tools.remove(name) != null

    fun listTools(): List<NeuronTool> = tools.values.toList()

    fun get(name: String): NeuronTool? = tools[name]

    suspend fun invoke(name: String, params: Map<String, String>): String? {
        val tool = tools[name] ?: return null
        return tool.execute(params)
    }

    fun toPromptSnippet(): String {
        if (tools.isEmpty()) return ""
        return buildString {
            appendLine("Available custom tools:")
            for (tool in tools.values) {
                appendLine("- ${tool.name}: ${tool.description}")
                if (tool.parameters.isNotEmpty()) {
                    appendLine("  Parameters: ${tool.parameters.entries.joinToString { "${it.key}: ${it.value}" }}")
                }
            }
        }
    }
}
