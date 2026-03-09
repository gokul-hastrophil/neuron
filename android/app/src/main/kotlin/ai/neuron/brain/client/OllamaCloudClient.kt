package ai.neuron.brain.client

import ai.neuron.BuildConfig
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.NeuronResult
import android.util.Log
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OllamaCloudApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body body: okhttp3.RequestBody,
    ): okhttp3.ResponseBody
}

class OllamaCloudClient(
    private val okHttpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "NeuronOllama"
        private const val DEFAULT_MODEL = "qwen3-vl:235b-cloud"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private val api: OllamaCloudApi = Retrofit.Builder()
        .baseUrl("https://ollama.com/")
        .client(okHttpClient)
        .build()
        .create(OllamaCloudApi::class.java)

    private val apiKey: String get() = BuildConfig.OLLAMA_API_KEY

    suspend fun generate(
        systemPrompt: String,
        userMessage: String,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.2,
        maxTokens: Int = 2048,
    ): NeuronResult<LLMResponse> {
        if (apiKey.isBlank()) {
            return NeuronResult.Error("Ollama Cloud API key not configured")
        }

        return try {
            Log.d(TAG, "Calling Ollama Cloud model: $model")
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
                Log.e(TAG, "$model HTTP ${e.code()} (${latency}ms): ${errorBody.take(300)}")
                return NeuronResult.Error("Ollama Cloud HTTP ${e.code()}: ${errorBody.take(200)}", e)
            }
            val latency = System.currentTimeMillis() - startTime

            val responseJson = rawBody.string()
            Log.d(TAG, "$model raw response (${responseJson.length} chars, ${latency}ms)")
            val response = json.decodeFromString(NvidiaResponse.serializer(), responseJson)

            val text = response.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?: return NeuronResult.Error("Empty response from Ollama Cloud $model")

            val totalTokens = response.usage?.totalTokens ?: 0

            val llmResponse = parseAsLLMResponse(text)
            if (llmResponse == null) {
                Log.w(TAG, "parseAsLLMResponse returned null for: ${text.take(500)}")
                return NeuronResult.Error("Failed to parse Ollama response as action JSON: ${text.take(200)}")
            }

            val finalResponse = llmResponse.copy(
                tier = "T3",
                modelId = model,
                latencyMs = latency,
                tokensUsed = totalTokens,
            )
            Log.d(TAG, "$model success: action=${finalResponse.action?.actionType}, target=${finalResponse.action?.targetId}")
            NeuronResult.Success(finalResponse)
        } catch (e: Exception) {
            Log.e(TAG, "$model exception: ${e.javaClass.simpleName}: ${e.message}")
            NeuronResult.Error("Ollama Cloud $model failed: ${e.message}", e)
        }
    }

    private fun parseAsLLMResponse(text: String): LLMResponse? {
        val cleaned = stripMarkdownFences(text).trim()
        Log.d(TAG, "Parsing cleaned text (${cleaned.length} chars): ${cleaned.take(200)}")

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
