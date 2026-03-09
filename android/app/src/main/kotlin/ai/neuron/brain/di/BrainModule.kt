package ai.neuron.brain.di

import ai.neuron.BuildConfig
import ai.neuron.accessibility.ActionExecutor
import ai.neuron.accessibility.NeuronAccessibilityService
import ai.neuron.accessibility.UITreeReader
import ai.neuron.accessibility.model.GlobalActionType
import ai.neuron.accessibility.model.NeuronAction
import ai.neuron.accessibility.model.ActionResult
import ai.neuron.accessibility.model.ScrollDirection
import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.PlanAndExecuteEngine
import ai.neuron.brain.client.GeminiFlashClient
import ai.neuron.brain.client.NvidiaQwenClient
import ai.neuron.brain.client.OllamaCloudClient
import ai.neuron.brain.client.OpenRouterClient
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
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
    ): PlanAndExecuteEngine.ActionDispatcher =
        object : PlanAndExecuteEngine.ActionDispatcher {
            override suspend fun dispatch(action: LLMAction): Boolean =
                withContext(Dispatchers.Default) {
                    val service = NeuronAccessibilityService.instance
                    if (service == null) {
                        Log.w(TAG, "ActionDispatcher: AccessibilityService not active, dropping action ${action.actionType}")
                        return@withContext false
                    }
                    val neuronAction = mapToNeuronAction(action, context.packageManager)
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

    private val KNOWN_APPS = mapOf(
        "settings" to "com.android.settings",
        "chrome" to "com.android.chrome",
        "whatsapp" to "com.whatsapp",
        "messages" to "com.google.android.apps.messaging",
        "phone" to "com.google.android.dialer",
        "dialer" to "com.google.android.dialer",
        "phone dialer" to "com.google.android.dialer",
        "camera" to "com.android.camera",
        "calculator" to "com.google.android.calculator",
        "clock" to "com.google.android.deskclock",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "youtube" to "com.google.android.youtube",
        "play store" to "com.android.vending",
        "files" to "com.google.android.apps.nbu.files",
        "photos" to "com.google.android.apps.photos",
        "contacts" to "com.google.android.contacts",
        "calendar" to "com.google.android.calendar",
    )

    private fun resolvePackageName(value: String, pm: PackageManager): String? {
        // Try known apps map first (case-insensitive)
        val lower = value.lowercase().trim()
        KNOWN_APPS[lower]?.let { return it }

        // If it looks like a package name (contains dot), verify it's launchable
        if ('.' in value) {
            if (pm.getLaunchIntentForPackage(value) != null) return value
            Log.w(TAG, "Package '$value' not launchable, trying label lookup...")
        }

        // Fuzzy match: search installed apps by label
        val searchTerm = value.replace("com.android.", "").replace("com.google.", "")
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.equals(lower, ignoreCase = true) || label.equals(searchTerm, ignoreCase = true)) {
                return app.packageName
            }
        }
        // Partial match as last resort
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(lower) || lower.contains(label)) {
                return app.packageName
            }
        }
        return null
    }

    private fun mapToNeuronAction(action: LLMAction, pm: PackageManager): NeuronAction? =
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
                val packageName = resolvePackageName(value, pm) ?: run {
                    Log.w(TAG, "ActionDispatcher: could not resolve app '$value' to package name")
                    return null
                }
                NeuronAction.LaunchApp(packageName = packageName)
            }
            ActionType.NAVIGATE -> {
                val globalActionType = when (action.value?.lowercase()) {
                    "home" -> GlobalActionType.HOME
                    "back" -> GlobalActionType.BACK
                    "recents" -> GlobalActionType.RECENTS
                    "notifications" -> GlobalActionType.NOTIFICATIONS
                    "quick_settings", "quicksettings" -> GlobalActionType.QUICK_SETTINGS
                    else -> {
                        Log.w(TAG, "ActionDispatcher: unknown NAVIGATE target '${action.value}', defaulting to HOME")
                        GlobalActionType.HOME
                    }
                }
                NeuronAction.GlobalAction(action = globalActionType)
            }
            // Terminal/meta action types — not dispatched to the accessibility layer
            ActionType.DONE,
            ActionType.ERROR,
            ActionType.CONFIRM,
            ActionType.WAIT,
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
