package ai.neuron.brain

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.NeuronResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified parser for structured tool call responses from all LLM providers.
 *
 * Supports:
 * - Gemini functionCall: {"name": "tap", "args": {"target_id": "...", ...}}
 * - OpenAI tool_calls: {"name": "tap", "arguments": "{\"target_id\": \"...\"}"}
 * - FunctionGemma func(): tap(target_id="...", reasoning="...")
 * - Legacy free-text JSON: {"action_type": "tap", "target_id": "...", ...}
 */
@Singleton
class StructuredToolCallParser
    @Inject
    constructor(
        private val toolSchema: NeuronToolSchema,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
                explicitNulls = false
            }

        /**
         * Auto-detect format and parse. Tries structured formats first, legacy JSON last.
         */
        fun parse(rawResponse: String): NeuronResult<LLMAction> {
            val trimmed = rawResponse.trim()

            // Try FunctionGemma func() format: starts with a word followed by (
            if (trimmed.matches(Regex("""^\w+\(.*\)$""", RegexOption.DOT_MATCHES_ALL))) {
                val result = parseFunctionGemmaCall(trimmed)
                if (result is NeuronResult.Success) return result
            }

            // Try JSON-based formats
            if (trimmed.startsWith("{") || trimmed.startsWith("```")) {
                // Try Gemini functionCall: has "name" + "args"
                val geminiResult = parseGeminiFunctionCall(trimmed)
                if (geminiResult is NeuronResult.Success) return geminiResult

                // Try OpenAI tool_call: has "name" + "arguments" (string)
                val openAIResult = parseOpenAIToolCall(trimmed)
                if (openAIResult is NeuronResult.Success) return openAIResult

                // Try legacy JSON
                val legacyResult = parseLegacyJson(trimmed)
                if (legacyResult is NeuronResult.Success) return legacyResult
            }

            return NeuronResult.Error("Could not parse response in any known format: ${trimmed.take(200)}")
        }

        /**
         * Parse Gemini functionCall format:
         * {"name": "tap", "args": {"target_id": "com.app:id/btn", "reasoning": "..."}}
         */
        fun parseGeminiFunctionCall(rawJson: String): NeuronResult<LLMAction> {
            return try {
                val obj = json.parseToJsonElement(rawJson).jsonObject
                val name =
                    obj["name"]?.jsonPrimitive?.content
                        ?: return NeuronResult.Error("Missing 'name' in functionCall")
                val args = obj["args"]?.jsonObject ?: JsonObject(emptyMap())

                val actionType =
                    toolSchema.toActionType(name)
                        ?: return NeuronResult.Error("Unknown function: $name")

                NeuronResult.Success(buildAction(actionType, args))
            } catch (e: Exception) {
                NeuronResult.Error("Failed to parse Gemini functionCall: ${e.message}")
            }
        }

        /**
         * Parse OpenAI tool_calls format:
         * {"name": "tap", "arguments": "{\"target_id\": \"...\", \"reasoning\": \"...\"}"}
         */
        fun parseOpenAIToolCall(rawJson: String): NeuronResult<LLMAction> {
            return try {
                val obj = json.parseToJsonElement(rawJson).jsonObject
                val name =
                    obj["name"]?.jsonPrimitive?.content
                        ?: return NeuronResult.Error("Missing 'name' in tool call")
                val argumentsStr =
                    obj["arguments"]?.jsonPrimitive?.content
                        ?: return NeuronResult.Error("Missing 'arguments' in tool call")

                val args =
                    try {
                        json.parseToJsonElement(argumentsStr).jsonObject
                    } catch (e: Exception) {
                        return NeuronResult.Error("Failed to parse tool call arguments JSON: ${e.message}")
                    }

                val actionType =
                    toolSchema.toActionType(name)
                        ?: return NeuronResult.Error("Unknown function: $name")

                NeuronResult.Success(buildAction(actionType, args))
            } catch (e: Exception) {
                NeuronResult.Error("Failed to parse OpenAI tool call: ${e.message}")
            }
        }

        /**
         * Parse FunctionGemma func() format:
         * tap(target_id="com.app:id/btn", reasoning="Tapping button")
         */
        fun parseFunctionGemmaCall(raw: String): NeuronResult<LLMAction> {
            val trimmed = raw.trim()
            val funcCallRegex = Regex("""^(\w+)\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)
            val match =
                funcCallRegex.find(trimmed)
                    ?: return NeuronResult.Error("Could not parse func() call: $trimmed")

            val functionName = match.groupValues[1]
            val argsString = match.groupValues[2]

            val actionType =
                toolSchema.toActionType(functionName)
                    ?: return NeuronResult.Error("Unknown function: $functionName")

            val args = parseNamedArgs(argsString)

            return NeuronResult.Success(
                LLMAction(
                    actionType = actionType,
                    targetId = args["target_id"],
                    targetText = args["target_text"],
                    value = args["value"],
                    reasoning = args["reasoning"],
                    confidence = 0.85,
                ),
            )
        }

        /**
         * Parse legacy free-text JSON format (bare LLMAction or wrapped LLMResponse).
         */
        fun parseLegacyJson(raw: String): NeuronResult<LLMAction> {
            val cleaned = stripMarkdownFences(raw).trim()

            // Try as wrapped LLMResponse
            try {
                val resp = LLMResponse.fromJson(cleaned)
                if (resp.action != null) {
                    return NeuronResult.Success(resp.action)
                }
            } catch (_: Exception) {
                // Not an LLMResponse
            }

            // Try as bare LLMAction
            try {
                val action = json.decodeFromString(LLMAction.serializer(), cleaned)
                return NeuronResult.Success(action)
            } catch (_: Exception) {
                // Not an LLMAction
            }

            return NeuronResult.Error("Failed to parse legacy JSON: ${cleaned.take(200)}")
        }

        private fun buildAction(
            actionType: ActionType,
            args: JsonObject,
        ): LLMAction {
            // Extract LLM-provided confidence if present; fall back to 0.85 baseline
            // for structured tool calls (higher than legacy JSON default of 0.0).
            val llmConfidence =
                args["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull()
            val effectiveConfidence = llmConfidence ?: DEFAULT_STRUCTURED_CONFIDENCE

            return LLMAction(
                actionType = actionType,
                targetId = args["target_id"]?.jsonPrimitive?.content,
                targetText = args["target_text"]?.jsonPrimitive?.content,
                value = args["value"]?.jsonPrimitive?.content,
                reasoning = args["reasoning"]?.jsonPrimitive?.content,
                confidence = effectiveConfidence.coerceIn(0.0, 1.0),
            )
        }

        companion object {
            /** Default confidence for structured tool calls when LLM doesn't provide one. */
            const val DEFAULT_STRUCTURED_CONFIDENCE = 0.85
        }

        private fun parseNamedArgs(argsString: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            val argRegex = Regex("""(\w+)\s*=\s*["']([^"']*)["']""")
            for (matchResult in argRegex.findAll(argsString)) {
                result[matchResult.groupValues[1]] = matchResult.groupValues[2]
            }
            return result
        }

        private fun stripMarkdownFences(text: String): String {
            val trimmed = text.trim()
            if (!trimmed.startsWith("```")) return trimmed
            val lines = trimmed.lines()
            val start = if (lines.first().startsWith("```")) 1 else 0
            val end = if (lines.last().trim() == "```") lines.size - 1 else lines.size
            return lines.subList(start, end).joinToString("\n")
        }
    }
