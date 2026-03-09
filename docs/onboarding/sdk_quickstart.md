# Neuron SDK Quick Start

Register custom tools that Neuron's AI brain can discover and invoke.

## Register a Tool (Kotlin)

```kotlin
class MyApp : Application() {

    @Inject lateinit var neuronSDK: NeuronSDK

    override fun onCreate() {
        super.onCreate()
        neuronSDK.init()

        neuronSDK.registerTool(
            NeuronTool(
                name = "check_balance",
                description = "Check the user's account balance",
                parameters = mapOf("account" to "string"),
                execute = { params ->
                    val account = params["account"] ?: "default"
                    getBalance(account).toString()
                }
            )
        )
    }
}
```

## How It Works

1. Your app registers tools via `NeuronSDK.registerTool()`
2. Neuron's LLM planner sees registered tools in its system prompt
3. When the AI decides to use your tool, it invokes the `execute` callback
4. Your callback returns a string result to the AI

## API Reference

| Method | Description |
|--------|-------------|
| `NeuronSDK.init()` | Initialize the SDK (call once) |
| `NeuronSDK.registerTool(tool)` | Register a tool |
| `NeuronSDK.unregisterTool(name)` | Remove a tool |
| `NeuronSDK.listTools()` | List all registered tools |

## NeuronTool

```kotlin
data class NeuronTool(
    val name: String,                                    // Unique tool name
    val description: String,                             // What the tool does (shown to AI)
    val parameters: Map<String, String>,                 // param name → type
    val execute: suspend (Map<String, String>) -> String // Callback
)
```
