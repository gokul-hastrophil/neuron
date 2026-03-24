package ai.neuron.brain

import ai.neuron.brain.model.ActionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defines the structured tool/function calling schema for Neuron actions.
 *
 * Used by FunctionGemma (on-device T1), Gemini (T2), and Claude (T3) for
 * structured output instead of free-text JSON parsing. Each action type
 * becomes a callable "function" with typed parameters.
 */
@Singleton
class NeuronToolSchema
    @Inject
    constructor() {
        @Serializable
        data class ToolDefinition(
            val name: String,
            val description: String,
            val parameters: List<ToolParameter>,
        )

        @Serializable
        data class ToolParameter(
            val name: String,
            val type: String,
            val description: String,
            val required: Boolean = true,
            val enumValues: List<String>? = null,
        )

        /**
         * All Neuron action tools as structured definitions.
         */
        val tools: List<ToolDefinition> =
            listOf(
                ToolDefinition(
                    name = "tap",
                    description = "Tap a UI element by its resource ID or text label",
                    parameters =
                        listOf(
                            ToolParameter("target_id", "string", "The resource ID of the element to tap", required = false),
                            ToolParameter("target_text", "string", "The visible text of the element to tap", required = false),
                            ToolParameter("reasoning", "string", "Why this action is being taken"),
                        ),
                ),
                ToolDefinition(
                    name = "type",
                    description = "Type text into the currently focused editable field",
                    parameters =
                        listOf(
                            ToolParameter("value", "string", "The text to type"),
                            ToolParameter("target_id", "string", "The resource ID of the field (optional)", required = false),
                            ToolParameter("reasoning", "string", "Why this text is being typed"),
                        ),
                ),
                ToolDefinition(
                    name = "launch",
                    description = "Launch an app by name",
                    parameters =
                        listOf(
                            ToolParameter("value", "string", "The app name to launch (e.g., 'Calculator', 'YouTube')"),
                            ToolParameter("reasoning", "string", "Why this app is being launched"),
                        ),
                ),
                ToolDefinition(
                    name = "navigate",
                    description = "Perform a navigation action",
                    parameters =
                        listOf(
                            ToolParameter(
                                "value",
                                "string",
                                "The navigation target",
                                enumValues = listOf("home", "back", "recents", "notifications", "quick_settings", "enter", "submit"),
                            ),
                            ToolParameter("reasoning", "string", "Why this navigation is needed"),
                        ),
                ),
                ToolDefinition(
                    name = "swipe",
                    description = "Swipe in a direction on the screen",
                    parameters =
                        listOf(
                            ToolParameter("value", "string", "Direction: up, down, left, right"),
                            ToolParameter("reasoning", "string", "Why swiping"),
                        ),
                ),
                ToolDefinition(
                    name = "done",
                    description = "Signal that the task is complete",
                    parameters =
                        listOf(
                            ToolParameter("reasoning", "string", "Summary of what was accomplished"),
                        ),
                ),
                ToolDefinition(
                    name = "error",
                    description = "Signal that the task cannot be completed",
                    parameters =
                        listOf(
                            ToolParameter("reasoning", "string", "Why the task failed"),
                        ),
                ),
            )

        /**
         * Convert tool name back to ActionType.
         */
        fun toActionType(toolName: String): ActionType? =
            when (toolName.lowercase()) {
                "tap" -> ActionType.TAP
                "type" -> ActionType.TYPE
                "launch" -> ActionType.LAUNCH
                "navigate" -> ActionType.NAVIGATE
                "swipe" -> ActionType.SWIPE
                "done" -> ActionType.DONE
                "error" -> ActionType.ERROR
                else -> null
            }

        /**
         * Format tool definitions as a compact prompt snippet for LLM system prompts.
         */
        fun toPromptSnippet(): String =
            buildString {
                appendLine("Available actions (use structured tool calling):")
                for (tool in tools) {
                    val params =
                        tool.parameters.joinToString(", ") { p ->
                            val req = if (p.required) "" else "?"
                            val enums = p.enumValues?.let { " [${it.joinToString("|")}]" } ?: ""
                            "${p.name}$req: ${p.type}$enums"
                        }
                    appendLine("- ${tool.name}($params): ${tool.description}")
                }
            }.trim()

        /**
         * Serialize tool definitions to JSON (internal format).
         */
        fun toJson(): String = json.encodeToString(tools)

        /**
         * Generate Gemini-compatible functionDeclarations JSON.
         * Format: [{"name": "tap", "description": "...", "parameters": {"type": "OBJECT", "properties": {...}, "required": [...]}}]
         */
        fun toGeminiToolsJson(): String =
            buildString {
                append("[")
                tools.forEachIndexed { i, tool ->
                    if (i > 0) append(",")
                    append("{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\"")
                    append(",\"parameters\":{\"type\":\"OBJECT\",\"properties\":{")
                    val required = mutableListOf<String>()
                    tool.parameters.forEachIndexed { j, param ->
                        if (j > 0) append(",")
                        append("\"${param.name}\":{\"type\":\"STRING\",\"description\":\"${param.description}\"")
                        if (param.enumValues != null) {
                            append(",\"enum\":[${param.enumValues.joinToString(",") { "\"$it\"" }}]")
                        }
                        append("}")
                        if (param.required) required.add(param.name)
                    }
                    append("},\"required\":[${required.joinToString(",") { "\"$it\"" }}]}}")
                }
                append("]")
            }

        /**
         * Generate OpenAI-compatible tools JSON.
         * Format: [{"type": "function", "function": {"name": "tap", "description": "...", "parameters": {...}}}]
         */
        fun toOpenAIToolsJson(): String =
            buildString {
                append("[")
                tools.forEachIndexed { i, tool ->
                    if (i > 0) append(",")
                    append("{\"type\":\"function\",\"function\":{\"name\":\"${tool.name}\"")
                    append(",\"description\":\"${tool.description}\"")
                    append(",\"parameters\":{\"type\":\"object\",\"properties\":{")
                    val required = mutableListOf<String>()
                    tool.parameters.forEachIndexed { j, param ->
                        if (j > 0) append(",")
                        append("\"${param.name}\":{\"type\":\"string\",\"description\":\"${param.description}\"")
                        if (param.enumValues != null) {
                            append(",\"enum\":[${param.enumValues.joinToString(",") { "\"$it\"" }}]")
                        }
                        append("}")
                        if (param.required) required.add(param.name)
                    }
                    append("},\"required\":[${required.joinToString(",") { "\"$it\"" }}]}}}")
                }
                append("]")
            }

        companion object {
            private val json =
                Json {
                    encodeDefaults = true
                    prettyPrint = false
                }
        }
    }
