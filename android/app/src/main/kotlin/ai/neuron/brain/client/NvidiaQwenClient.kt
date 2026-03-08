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
data class NvidiaRequest(
    val model: String = "qwen/qwen3.5-397b-a17b",
    val messages: List<NvidiaMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 16384,
    val temperature: Double = 0.6,
    @SerialName("top_p") val topP: Double = 0.95,
    @SerialName("top_k") val topK: Int = 20,
    @SerialName("presence_penalty") val presencePenalty: Double = 0.0,
    @SerialName("repetition_penalty") val repetitionPenalty: Double = 1.0,
    val stream: Boolean = false,
    @SerialName("chat_template_kwargs") val chatTemplateKwargs: ChatTemplateKwargs? = null,
)

@Serializable
data class NvidiaMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatTemplateKwargs(
    @SerialName("enable_thinking") val enableThinking: Boolean = true,
)

@Serializable
data class NvidiaResponse(
    val id: String? = null,
    val choices: List<NvidiaChoice>? = null,
    val usage: NvidiaUsage? = null,
    val model: String? = null,
)

@Serializable
data class NvidiaChoice(
    val index: Int? = null,
    val message: NvidiaMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class NvidiaUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
)

interface NvidiaApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: RequestBody,
    ): NvidiaResponse
}

class NvidiaQwenClient(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val api: NvidiaApi = Retrofit.Builder()
        .baseUrl("https://integrate.api.nvidia.com/")
        .client(okHttpClient)
        .build()
        .create(NvidiaApi::class.java)

    private val apiKey: String get() = BuildConfig.NVIDIA_API_KEY

    suspend fun generate(
        systemPrompt: String,
        userMessage: String,
        model: String = "qwen/qwen3.5-397b-a17b",
        temperature: Double = 0.6,
        maxTokens: Int = 16384,
        enableThinking: Boolean = true,
    ): NeuronResult<LLMResponse> {
        return try {
            val messages = mutableListOf<NvidiaMessage>()
            if (systemPrompt.isNotBlank()) {
                messages.add(NvidiaMessage(role = "system", content = systemPrompt))
            }
            messages.add(NvidiaMessage(role = "user", content = userMessage))

            val request = NvidiaRequest(
                model = model,
                messages = messages,
                maxTokens = maxTokens,
                temperature = temperature,
                stream = false,
                chatTemplateKwargs = ChatTemplateKwargs(enableThinking = enableThinking),
            )

            val requestJson = json.encodeToString(NvidiaRequest.serializer(), request)
            val body = requestJson.toRequestBody("application/json".toMediaType())

            val startTime = System.currentTimeMillis()
            val response = api.chatCompletion(
                authorization = "Bearer $apiKey",
                body = body,
            )
            val latency = System.currentTimeMillis() - startTime

            val text = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?: return NeuronResult.Error("Empty response from NVIDIA Qwen")

            // Strip thinking tags if present
            val cleanText = stripThinkingTags(text)

            val totalTokens = response.usage?.totalTokens ?: 0

            val llmResponse = try {
                LLMResponse.fromJson(cleanText)
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
            NeuronResult.Error("NVIDIA Qwen API call failed: ${e.message}", e)
        }
    }

    private fun stripThinkingTags(text: String): String {
        // Qwen with enable_thinking may wrap reasoning in <think>...</think>
        val thinkPattern = Regex("<think>[\\s\\S]*?</think>", RegexOption.DOT_MATCHES_ALL)
        return thinkPattern.replace(text, "").trim()
    }
}
