package ai.neuron.brain.llm

import ai.neuron.brain.NeuronToolSchema
import ai.neuron.brain.StructuredToolCallParser
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.NeuronResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device Gemma 3n client for T4 (sensitive) and T1.5 (complex on-device) tasks.
 *
 * Gemma 3n has ~2B effective parameters (MatFormer E2B configuration),
 * supports multimodal input (text + image), and handles 140 languages.
 *
 * Key advantages over FunctionGemma:
 * - Visual understanding: can process screenshots of sensitive screens
 * - Complex reasoning: handles multi-step tasks FunctionGemma can't
 * - Multimodal: text + image inputs via LiteRT-LM
 *
 * Deployment: LiteRT-LM runtime with MatFormer E2B configuration.
 */
@Singleton
class Gemma3nClient
    @Inject
    constructor(
        private val toolSchema: NeuronToolSchema,
        private val toolCallParser: StructuredToolCallParser,
    ) {
        /**
         * Abstraction over the on-device inference runtime.
         * Supports both text-only and multimodal (text+image) inference.
         */
        interface InferenceEngine {
            /** Run text-only inference. */
            suspend fun generate(prompt: String): String

            /** Run multimodal inference with text prompt and image bytes (JPEG/PNG). */
            suspend fun generateWithImage(
                prompt: String,
                imageBytes: ByteArray,
            ): String

            /** Whether the engine is ready (model loaded). */
            fun isReady(): Boolean

            /** Whether the engine supports multimodal (image) input. */
            fun supportsMultimodal(): Boolean = false
        }

        var engine: InferenceEngine? = null
            private set

        fun initialize(inferenceEngine: InferenceEngine) {
            engine = inferenceEngine
        }

        fun isAvailable(): Boolean = engine?.isReady() == true

        fun supportsMultimodal(): Boolean = engine?.supportsMultimodal() == true

        /**
         * Generate an action from text-only context (T1.5 mode).
         */
        suspend fun generateAction(
            command: String,
            screenContext: String? = null,
        ): NeuronResult<LLMResponse> {
            val eng = engine ?: return NeuronResult.Error("Gemma 3n engine not initialized")
            if (!eng.isReady()) return NeuronResult.Error("Gemma 3n model not loaded")

            val prompt = buildTextPrompt(command, screenContext)

            return try {
                val rawOutput = eng.generate(prompt)
                parseOutput(rawOutput, "T1.5")
            } catch (e: Exception) {
                NeuronResult.Error("Gemma 3n inference failed: ${e.message}")
            }
        }

        /**
         * Generate an action from text + screenshot (T4 sensitive mode).
         * This is the key feature: sensitive screens send a screenshot to the
         * on-device model instead of sending UI tree text to the cloud.
         */
        suspend fun generateFromScreenshot(
            command: String,
            screenshotBytes: ByteArray,
        ): NeuronResult<LLMResponse> {
            val eng = engine ?: return NeuronResult.Error("Gemma 3n engine not initialized")
            if (!eng.isReady()) return NeuronResult.Error("Gemma 3n model not loaded")
            if (!eng.supportsMultimodal()) {
                return NeuronResult.Error("Gemma 3n engine does not support multimodal input")
            }

            val prompt = buildImagePrompt(command)

            return try {
                val rawOutput = eng.generateWithImage(prompt, screenshotBytes)
                parseOutput(rawOutput, "T4")
            } catch (e: Exception) {
                NeuronResult.Error("Gemma 3n multimodal inference failed: ${e.message}")
            }
        }

        private fun buildTextPrompt(
            command: String,
            screenContext: String?,
        ): String =
            buildString {
                appendLine("You are a phone assistant. Analyze the screen and select the best action.")
                appendLine()
                appendLine(toolSchema.toPromptSnippet())
                appendLine()
                if (screenContext != null) {
                    appendLine("Current screen: $screenContext")
                    appendLine()
                }
                appendLine("User command: $command")
                appendLine()
                appendLine("Call exactly one function.")
            }

        private fun buildImagePrompt(command: String): String =
            buildString {
                appendLine("You are a phone assistant. Look at the screenshot and select the best action.")
                appendLine("IMPORTANT: This is a sensitive screen. All processing is on-device only.")
                appendLine()
                appendLine(toolSchema.toPromptSnippet())
                appendLine()
                appendLine("User command: $command")
                appendLine()
                appendLine("Call exactly one function based on what you see in the screenshot.")
            }

        private fun parseOutput(
            rawOutput: String,
            tier: String,
        ): NeuronResult<LLMResponse> {
            val actionResult = toolCallParser.parse(rawOutput)
            return when (actionResult) {
                is NeuronResult.Success ->
                    NeuronResult.Success(
                        LLMResponse(
                            action = actionResult.data,
                            tier = tier,
                            modelId = "gemma-3n-e2b",
                        ),
                    )
                is NeuronResult.Error -> NeuronResult.Error("Gemma 3n parse failed: ${actionResult.message}")
            }
        }

        companion object {
            private const val TAG = "Gemma3n"
        }
    }
