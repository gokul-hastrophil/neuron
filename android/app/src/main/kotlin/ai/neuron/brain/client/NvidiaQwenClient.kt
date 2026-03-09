package ai.neuron.brain.client

import ai.neuron.BuildConfig
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
        @Header("Accept") accept: String = "application/json",
        @Body body: RequestBody,
    ): okhttp3.ResponseBody
}

class NvidiaQwenClient(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "NeuronNvidia"
    }

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
        maxTokens: Int = 2048,
        enableThinking: Boolean = false,
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
                chatTemplateKwargs = if (enableThinking) ChatTemplateKwargs(enableThinking = true) else null,
            )

            val requestJson = json.encodeToString(NvidiaRequest.serializer(), request)
            val body = requestJson.toRequestBody("application/json".toMediaType())

            val startTime = System.currentTimeMillis()
            val rawBody = try {
                api.chatCompletion(
                    authorization = "Bearer $apiKey",
                    body = body,
                )
            } catch (e: retrofit2.HttpException) {
                val latency = System.currentTimeMillis() - startTime
                val errorBody = e.response()?.errorBody()?.string() ?: "no body"
                Log.e(TAG, "NVIDIA HTTP ${e.code()} (${latency}ms): ${errorBody.take(300)}")
                return NeuronResult.Error("NVIDIA API HTTP ${e.code()}: ${errorBody.take(200)}", e)
            }
            val latency = System.currentTimeMillis() - startTime

            val responseJson = rawBody.string()
            Log.d(TAG, "NVIDIA raw response (${responseJson.length} chars, ${latency}ms)")
            val response = json.decodeFromString(NvidiaResponse.serializer(), responseJson)

            val text = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?: return NeuronResult.Error("Empty response from NVIDIA Qwen")

            // Strip thinking tags if present
            val cleanText = stripThinkingTags(text)

            val totalTokens = response.usage?.totalTokens ?: 0

            val llmResponse = parseAsLLMResponse(cleanText)
            if (llmResponse == null) {
                Log.w(TAG, "parseAsLLMResponse returned null for: ${cleanText.take(500)}")
                return NeuronResult.Error("Failed to parse NVIDIA response as action JSON: ${cleanText.take(200)}")
            }

            val finalResponse = llmResponse.copy(
                tier = "T3",
                modelId = model,
                latencyMs = latency,
                tokensUsed = totalTokens,
            )
            Log.d(TAG, "NVIDIA success: action=${finalResponse.action?.actionType}, target=${finalResponse.action?.targetId}")
            NeuronResult.Success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "NVIDIA exception: ${e.javaClass.simpleName}: ${e.message}")
            NeuronResult.Error("NVIDIA Qwen API call failed: ${e.message}", e)
        }
    }

    private fun stripThinkingTags(text: String): String {
        // Qwen with enable_thinking may wrap reasoning in <think>...</think>
        val thinkPattern = Regex("<think>[\\s\\S]*?</think>", RegexOption.DOT_MATCHES_ALL)
        return thinkPattern.replace(text, "").trim()
    }

    /**
     * Try parsing as LLMResponse first; if the result has no action,
     * try parsing as a raw LLMAction and wrap it.
     */
    private fun parseAsLLMResponse(text: String): LLMResponse? {
        return try {
            val resp = LLMResponse.fromJson(text)
            if (resp.action != null) resp
            else {
                val action = json.decodeFromString(LLMAction.serializer(), text)
                LLMResponse(action = action)
            }
        } catch (_: Exception) {
            null
        }
    }
}
