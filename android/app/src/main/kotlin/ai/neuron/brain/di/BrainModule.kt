package ai.neuron.brain.di

import ai.neuron.BuildConfig
import ai.neuron.accessibility.ActionExecutor
import ai.neuron.accessibility.NeuronAccessibilityService
import ai.neuron.accessibility.UITreeReader
import ai.neuron.accessibility.model.NeuronAction
import ai.neuron.accessibility.model.ActionResult
import ai.neuron.accessibility.model.ScrollDirection
import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.ActionMapper
import ai.neuron.brain.AppResolver
import ai.neuron.brain.PlanAndExecuteEngine
import ai.neuron.brain.client.GeminiFlashClient
import ai.neuron.brain.client.NvidiaQwenClient
import ai.neuron.brain.client.OllamaCloudClient
import ai.neuron.brain.client.OpenRouterClient
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import ai.neuron.sdk.ToolRegistry
import android.content.Context
import android.util.Log
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrainModule {

    private const val TAG = "NeuronBrainModule"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                val sanitized = sanitizeApiKeys(message)
                android.util.Log.d("NeuronHttp", sanitized)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGeminiFlashClient(okHttpClient: OkHttpClient): GeminiFlashClient =
        GeminiFlashClient(okHttpClient)

    @Provides
    @Singleton
    fun provideNvidiaQwenClient(okHttpClient: OkHttpClient): NvidiaQwenClient =
        NvidiaQwenClient(okHttpClient)

    @Provides
    @Singleton
    fun provideOpenRouterClient(okHttpClient: OkHttpClient): OpenRouterClient =
        OpenRouterClient(okHttpClient)

    @Provides
    @Singleton
    fun provideOllamaCloudClient(okHttpClient: OkHttpClient): OllamaCloudClient =
        OllamaCloudClient(okHttpClient)

    @Provides
    @Singleton
    fun provideUIProvider(): PlanAndExecuteEngine.UIProvider =
        object : PlanAndExecuteEngine.UIProvider {
            override suspend fun getCurrentUITree(): UITree =
                withContext(Dispatchers.Default) {
                    val service = NeuronAccessibilityService.instance
                    if (service == null) {
                        Log.w(TAG, "UIProvider: AccessibilityService not active, returning empty UITree")
                        return@withContext UITree.empty()
                    }
                    UITreeReader(service).getUITree()
                }
        }

    @Provides
    @Singleton
    fun provideActionDispatcher(
        @ApplicationContext context: Context,
        appResolver: AppResolver,
        toolRegistry: ToolRegistry,
    ): PlanAndExecuteEngine.ActionDispatcher =
        object : PlanAndExecuteEngine.ActionDispatcher {
            override suspend fun dispatch(action: LLMAction): Boolean =
                withContext(Dispatchers.Default) {
                    // Handle tool_call actions via ToolRegistry (no AccessibilityService needed)
                    if (action.actionType == ActionType.TOOL_CALL) {
                        val toolName = action.value ?: return@withContext false
                        val paramsJson = action.targetText ?: "{}"
                        val params = try {
                            kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(paramsJson)
                        } catch (e: Exception) {
                            emptyMap()
                        }
                        val result = toolRegistry.invoke(toolName, params)
                        if (result == null) {
                            Log.w(TAG, "ActionDispatcher: tool '$toolName' not found in registry")
                            return@withContext false
                        }
                        Log.i(TAG, "ActionDispatcher: tool '$toolName' returned: $result")
                        return@withContext true
                    }

                    val service = NeuronAccessibilityService.instance
                    if (service == null) {
                        Log.w(TAG, "ActionDispatcher: AccessibilityService not active, dropping action ${action.actionType}")
                        return@withContext false
                    }
                    val neuronAction = mapToNeuronAction(action, context.packageManager, appResolver)
                    if (neuronAction == null) {
                        Log.w(TAG, "ActionDispatcher: no NeuronAction mapping for ${action.actionType}, dropping")
                        return@withContext false
                    }
                    val result = ActionExecutor(service).execute(neuronAction)
                    if (result is ActionResult.Error) {
                        Log.e(TAG, "ActionDispatcher: execution failed: ${result.message}")
                    }
                    result is ActionResult.Success
                }
        }

    private fun mapToNeuronAction(action: LLMAction, pm: PackageManager, appResolver: AppResolver): NeuronAction? =
        when (action.actionType) {
            ActionType.TAP -> {
                val nodeId = action.targetId ?: return null
                NeuronAction.Tap(nodeId = nodeId)
            }
            ActionType.TYPE -> {
                val nodeId = action.targetId ?: return null
                val text = action.value ?: return null
                NeuronAction.TypeText(nodeId = nodeId, text = text)
            }
            ActionType.SWIPE -> {
                val direction = when (action.value?.lowercase()) {
                    "up" -> ScrollDirection.UP
                    "left" -> ScrollDirection.LEFT
                    "right" -> ScrollDirection.RIGHT
                    else -> ScrollDirection.DOWN
                }
                NeuronAction.Scroll(nodeId = "", direction = direction)
            }
            ActionType.LAUNCH -> {
                val value = action.value ?: return null
                val packageName = appResolver.resolve(value, pm) ?: run {
                    Log.w(TAG, "ActionDispatcher: could not resolve app '$value' to package name")
                    return null
                }
                NeuronAction.LaunchApp(packageName = packageName)
            }
            ActionType.NAVIGATE -> ActionMapper.mapNavigate(action.value)
            // Terminal/meta action types — not dispatched to the accessibility layer
            ActionType.DONE,
            ActionType.ERROR,
            ActionType.CONFIRM,
            ActionType.WAIT,
            ActionType.TOOL_CALL,
            -> null
        }

    private fun sanitizeApiKeys(message: String): String {
        var sanitized = message
        val geminiKey = BuildConfig.GEMINI_API_KEY
        if (geminiKey.isNotEmpty()) {
            sanitized = sanitized.replace(geminiKey, "***GEMINI_KEY***")
        }
        val nvidiaKey = BuildConfig.NVIDIA_API_KEY
        if (nvidiaKey.isNotEmpty()) {
            sanitized = sanitized.replace(nvidiaKey, "***NVIDIA_KEY***")
        }
        val openRouterKey = BuildConfig.OPENROUTER_API_KEY
        if (openRouterKey.isNotEmpty()) {
            sanitized = sanitized.replace(openRouterKey, "***OPENROUTER_KEY***")
        }
        val ollamaKey = BuildConfig.OLLAMA_API_KEY
        if (ollamaKey.isNotEmpty()) {
            sanitized = sanitized.replace(ollamaKey, "***OLLAMA_KEY***")
        }
        return sanitized
    }
}
