# Neuron Android Module — Claude Code Context

> Read the root CLAUDE.md at `/home/disk/Personal/projects/neuron/neuron/CLAUDE.md` before working in this module.
> That file is the single source of truth for architecture, LLM routing rules, hard rules, and tech stack versions.

---

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Format Kotlin code
./gradlew ktlintFormat

# Build and install on connected device
./gradlew installDebug
```

---

## Module Structure

```
app/src/main/kotlin/ai/neuron/
├── accessibility/              ← L2 PERCEPTION + L5 NERVE (AccessibilityService core)
│   ├── NeuronAccessibilityService.kt   ← Service entry point, event routing
│   ├── UITreeReader.kt                 ← Traverses + prunes + serializes UI tree to JSON
│   ├── ActionExecutor.kt               ← tap, type, swipe, scroll, global nav actions
│   ├── ScreenCapture.kt                ← takeScreenshot() → compressed JPEG
│   └── OverlayManager.kt               ← Floating input + status bubble
│
├── brain/                      ← L3 BRAIN (LLM routing + planning)
│   ├── NeuronBrainService.kt           ← Bound service, orchestrates planning loop
│   ├── LLMRouter.kt                    ← T0/T1/T2/T3/T4 tier routing logic
│   ├── PlanAndExecuteEngine.kt         ← Step-by-step task execution with replanning
│   ├── IntentClassifier.kt             ← Classify command complexity → select tier
│   └── SensitivityGate.kt              ← Detects password/banking screens → force T4
│
├── memory/                     ← L4 MEMORY (Room DB + vectors)
│   ├── WorkingMemory.kt                ← In-memory task state, clears on completion
│   ├── LongTermMemory.kt               ← Room DB: preferences, workflows
│   ├── EpisodicMemory.kt               ← Task traces with full step history
│   └── RAGEngine.kt                    ← sqlite-vec retrieval for similar past tasks
│
├── input/                      ← L1 INPUT (voice + text)
│   ├── WakeWordService.kt              ← Porcupine wake word detection
│   ├── SpeechRecognitionService.kt     ← whisper.cpp streaming transcription
│   └── TextInputHandler.kt             ← Overlay text input processing
│
├── sdk/                        ← L6 public SDK surface
│   ├── NeuronSDK.kt                    ← Public API: connect(), disconnect()
│   ├── ToolRegistry.kt                 ← Register/unregister third-party tools
│   └── AppFunctionsBridge.kt           ← AppFunctions API integration
│
└── ui/                         ← L6 OUTPUT (settings + onboarding)
    ├── MainActivity.kt                 ← Root activity, Compose nav host
    ├── OnboardingActivity.kt           ← Accessibility permission grant flow
    └── SettingsFragment.kt             ← Runtime settings
```

---

## Android Coding Rules

### Hilt DI

All services, ViewModels, and repositories must use constructor injection. Never instantiate dependencies manually inside a class.

```kotlin
// Correct: inject via constructor or field injection for Android system types
@AndroidEntryPoint
class NeuronAccessibilityService : AccessibilityService() {
    @Inject lateinit var uiTreeReader: UITreeReader
    @Inject lateinit var actionExecutor: ActionExecutor
    @Inject lateinit var sensitivityGate: SensitivityGate
}

// Correct: provide via @Module
@Module
@InstallIn(ServiceComponent::class)
object AccessibilityModule {
    @Provides
    fun provideUITreeReader(): UITreeReader = UITreeReader()
}

// Never:
val reader = UITreeReader() // manual instantiation bypasses the DI graph
```

### Coroutine Scopes

Use the correct scope for the context. Never use `GlobalScope` — it leaks and cannot be cancelled.

```kotlin
// ViewModels → viewModelScope (auto-cancelled on ViewModel clear)
viewModelScope.launch { /* UI-driven async work */ }

// Services → define a serviceScope tied to the service lifecycle
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()
}

// Never:
GlobalScope.launch { /* leaks, uncancellable */ }
```

### AccessibilityNodeInfo Lifecycle

Every `AccessibilityNodeInfo` obtained from the system must be recycled after use. Not recycling leaks native memory and will cause OOM on long-running sessions.

```kotlin
val root = rootInActiveWindow ?: return
try {
    // work with root and any child nodes
    val children = (0 until root.childCount).map { root.getChild(it) }
    children.forEach { child ->
        // use child
        child?.recycle()
    }
} finally {
    root.recycle()
}
```

### UI: Compose Only

No XML layouts. No `View`-based UI. All UI is Jetpack Compose. Do not add View-based dependencies.

```kotlin
// Correct
@Composable
fun OverlayContent(state: OverlayState, onCommand: (String) -> Unit) {
    // Compose UI
}

// Never
setContentView(R.layout.activity_main)
val button = findViewById<Button>(R.id.btn_submit)
```

### JSON Serialization

Use KotlinX Serialization exclusively. Do not add Gson or Moshi to the project.

```kotlin
@Serializable
data class UINode(
    val id: String,
    val text: String?,
    val contentDescription: String?,
    val bounds: SerializableBounds,
    val children: List<UINode> = emptyList(),
)

val json = Json.encodeToString(node)
val node = Json.decodeFromString<UINode>(json)
```

### Sealed Result Types

All async operations that can fail must return `NeuronResult<T>`. Never throw exceptions across module boundaries. Handle them at the source and wrap in `Error`.

```kotlin
sealed class NeuronResult<out T> {
    data class Success<T>(val data: T) : NeuronResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : NeuronResult<Nothing>()
}

suspend fun executeAction(action: Action): NeuronResult<ActionOutcome> {
    return try {
        NeuronResult.Success(performAction(action))
    } catch (e: Exception) {
        NeuronResult.Error("Action '${action.type}' failed: ${e.message}", e)
    }
}

// Callers handle explicitly — no unchecked exceptions
when (val result = actionExecutor.executeAction(tapAction)) {
    is NeuronResult.Success -> verifyActionSucceeded(result.data)
    is NeuronResult.Error -> log.error("Action failed", result.message)
}
```

---

## ADB Debug Commands

```bash
# Filter logcat to Neuron tags only
adb logcat -s NeuronAccessibility NeuronBrain NeuronAction NeuronMemory NeuronInput

# Dump current UI tree to stdout (pipe to file if needed)
adb shell uiautomator dump /dev/tty

# Dump UI tree to file and pull it
adb shell uiautomator dump /sdcard/window_dump.xml && adb pull /sdcard/window_dump.xml

# Force stop the app (resets all service state)
adb shell am force-stop ai.neuron

# Capture the current screen as PNG
adb exec-out screencap -p > screen.png

# Grant accessibility service permission via ADB (dev only, requires ADB debug)
adb shell settings put secure enabled_accessibility_services ai.neuron/.accessibility.NeuronAccessibilityService

# Verify accessibility service is active
adb shell dumpsys accessibility | grep ai.neuron

# Grant SYSTEM_ALERT_WINDOW (overlay) permission
adb shell appops set ai.neuron SYSTEM_ALERT_WINDOW allow

# Send a simulated text command for testing without voice
adb shell am broadcast -a ai.neuron.ACTION_TEXT_COMMAND --es command "open WhatsApp and message John"

# Watch live overlay log output
adb logcat -s NeuronOverlay:V
```

---

## Test Commands

```bash
# Run all unit tests (JVM only, no device required)
./gradlew test

# Run instrumentation tests (requires connected device or running emulator)
./gradlew connectedAndroidTest

# Run a specific test class
./gradlew test --tests "ai.neuron.accessibility.UITreeReaderTest"

# Run tests with coverage report (output in build/reports/coverage/)
./gradlew testDebugUnitTestCoverage
```

Test locations:
- Unit tests: `app/src/test/kotlin/ai/neuron/`
- Instrumentation tests: `app/src/androidTest/kotlin/ai/neuron/`
- Test naming convention: `should_[expectedBehavior]_when_[condition]`

---

## Hard Rules (reminders for this module)

- Never send content from password fields or sensitivity-gated screens to any cloud API. Always check `SensitivityGate` before routing to T2/T3.
- Always verify action success by re-reading the UI tree after `ActionExecutor` executes an action. Never assume success.
- Always log every action to the audit log with a timestamp and the target app's package name.
- Never start `NeuronAccessibilityService` programmatically — the user must enable it manually in Android system settings.
- Minimum SDK is API 26. Use `Build.VERSION.SDK_INT` checks before calling APIs above the minimum.
- All DB and network operations run on `Dispatchers.IO`. Never touch them from the main thread.

---

*Module: android | Project: Neuron 0.1.0-alpha | Last updated: March 2026*
