package ai.neuron.brain.llm

import ai.neuron.brain.NeuronToolSchema
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.NeuronResult
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device function calling client using FunctionGemma (270M params).
 *
 * FunctionGemma is a small model specifically fine-tuned for function/tool calling.
 * It takes a user command + tool definitions and returns a structured tool call
 * (function name + arguments). This replaces regex pattern matching at T1 tier
 * with 85% accuracy on mobile actions.
 *
 * Deployment: LiteRT-LM runtime, model file in assets/models/ (~60MB).
 *
 * Until LiteRT-LM SDK is available as a Gradle dependency, this client uses
 * the [InferenceEngine] interface for pluggable backends (allows mock testing
 * and future runtime swapping).
 */
@Singleton
class FunctionGemmaClient
    @Inject
    constructor(
        private val toolSchema: NeuronToolSchema,
    ) {
        /**
         * Abstraction over the on-device inference runtime.
         * Implementations: LiteRtLmEngine (production), MockEngine (testing).
         */
        interface InferenceEngine {
            /** Run inference and return raw text output. */
            suspend fun generate(prompt: String): String

            /** Whether the engine is ready (model loaded). */
            fun isReady(): Boolean
        }

        var engine: InferenceEngine? = null
            private set

        fun initialize(inferenceEngine: InferenceEngine) {
            engine = inferenceEngine
            Log.i(TAG, "FunctionGemma engine initialized, ready=${inferenceEngine.isReady()}")
        }

        fun isAvailable(): Boolean = engine?.isReady() == true

        /**
         * Generate a structured action from a natural language command.
         *
         * @param command User's natural language command
         * @param screenSummary Optional compact screen context (from UITreeTools.getScreenSummary)
         * @return Parsed LLMAction or error
         */
        suspend fun generateAction(
            command: String,
            screenSummary: String? = null,
        ): NeuronResult<LLMResponse> {
            val eng = engine ?: return NeuronResult.Error("FunctionGemma engine not initialized")
            if (!eng.isReady()) return NeuronResult.Error("FunctionGemma model not loaded")

            val prompt = buildPrompt(command, screenSummary)

            return try {
                val rawOutput = eng.generate(prompt)
                parseToolCall(rawOutput)
            } catch (e: Exception) {
                Log.e(TAG, "FunctionGemma inference failed", e)
                NeuronResult.Error("FunctionGemma inference failed: ${e.message}")
            }
        }

        private fun buildPrompt(
            command: String,
            screenSummary: String?,
        ): String =
            buildString {
                appendLine("You are a phone assistant. Call one function to handle the user's command.")
                appendLine()
                appendLine(toolSchema.toPromptSnippet())
                appendLine()
                if (screenSummary != null) {
                    appendLine("Current screen: $screenSummary")
                    appendLine()
                }
                appendLine("User command: $command")
                appendLine()
                appendLine("Call exactly one function. Output format: function_name(param1=\"value1\", param2=\"value2\")")
            }

        /**
         * Parse FunctionGemma's output into a structured LLMAction.
         *
         * Expected format: `function_name(param1="value1", param2="value2")`
         */
        internal fun parseToolCall(rawOutput: String): NeuronResult<LLMResponse> {
            val trimmed = rawOutput.trim()

            // Match: function_name(...)
            val funcCallRegex = Regex("""^(\w+)\((.*)\)$""", RegexOption.DOT_MATCHES_ALL)
            val match =
                funcCallRegex.find(trimmed)
                    ?: return NeuronResult.Error("Could not parse tool call: $trimmed")

            val functionName = match.groupValues[1]
            val argsString = match.groupValues[2]

            val actionType =
                toolSchema.toActionType(functionName)
                    ?: return NeuronResult.Error("Unknown function: $functionName")

            // Parse named arguments: key="value"
            val args = parseNamedArgs(argsString)

            val action =
                LLMAction(
                    actionType = actionType,
                    targetId = args["target_id"],
                    targetText = args["target_text"],
                    value = args["value"],
                    reasoning = args["reasoning"],
                    // FunctionGemma baseline confidence
                    confidence = 0.85,
                )

            return NeuronResult.Success(
                LLMResponse(
                    action = action,
                    tier = "T1",
                    modelId = "function-gemma",
                ),
            )
        }

        private fun parseNamedArgs(argsString: String): Map<String, String> {
            val result = mutableMapOf<String, String>()
            // Match: key="value" or key='value'
            val argRegex = Regex("""(\w+)\s*=\s*["']([^"']*)["']""")
            for (matchResult in argRegex.findAll(argsString)) {
                result[matchResult.groupValues[1]] = matchResult.groupValues[2]
            }
            return result
        }

        companion object {
            private const val TAG = "FunctionGemma"
        }
    }
