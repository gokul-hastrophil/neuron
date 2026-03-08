package ai.neuron.brain.client

import ai.neuron.BuildConfig
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.NeuronResult
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
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body body: RequestBody,
    ): GeminiResponse
}

class GeminiFlashClient(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val api: GeminiApi = Retrofit.Builder()
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
        return try {
            val request = GeminiRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt)),
                ),
                contents = listOf(
                    GeminiContent(
                        role = "user",
                        parts = listOf(GeminiPart(text = userMessage)),
                    ),
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = temperature,
                    maxOutputTokens = maxTokens,
                    responseMimeType = "application/json",
                ),
            )

            val requestJson = json.encodeToString(GeminiRequest.serializer(), request)
            val body = requestJson.toRequestBody("application/json".toMediaType())

            val startTime = System.currentTimeMillis()
            val response = api.generateContent(apiKey, body)
            val latency = System.currentTimeMillis() - startTime

            val text = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: return NeuronResult.Error("Empty response from Gemini")

            val llmResponse = try {
                LLMResponse.fromJson(text)
            } catch (e: Exception) {
                // If the response isn't a valid LLMResponse JSON, wrap the raw text
                LLMResponse(
                    tier = "T2",
                    modelId = "gemini-2.5-flash",
                    latencyMs = latency,
                    tokensUsed = response.usageMetadata?.totalTokenCount,
                )
            }

            NeuronResult.Success(
                llmResponse.copy(
                    tier = "T2",
                    modelId = "gemini-2.5-flash",
                    latencyMs = latency,
                    tokensUsed = response.usageMetadata?.totalTokenCount,
                ),
            )
        } catch (e: Exception) {
            NeuronResult.Error("Gemini Flash API call failed: ${e.message}", e)
        }
    }
}
