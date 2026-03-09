package ai.neuron.ui

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ai.neuron.ui.onboarding.OnboardingScreen
import ai.neuron.ui.settings.NeuronSettings
import ai.neuron.ui.settings.SettingsScreen
import ai.neuron.ui.audit.AuditLogScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("neuron_prefs", Context.MODE_PRIVATE)
        val onboardingComplete = prefs.getBoolean("onboarding_complete", false)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember {
                        mutableStateOf(if (onboardingComplete) "main" else "onboarding")
                    }
                    var settings by remember { mutableStateOf(loadSettings(prefs)) }

                    Scaffold { innerPadding ->
                        val modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)

                        when (currentScreen) {
                            "onboarding" -> OnboardingScreen(
                                isAccessibilityEnabled = isAccessibilityServiceEnabled(),
                                onComplete = {
                                    prefs.edit().putBoolean("onboarding_complete", true).apply()
                                    currentScreen = "main"
                                },
                            )
                            "settings" -> SettingsScreen(
                                settings = settings,
                                onSettingsChanged = { newSettings ->
                                    settings = newSettings
                                    saveSettings(prefs, newSettings)
                                },
                                onClearMemory = { /* TODO: wire to LongTermMemory.clearAll() */ },
                                onViewAuditLog = { currentScreen = "audit" },
                            )
                            "audit" -> AuditLogScreen(entries = emptyList())
                            else -> MainScreen(
                                onOpenSettings = { currentScreen = "settings" },
                                onRunOnboarding = { currentScreen = "onboarding" },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabledServices.contains("ai.neuron")
    }

    private fun loadSettings(prefs: android.content.SharedPreferences): NeuronSettings {
        return NeuronSettings(
            geminiApiKey = prefs.getString("gemini_api_key", "") ?: "",
            ollamaEndpoint = prefs.getString("ollama_endpoint", "") ?: "",
            openRouterApiKey = prefs.getString("openrouter_api_key", "") ?: "",
            cloudEnabled = prefs.getBoolean("cloud_enabled", true),
        )
    }

    private fun saveSettings(prefs: android.content.SharedPreferences, settings: NeuronSettings) {
        prefs.edit()
            .putString("gemini_api_key", settings.geminiApiKey)
            .putString("ollama_endpoint", settings.ollamaEndpoint)
            .putString("openrouter_api_key", settings.openRouterApiKey)
            .putBoolean("cloud_enabled", settings.cloudEnabled)
            .apply()
    }
}
