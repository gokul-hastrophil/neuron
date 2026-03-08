package ai.neuron.brain.di

import ai.neuron.BuildConfig
import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.PlanAndExecuteEngine
import ai.neuron.brain.client.ClaudeClient
import ai.neuron.brain.client.GeminiFlashClient
import ai.neuron.brain.model.LLMAction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BrainModule {

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
    fun provideClaudeClient(okHttpClient: OkHttpClient): ClaudeClient =
        ClaudeClient(okHttpClient)

    @Provides
    @Singleton
    fun provideUIProvider(): PlanAndExecuteEngine.UIProvider =
        object : PlanAndExecuteEngine.UIProvider {
            override suspend fun getCurrentUITree(): UITree {
                // Will be wired to real AccessibilityService UITreeReader at runtime
                return UITree.empty()
            }
        }

    @Provides
    @Singleton
    fun provideActionDispatcher(): PlanAndExecuteEngine.ActionDispatcher =
        object : PlanAndExecuteEngine.ActionDispatcher {
            override suspend fun dispatch(action: LLMAction): Boolean {
                // Will be wired to real ActionExecutor at runtime
                return false
            }
        }

    private fun sanitizeApiKeys(message: String): String {
        var sanitized = message
        val geminiKey = BuildConfig.GEMINI_API_KEY
        if (geminiKey.isNotEmpty()) {
            sanitized = sanitized.replace(geminiKey, "***GEMINI_KEY***")
        }
        val anthropicKey = BuildConfig.ANTHROPIC_API_KEY
        if (anthropicKey.isNotEmpty()) {
            sanitized = sanitized.replace(anthropicKey, "***ANTHROPIC_KEY***")
        }
        return sanitized
    }
}
