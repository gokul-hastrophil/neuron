package ai.neuron.brain.client

import ai.neuron.brain.StructuredToolCallParser
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class ProxyChatRequest(
    val tier: String,
    val messages: List<ProxyChatMessage>,
    val temperature: Double = 0.2,
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    val stream: Boolean = false,
)

@Serializable
data class ProxyChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ProxyChatResponse(
    val text: String? = null,
    val model: String? = null,
    val tier: String? = null,
    @SerialName("tokens_used") val tokensUsed: Int? = null,
    @SerialName("latency_ms") val latencyMs: Long? = null,
    val error: String? = null,
)

interface LlmProxyApi {
    @POST("v1/llm/chat")
    suspend fun chat(
        @Header("Authorization") authorization: String,
        @Body body: okhttp3.RequestBody,
    ): okhttp3.ResponseBody
}

class LlmProxyClient(
    private val okHttpClient: OkHttpClient,
    private val toolCallParser: StructuredToolCallParser? = null,
    serverUrl: String = DEFAULT_SERVER_URL,
    private val deviceTokenProvider: () -> String = { "" },
) {
    companion object {
        private const val TAG = "NeuronLlmProxy"
        const val DEFAULT_SERVER_URL = "http://localhost:8384"
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    private var api: LlmProxyApi = buildApi(serverUrl)

    private fun buildApi(baseUrl: String): LlmProxyApi {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .build()
            .create(LlmProxyApi::class.java)
    }

    fun updateServerUrl(newUrl: String) {
        api = buildApi(newUrl)
    }

    suspend fun generate(
        tier: LLMTier,
        systemPrompt: String,
        userMessage: String,
        temperature: Double = 0.2,
        maxTokens: Int = 2048,
    ): NeuronResult<LLMResponse> {
        val token = deviceTokenProvider()
        if (token.isBlank()) {
            return NeuronResult.Error("Device token not configured — set it in Settings")
        }

        return try {
            val messages = mutableListOf<ProxyChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messages.add(ProxyChatMessage(role = "system", content = systemPrompt))
            }
            messages.add(ProxyChatMessage(role = "user", content = userMessage))

            val request =
                ProxyChatRequest(
                    tier = tier.name,
                    messages = messages,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    stream = false,
                )

            val requestJson = json.encodeToString(ProxyChatRequest.serializer(), request)
            val body = requestJson.toRequestBody("application/json".toMediaType())

            val startTime = System.currentTimeMillis()
            val rawBody =
                try {
                    api.chat(
                        authorization = "Bearer $token",
                        body = body,
                    )
                } catch (e: retrofit2.HttpException) {
                    val latency = System.currentTimeMillis() - startTime
                    val errorBody = e.response()?.errorBody()?.string() ?: "no body"
                    Log.e(TAG, "Proxy HTTP ${e.code()} (${latency}ms): ${errorBody.take(300)}")
                    return NeuronResult.Error(
                        "LLM proxy HTTP ${e.code()}: ${errorBody.take(200)}",
                        e,
                    )
                }
            val latency = System.currentTimeMillis() - startTime

            val responseJson = rawBody.string()
            Log.d(TAG, "Proxy response (${responseJson.length} chars, ${latency}ms)")

            val response = json.decodeFromString(ProxyChatResponse.serializer(), responseJson)

            if (response.error != null) {
                Log.e(TAG, "Proxy returned error: ${response.error}")
                return NeuronResult.Error("LLM proxy error: ${response.error}")
            }

            val text =
                response.text
                    ?: return NeuronResult.Error("Empty response from LLM proxy")

            val llmResponse = parseResponse(text)
            if (llmResponse == null) {
                Log.w(TAG, "Failed to parse proxy response: ${text.take(500)}")
                return NeuronResult.Error(
                    "Failed to parse LLM proxy response as action JSON: ${text.take(200)}",
                )
            }

            val finalResponse =
                llmResponse.copy(
                    tier = response.tier ?: tier.name,
                    modelId = response.model ?: "proxy",
                    latencyMs = response.latencyMs ?: latency,
                    tokensUsed = response.tokensUsed,
                )
            Log.d(
                TAG,
                "Proxy success: tier=${finalResponse.tier} model=${finalResponse.modelId} " +
                    "action=${finalResponse.action?.actionType}",
            )
            NeuronResult.Success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Proxy exception: ${e.javaClass.simpleName}: ${e.message}")
            NeuronResult.Error("LLM proxy call failed: ${e.message}", e)
        }
    }

    private fun parseResponse(text: String): LLMResponse? {
        val cleaned = stripMarkdownFences(text).trim()

        if (toolCallParser != null) {
            val result = toolCallParser.parse(cleaned)
            if (result is NeuronResult.Success) {
                return LLMResponse(action = result.data)
            }
        }

        return parseAsLLMResponseLegacy(cleaned)
    }

    private fun parseAsLLMResponseLegacy(cleaned: String): LLMResponse? {
        try {
            val resp = LLMResponse.fromJson(cleaned)
            if (resp.action != null) return resp
        } catch (_: Exception) {
            // Not an LLMResponse — try LLMAction next
        }

        try {
            val action = json.decodeFromString(LLMAction.serializer(), cleaned)
            return LLMResponse(action = action)
        } catch (_: Exception) {
            // Not an LLMAction either
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
