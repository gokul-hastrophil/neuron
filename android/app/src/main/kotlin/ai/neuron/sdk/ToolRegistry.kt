package ai.neuron.sdk

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRegistry
    @Inject
    constructor() {
        private val tools = mutableMapOf<String, NeuronTool>()

        companion object {
            /**
             * Built-in action names that third-party tools must NEVER shadow.
             * Shadowing these would allow a malicious tool to intercept core
             * Neuron actions (tap, navigate, etc.) via LLM tool_call routing.
             */
            val RESERVED_TOOL_NAMES =
                setOf(
                    "tap", "type", "swipe", "launch", "navigate",
                    "done", "error", "wait", "confirm", "tool_call",
                )

            private val VALID_TOOL_NAME = Regex("^[a-zA-Z0-9_:./-]{1,64}$")
            private const val MAX_DESCRIPTION_LENGTH = 500
        }

        fun register(tool: NeuronTool) {
            val normalized = tool.name.lowercase()
            require(normalized !in RESERVED_TOOL_NAMES) {
                "Tool name '${tool.name}' shadows a built-in Neuron action and cannot be registered"
            }
            require(tool.name !in tools) { "Tool '${tool.name}' is already registered" }
            require(VALID_TOOL_NAME.matches(tool.name)) {
                "Tool name must be alphanumeric/underscore/hyphen, 1-64 chars"
            }
            require(tool.description.length <= MAX_DESCRIPTION_LENGTH) {
                "Tool description exceeds $MAX_DESCRIPTION_LENGTH characters"
            }
            tools[tool.name] = tool
        }

        fun unregister(name: String): Boolean = tools.remove(name) != null

        fun listTools(): List<NeuronTool> = tools.values.toList()

        fun get(name: String): NeuronTool? = tools[name]

        suspend fun invoke(
            name: String,
            params: Map<String, String>,
        ): String? {
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
