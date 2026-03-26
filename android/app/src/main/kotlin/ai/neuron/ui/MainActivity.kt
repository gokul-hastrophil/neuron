package ai.neuron.ui

import ai.neuron.brain.ExecutionMode
import ai.neuron.brain.client.LlmProxyClient
import ai.neuron.brain.client.SecureKeyStore
import ai.neuron.memory.AuditRepository
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var auditRepository: AuditRepository

    @Inject
    lateinit var secureKeyStore: SecureKeyStore

    @Inject
    lateinit var llmProxyClient: LlmProxyClient

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
        migrateKeysToSecureStore(prefs)
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
                            "audit" -> {
                                val auditEntries by auditRepository.observeAll()
                                    .collectAsState(initial = emptyList())
                                AuditLogScreen(entries = auditEntries)
                            }
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

    /**
     * One-time migration: moves any existing plaintext keys from SharedPreferences
     * into EncryptedSharedPreferences, then deletes the plaintext copies.
     */
    private fun migrateKeysToSecureStore(prefs: android.content.SharedPreferences) {
        val editor = prefs.edit()

        fun migrateKey(
            prefKey: String,
            setter: (String) -> Unit,
            getter: () -> String,
        ) {
            val plainValue = prefs.getString(prefKey, "") ?: ""
            if (plainValue.isNotEmpty() && getter().isEmpty()) {
                setter(plainValue)
                editor.remove(prefKey)
            }
        }

        migrateKey("picovoice_access_key", { secureKeyStore.picovoiceAccessKey = it }, { secureKeyStore.picovoiceAccessKey })

        // Remove stale cloud API key entries that are no longer used
        editor.remove("gemini_api_key")
        editor.remove("openrouter_api_key")
        editor.remove("ollama_api_key")

        editor.apply()
    }

    private fun loadSettings(prefs: android.content.SharedPreferences): NeuronSettings {
        return NeuronSettings(
            serverUrl = secureKeyStore.serverUrl,
            deviceToken = secureKeyStore.deviceToken,
            picovoiceAccessKey = secureKeyStore.picovoiceAccessKey,
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
        // Secure keys go to EncryptedSharedPreferences
        secureKeyStore.serverUrl = settings.serverUrl
        secureKeyStore.deviceToken = settings.deviceToken
        secureKeyStore.picovoiceAccessKey = settings.picovoiceAccessKey

        // Update proxy client with new server URL
        llmProxyClient.updateServerUrl(settings.serverUrl)

        // Non-secret settings stay in regular prefs
        prefs.edit()
            .putString("wake_word_keyword", settings.wakeWordKeyword)
            .putBoolean("wake_word_enabled", settings.wakeWordEnabled)
            .putBoolean("cloud_enabled", settings.cloudEnabled)
            .putString("execution_mode", settings.executionMode.name)
            .apply()
    }
}
