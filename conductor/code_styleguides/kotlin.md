# Kotlin Style Guide - Neuron

## Formatting

- **Formatter**: Ktlint (with default rules)
- **Static Analysis**: Detekt
- **Max line length**: 120 characters
- **Indentation**: 4 spaces (no tabs)
- **Trailing commas**: Required in multiline parameter lists

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Classes | PascalCase | `UITreeReader`, `ActionExecutor` |
| Functions | camelCase | `getUITree()`, `executeAction()` |
| Properties | camelCase | `currentTask`, `isRunning` |
| Constants | SCREAMING_SNAKE | `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT_MS` |
| Packages | lowercase | `ai.neuron.accessibility` |
| Test methods | should_X_when_Y | `should_returnPrunedTree_when_invisibleNodesExist` |

## Language Features

### Sealed Classes for Results

Always use sealed classes for operation results:

```kotlin
sealed class NeuronResult<out T> {
    data class Success<T>(val data: T) : NeuronResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : NeuronResult<Nothing>()
}
```

### Coroutines

- Use `viewModelScope` or `lifecycleScope` in UI components
- Use `SupervisorJob() + Dispatchers.IO` for services
- Never use `GlobalScope`
- Always cancel scopes in `onDestroy()`

```kotlin
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
}
```

### Null Safety

- Prefer `?.let {}` over `if (x != null)`
- Use `?: return` for early exits
- Never use `!!` — find a safe alternative

### Extension Functions

Use for readability, but keep them discoverable (in the same file or a clearly named `Extensions.kt`):

```kotlin
fun AccessibilityNodeInfo.toUINode(): UINode = UINode(
    id = viewIdResourceName.orEmpty(),
    text = text?.toString().orEmpty(),
    clickable = isClickable,
    visible = isVisibleToUser,
)
```

## Dependency Injection

- **Hilt** for all DI — never manual construction in Activities/Services
- Use `@Inject constructor` for class dependencies
- Use `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`
- Bind interfaces in `@Module` classes

## Android-Specific Rules

1. **Never block the main thread** — all I/O, LLM calls, network in coroutines
2. **Always recycle AccessibilityNodeInfo** — call `recycle()` when done
3. **Use structured logging** — `Log.d("NeuronAS", "message")`, never `println()`
4. **Handle service reconnection** — AccessibilityService can be killed anytime
5. **Compose-only UI** — no XML layouts, no Fragments

## File Organization

```
ai/neuron/
├── accessibility/     # One file per class
│   ├── NeuronAccessibilityService.kt
│   ├── UITreeReader.kt
│   └── ActionExecutor.kt
├── brain/
├── memory/
├── input/
├── sdk/
├── ui/
└── di/               # Hilt modules
    └── AppModule.kt
```

## Anti-Patterns (Never Do)

- `runBlocking` on the main thread
- Hardcoded package names or UI element IDs
- Raw string concatenation for JSON (use Kotlinx Serialization)
- `Thread.sleep()` — use `delay()` in coroutines
- Catching `Exception` broadly — catch specific types
