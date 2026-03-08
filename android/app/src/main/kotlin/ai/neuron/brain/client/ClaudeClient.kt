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
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class ClaudeRequest(
    val model: String = "claude-sonnet-4-5-20250514",
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    val system: String? = null,
    val messages: List<ClaudeMessage>,
    val temperature: Double? = null,
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ClaudeResponse(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val content: List<ClaudeContentBlock>? = null,
    val model: String? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: ClaudeUsage? = null,
)

@Serializable
data class ClaudeContentBlock(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens") val inputTokens: Int? = null,
    @SerialName("output_tokens") val outputTokens: Int? = null,
)

interface ClaudeApi {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("content-type") contentType: String = "application/json",
        @Body body: RequestBody,
    ): ClaudeResponse
}

class ClaudeClient(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val api: ClaudeApi = Retrofit.Builder()
        .baseUrl("https://api.anthropic.com/")
        .client(okHttpClient)
        .build()
        .create(ClaudeApi::class.java)

    private val apiKey: String get() = BuildConfig.ANTHROPIC_API_KEY

    suspend fun generate(
        systemPrompt: String,
        userMessage: String,
        model: String = "claude-sonnet-4-5-20250514",
        temperature: Double = 0.2,
        maxTokens: Int = 2048,
    ): NeuronResult<LLMResponse> {
        return try {
            val request = ClaudeRequest(
                model = model,
                maxTokens = maxTokens,
                system = systemPrompt,
                temperature = temperature,
                messages = listOf(
                    ClaudeMessage(role = "user", content = userMessage),
                ),
            )

            val requestJson = json.encodeToString(ClaudeRequest.serializer(), request)
            val body = requestJson.toRequestBody("application/json".toMediaType())

            val startTime = System.currentTimeMillis()
            val response = api.createMessage(apiKey = apiKey, body = body)
            val latency = System.currentTimeMillis() - startTime

            val text = response.content
                ?.firstOrNull { it.type == "text" }
                ?.text
                ?: return NeuronResult.Error("Empty response from Claude")

            val totalTokens = (response.usage?.inputTokens ?: 0) +
                (response.usage?.outputTokens ?: 0)

            val llmResponse = try {
                LLMResponse.fromJson(text)
            } catch (e: Exception) {
                LLMResponse(
                    tier = "T3",
                    modelId = model,
                    latencyMs = latency,
                    tokensUsed = totalTokens,
                )
            }

            NeuronResult.Success(
                llmResponse.copy(
                    tier = "T3",
                    modelId = model,
                    latencyMs = latency,
                    tokensUsed = totalTokens,
                ),
            )
        } catch (e: Exception) {
            NeuronResult.Error("Claude API call failed: ${e.message}", e)
        }
    }
}
