package ai.neuron.brain.di

import ai.neuron.BuildConfig
import ai.neuron.accessibility.NeuronAccessibilityService
import ai.neuron.accessibility.UITreeReader
import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.AppResolver
import ai.neuron.brain.PlanAndExecuteEngine
import ai.neuron.brain.StructuredToolCallParser
import ai.neuron.brain.client.LlmProxyClient
import ai.neuron.brain.client.SecureKeyStore
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
    fun provideOkHttpClient(secureKeyStore: SecureKeyStore): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor =
                HttpLoggingInterceptor { message ->
                    val sanitized = sanitizeSecrets(message, secureKeyStore)
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
    fun provideSecureKeyStore(
        @ApplicationContext context: Context,
    ): SecureKeyStore = SecureKeyStore(context)

    @Provides
    @Singleton
    fun provideLlmProxyClient(
        okHttpClient: OkHttpClient,
        toolCallParser: StructuredToolCallParser,
        secureKeyStore: SecureKeyStore,
    ): LlmProxyClient =
        LlmProxyClient(
            okHttpClient = okHttpClient,
            toolCallParser = toolCallParser,
            serverUrl = secureKeyStore.serverUrl,
            deviceTokenProvider = { secureKeyStore.deviceToken },
        )

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

    /**
     * Sanitize device token and Picovoice key from log messages.
     * Cloud API keys no longer exist in the app — only runtime secrets remain.
     */
    private fun sanitizeSecrets(
        message: String,
        secureKeyStore: SecureKeyStore,
    ): String {
        var sanitized = message
        val token = secureKeyStore.deviceToken
        if (token.isNotEmpty()) {
            sanitized = sanitized.replace(token, "***DEVICE_TOKEN***")
        }
        val picoKey = secureKeyStore.picovoiceAccessKey
        if (picoKey.isNotEmpty()) {
            sanitized = sanitized.replace(picoKey, "***PICOVOICE_KEY***")
        }
        return sanitized
    }
}
