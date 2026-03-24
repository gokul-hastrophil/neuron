package ai.neuron.brain.client

import ai.neuron.BuildConfig
import ai.neuron.brain.StructuredToolCallParser
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.NeuronResult
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
    @SerialName("systemInstruction") val systemInstruction: GeminiContent? = null,
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = null,
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val thought: Boolean? = null,
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = null,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null,
    @SerialName("responseMimeType") val responseMimeType: String? = null,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

@Serializable
data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null,
)

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Path("model") model: String,
        @Query("key") apiKey: String,
        @Body body: RequestBody,
    ): okhttp3.ResponseBody
}

class GeminiFlashClient(
    private val okHttpClient: OkHttpClient,
    private val toolCallParser: StructuredToolCallParser? = null,
) {
    companion object {
        private const val TAG = "NeuronGemini"

        // Ordered list of models to try — separate per-model quotas on free tier.
        // Order: fast/cheap first (2.0-flash), better reasoning second (2.5-flash), lite variants last.
        private val MODEL_CHAIN =
            listOf(
                "gemini-2.0-flash",
                "gemini-2.5-flash",
                "gemini-2.5-flash-lite",
                "gemini-2.0-flash-lite",
            )
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    private val api: GeminiApi =
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .build()
            .create(GeminiApi::class.java)

    private val apiKey: String get() = BuildConfig.GEMINI_API_KEY

    suspend fun generate(
        systemPrompt: String,
        userMessage: String,
        temperature: Double = 0.2,
        maxTokens: Int = 2048,
    ): NeuronResult<LLMResponse> {
        // Try models in order, fall back on rate limit (429)
        for (model in MODEL_CHAIN) {
            val result = generateWithModel(model, systemPrompt, userMessage, temperature, maxTokens)
            if (result is NeuronResult.Error && result.message.contains("HTTP 429")) {
                Log.w(TAG, "$model rate-limited, trying next model...")
                continue
            }
            return result
        }
        return NeuronResult.Error("All Gemini models rate-limited")
    }

    private suspend fun generateWithModel(
        model: String,
        systemPrompt: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int,
    ): NeuronResult<LLMResponse> {
        return try {
            Log.d(TAG, "Calling Gemini model: $model")
            val request =
                GeminiRequest(
                    systemInstruction =
                        GeminiContent(
                            parts = listOf(GeminiPart(text = systemPrompt)),
                        ),
                    contents =
                        listOf(
                            GeminiContent(
                                role = "user",
                                parts = listOf(GeminiPart(text = userMessage)),
                            ),
                        ),
                    generationConfig =
                        GeminiGenerationConfig(
                            temperature = temperature,
                            maxOutputTokens = maxTokens,
                            responseMimeType = "application/json",
                        ),
                )

            val requestJson = json.encodeToString(GeminiRequest.serializer(), request)
            val body = requestJson.toRequestBody("application/json".toMediaType())

            val startTime = System.currentTimeMillis()
            val rawBody =
                try {
                    api.generateContent(model, apiKey, body)
                } catch (e: retrofit2.HttpException) {
                    val latency = System.currentTimeMillis() - startTime
                    val errorBody = e.response()?.errorBody()?.string() ?: "no body"
                    Log.e(TAG, "$model HTTP ${e.code()} (${latency}ms): ${errorBody.take(300)}")
                    return NeuronResult.Error("Gemini API HTTP ${e.code()}: ${errorBody.take(200)}", e)
                }
            val latency = System.currentTimeMillis() - startTime

            val responseJson = rawBody.string()
            Log.d(TAG, "$model raw response (${responseJson.length} chars, ${latency}ms)")
            val response = json.decodeFromString(GeminiResponse.serializer(), responseJson)

            val parts = response.candidates?.firstOrNull()?.content?.parts
            Log.d(TAG, "$model parts count: ${parts?.size}, thought parts: ${parts?.count { it.thought == true }}")

            // Gemini 2.5 Flash is a thinking model — skip thought parts, take the actual response
            val text =
                parts
                    ?.lastOrNull { it.thought != true }
                    ?.text
                    ?: parts?.lastOrNull()?.text
                    ?: return NeuronResult.Error("Empty response from Gemini $model")

            Log.d(TAG, "$model extracted text (${text.length} chars): ${text.take(300)}")

            // Try structured tool call parsing first (Gemini functionCall or legacy JSON)
            val llmResponse = parseResponse(text)
            if (llmResponse == null) {
                Log.w(TAG, "parseResponse returned null for: ${text.take(500)}")
                return NeuronResult.Error("Failed to parse Gemini response as action JSON: ${text.take(200)}")
            }

            val finalResponse =
                llmResponse.copy(
                    tier = "T2",
                    modelId = model,
                    latencyMs = latency,
                    tokensUsed = response.usageMetadata?.totalTokenCount,
                )
            val action = finalResponse.action
            Log.d(
                TAG,
                "$model success: action=${action?.actionType}, target=${action?.targetId}, value=${action?.value}",
            )
            NeuronResult.Success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "$model exception: ${e.javaClass.simpleName}: ${e.message}")
            NeuronResult.Error("Gemini $model API call failed: ${e.message}", e)
        }
    }

    /**
     * Parse response using structured tool call parser first, then legacy JSON fallback.
     */
    private fun parseResponse(text: String): LLMResponse? {
        val cleaned = stripMarkdownFences(text).trim()
        Log.d(TAG, "Parsing cleaned text (${cleaned.length} chars): ${cleaned.take(200)}")

        // Try structured tool call parsing (handles Gemini functionCall, OpenAI, FunctionGemma, and legacy)
        if (toolCallParser != null) {
            val result = toolCallParser.parse(cleaned)
            if (result is NeuronResult.Success) {
                Log.d(TAG, "Parsed via StructuredToolCallParser: ${result.data.actionType}")
                return LLMResponse(action = result.data)
            }
        }

        // Fallback: legacy parsing for backward compatibility
        return parseAsLLMResponseLegacy(cleaned)
    }

    private fun parseAsLLMResponseLegacy(cleaned: String): LLMResponse? {
        try {
            val resp = LLMResponse.fromJson(cleaned)
            if (resp.action != null) {
                Log.d(TAG, "Parsed as LLMResponse with action: ${resp.action.actionType}")
                return resp
            }
        } catch (e: Exception) {
            Log.d(TAG, "Not an LLMResponse: ${e.message?.take(100)}")
        }

        try {
            val action = json.decodeFromString(LLMAction.serializer(), cleaned)
            Log.d(TAG, "Parsed as bare LLMAction: ${action.actionType}")
            return LLMResponse(action = action)
        } catch (e: Exception) {
            Log.d(TAG, "Not an LLMAction either: ${e.message?.take(100)}")
        }

        return null
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
