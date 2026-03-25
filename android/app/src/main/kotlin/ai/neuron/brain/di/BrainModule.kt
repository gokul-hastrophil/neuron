package ai.neuron.brain.di

import ai.neuron.BuildConfig
import ai.neuron.accessibility.NeuronAccessibilityService
import ai.neuron.accessibility.UITreeReader
import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.AppResolver
import ai.neuron.brain.PlanAndExecuteEngine
import ai.neuron.brain.StructuredToolCallParser
import ai.neuron.brain.client.GeminiFlashClient
import ai.neuron.brain.client.NvidiaQwenClient
import ai.neuron.brain.client.OllamaCloudClient
import ai.neuron.brain.client.OpenRouterClient
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import ai.neuron.nerve.DualPathExecutor
import ai.neuron.sdk.ToolRegistry
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor =
                HttpLoggingInterceptor { message ->
                    val sanitized = sanitizeApiKeys(message)
                    android.util.Log.d("NeuronHttp", sanitized)
                }.apply {
                    // SECURITY: Never use Level.BODY — it leaks full UITree JSON,
                    // system prompts, and user commands to logcat where any app
                    // with READ_LOGS permission (or ADB access) can read them.
                    level = HttpLoggingInterceptor.Level.HEADERS
                }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideGeminiFlashClient(
        okHttpClient: OkHttpClient,
        toolCallParser: StructuredToolCallParser,
    ): GeminiFlashClient = GeminiFlashClient(okHttpClient, toolCallParser)

    @Provides
    @Singleton
    fun provideNvidiaQwenClient(okHttpClient: OkHttpClient): NvidiaQwenClient = NvidiaQwenClient(okHttpClient)

    @Provides
    @Singleton
    fun provideOpenRouterClient(okHttpClient: OkHttpClient): OpenRouterClient = OpenRouterClient(okHttpClient)

    @Provides
    @Singleton
    fun provideOllamaCloudClient(
        okHttpClient: OkHttpClient,
        toolCallParser: StructuredToolCallParser,
    ): OllamaCloudClient = OllamaCloudClient(okHttpClient, toolCallParser)

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
        dualPathExecutor: DualPathExecutor,
    ): PlanAndExecuteEngine.ActionDispatcher =
        object : PlanAndExecuteEngine.ActionDispatcher {
            override suspend fun dispatch(action: LLMAction): Boolean =
                withContext(Dispatchers.Default) {
                    // Handle tool_call actions via ToolRegistry (no AccessibilityService needed)
                    if (action.actionType == ActionType.TOOL_CALL) {
                        val toolName = action.value ?: return@withContext false
                        val paramsJson = action.targetText ?: "{}"
                        val params =
                            try {
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

                    // Route through DualPathExecutor: tries AppFunctions first, falls back to Accessibility
                    val targetPackage = resolveTargetPackage(action, context.packageManager, appResolver)
                    val result = dualPathExecutor.execute(action, targetPackage)
                    if (!result.success) {
                        Log.e(TAG, "ActionDispatcher: execution failed via ${result.path}: ${result.message}")
                    } else {
                        Log.d(TAG, "ActionDispatcher: executed via ${result.path}")
                    }
                    result.success
                }
        }

    private fun resolveTargetPackage(
        action: LLMAction,
        pm: PackageManager,
        appResolver: AppResolver,
    ): String? {
        if (action.actionType == ActionType.LAUNCH) {
            val value = action.value ?: return null
            return appResolver.resolve(value, pm)
        }
        // For non-launch actions, the target package comes from the current foreground app
        val service = NeuronAccessibilityService.instance ?: return null
        return service.rootInActiveWindow?.packageName?.toString()
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
