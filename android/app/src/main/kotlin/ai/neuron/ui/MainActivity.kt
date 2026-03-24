package ai.neuron.ui

import ai.neuron.BuildConfig
import ai.neuron.brain.ExecutionMode
import ai.neuron.ui.audit.AuditLogScreen
import ai.neuron.ui.onboarding.OnboardingScreen
import ai.neuron.ui.settings.NeuronSettings
import ai.neuron.ui.settings.SettingsScreen
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var isAccessibilityEnabled = mutableStateOf(false)
    private var isMicrophoneGranted = mutableStateOf(false)

    private val micPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            isMicrophoneGranted.value = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("neuron_prefs", Context.MODE_PRIVATE)
        seedBuildConfigKeys(prefs)
        val onboardingComplete = prefs.getBoolean("onboarding_complete", false)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by mutableStateOf(
                        if (onboardingComplete) "main" else "onboarding",
                    )
                    var settings by mutableStateOf(loadSettings(prefs))

                    Scaffold { innerPadding ->
                        val modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)

                        when (currentScreen) {
                            "onboarding" ->
                                OnboardingScreen(
                                    isAccessibilityEnabled = isAccessibilityEnabled.value,
                                    onComplete = {
                                        prefs.edit().putBoolean("onboarding_complete", true).apply()
                                        currentScreen = "main"
                                    },
                                )
                            "settings" ->
                                SettingsScreen(
                                    settings = settings,
                                    onSettingsChanged = { newSettings ->
                                        settings = newSettings
                                        saveSettings(prefs, newSettings)
                                    },
                                    onClearMemory = { /* TODO: wire to LongTermMemory.clearAll() */ },
                                    onViewAuditLog = { currentScreen = "audit" },
                                    onBack = { currentScreen = "main" },
                                )
                            "audit" -> AuditLogScreen(entries = emptyList())
                            else ->
                                MainScreen(
                                    isAccessibilityEnabled = isAccessibilityEnabled.value,
                                    isMicrophoneGranted = isMicrophoneGranted.value,
                                    onOpenSettings = { currentScreen = "settings" },
                                    onRunOnboarding = { currentScreen = "onboarding" },
                                    onRequestMicrophone = {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from system settings
        isAccessibilityEnabled.value = isAccessibilityServiceEnabled()
        isMicrophoneGranted.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
        return enabledServices.contains("ai.neuron")
    }

    private fun seedBuildConfigKeys(prefs: android.content.SharedPreferences) {
        val editor = prefs.edit()

        fun seedIfEmpty(
            prefKey: String,
            buildConfigValue: String,
        ) {
            val current = prefs.getString(prefKey, "") ?: ""
            if (current.isEmpty() && buildConfigValue.isNotEmpty()) {
                editor.putString(prefKey, buildConfigValue)
            }
        }
        seedIfEmpty("gemini_api_key", BuildConfig.GEMINI_API_KEY)
        seedIfEmpty("openrouter_api_key", BuildConfig.OPENROUTER_API_KEY)
        seedIfEmpty("ollama_api_key", BuildConfig.OLLAMA_API_KEY)
        seedIfEmpty("picovoice_access_key", BuildConfig.PICOVOICE_ACCESS_KEY)
        editor.apply()
    }

    private fun loadSettings(prefs: android.content.SharedPreferences): NeuronSettings {
        return NeuronSettings(
            geminiApiKey = prefs.getString("gemini_api_key", "") ?: "",
            ollamaEndpoint = prefs.getString("ollama_endpoint", "") ?: "",
            ollamaApiKey = prefs.getString("ollama_api_key", "") ?: "",
            openRouterApiKey = prefs.getString("openrouter_api_key", "") ?: "",
            picovoiceAccessKey = prefs.getString("picovoice_access_key", "") ?: "",
            wakeWordKeyword = prefs.getString("wake_word_keyword", "JARVIS") ?: "JARVIS",
            wakeWordEnabled = prefs.getBoolean("wake_word_enabled", true),
            cloudEnabled = prefs.getBoolean("cloud_enabled", true),
            executionMode =
                try {
                    ExecutionMode.valueOf(prefs.getString("execution_mode", "SUPERVISED") ?: "SUPERVISED")
                } catch (_: IllegalArgumentException) {
                    ExecutionMode.SUPERVISED
                },
        )
    }

    private fun saveSettings(
        prefs: android.content.SharedPreferences,
        settings: NeuronSettings,
    ) {
        prefs.edit()
            .putString("gemini_api_key", settings.geminiApiKey)
            .putString("ollama_endpoint", settings.ollamaEndpoint)
            .putString("ollama_api_key", settings.ollamaApiKey)
            .putString("openrouter_api_key", settings.openRouterApiKey)
            .putString("picovoice_access_key", settings.picovoiceAccessKey)
            .putString("wake_word_keyword", settings.wakeWordKeyword)
            .putBoolean("wake_word_enabled", settings.wakeWordEnabled)
            .putBoolean("cloud_enabled", settings.cloudEnabled)
            .putString("execution_mode", settings.executionMode.name)
            .apply()
    }
}
