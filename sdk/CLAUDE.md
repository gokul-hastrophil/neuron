# Neuron SDK — Claude Code Context

> Read the root CLAUDE.md at `/home/disk/Personal/projects/neuron/neuron/CLAUDE.md` before working in this module.
> That file is the single source of truth for architecture, LLM routing rules, hard rules, and tech stack versions.

---

## Overview

The Neuron SDK provides two packages that allow third-party developers to extend Neuron's capabilities by registering custom tools. A registered tool becomes callable by the Neuron AI planner like any built-in capability.

- **Python SDK** (`neuron-sdk`) — register tools and control the phone from Python scripts, automation workflows, or desktop agents.
- **Kotlin SDK** (`neuron-sdk-android`) — register tools from within an Android app, integrated with the AppFunctions API so Neuron can discover and invoke app-provided capabilities.

The SDK surface is intentionally narrow. It exposes tool registration and connectivity. Internal Neuron types (UINode, ExecutionPlan, LLMRouter, etc.) are never exposed.

---

## Python SDK

### Build and Test

```bash
# Install in editable mode with dev dependencies
pip install -e sdk/python/

# Run tests
pytest sdk/python/tests/

# Run tests with coverage
pytest sdk/python/tests/ --cov=neuron_sdk --cov-report=term-missing

# Lint and format
ruff check sdk/python/
ruff format sdk/python/
mypy sdk/python/
```

### Target API

```python
import asyncio
from neuron_sdk import NeuronClient

async def main():
    client = NeuronClient("ws://localhost:7384")
    await client.connect()

    # Register a custom tool
    await client.register_tool(
        name="check_calendar",
        description="Check the user's calendar for events on a given date.",
        params={"date": "ISO 8601 date string (YYYY-MM-DD)"},
        handler=check_calendar_handler,
    )

    # List all registered tools
    tools = await client.list_tools()
    print(tools)

    await client.disconnect()

asyncio.run(main())
```

Core methods:
- `NeuronClient(url: str)` — create a client pointing at the MCP server
- `await client.connect()` — establish the WebSocket connection
- `await client.disconnect()` — clean shutdown
- `await client.register_tool(name, description, params, handler)` — register a callable tool
- `await client.list_tools()` — return all tools currently registered with the server

---

## Kotlin SDK

### Build and Test

```bash
# Build the AAR artifact (output: sdk/kotlin/build/outputs/aar/)
./gradlew :sdk:assembleRelease

# Run unit tests
./gradlew :sdk:test

# Run with verbose output
./gradlew :sdk:test --info
```

### Target API

```kotlin
import ai.neuron.sdk.NeuronSDK
import ai.neuron.sdk.ToolRegistry
import ai.neuron.sdk.ToolDefinition
import ai.neuron.sdk.Param

// Obtain the SDK instance (singleton, Application context)
val neuron = NeuronSDK.getInstance(context)

// Connect to the local Neuron service
neuron.connect()

// Register a tool
neuron.registerTool(
    ToolDefinition(
        name = "send_email",
        description = "Send an email via the default email app.",
        params = listOf(
            Param(name = "to", description = "Recipient email address"),
            Param(name = "subject", description = "Email subject line"),
            Param(name = "body", description = "Email body text"),
        ),
        execute = { params ->
            sendEmailViaIntent(params["to"]!!, params["subject"]!!, params["body"]!!)
        }
    )
)

// Query registered tools
val tools: List<ToolDefinition> = ToolRegistry.getInstance().getRegisteredTools()

// Disconnect when the component is destroyed
neuron.disconnect()
```

Core types:
- `NeuronSDK` — singleton entry point; `getInstance(context)`, `connect()`, `disconnect()`, `registerTool()`
- `ToolRegistry` — maintains the registered tool list; `getRegisteredTools()`, `unregisterTool(name)`
- `ToolDefinition` — data class describing a tool: name, description, params, execute lambda
- `Param` — describes a single tool parameter: name, description, required (default true)

---

## SDK-Specific Coding Rules

### Python SDK
- Support Python 3.10 and above. Do not use syntax or stdlib features introduced after 3.10 without a version guard.
- Zero required external dependencies for the core SDK. Only `websockets` (or `httpx` for HTTP transport) is permitted. All other dependencies go in `[dev]` extras.
- All public functions must have complete type annotations and pass `mypy --strict`.
- All public APIs must have docstrings.

### Kotlin SDK
- Minimum API level is 26 (Android 8.0), matching the main app. No API-level-specific code above 26 without a `Build.VERSION.SDK_INT` check.
- Distribute as an AAR artifact. The SDK must not include classes that duplicate what the host app already provides (Hilt, Compose, Room). Mark those as `compileOnly` or `provided`.
- All public APIs must have KDoc comments.
- The execute lambda in `ToolDefinition` is called on a background coroutine. SDK guarantees this — tool authors do not need to dispatch to a background thread themselves.

### Both SDKs
- Semantic versioning (`MAJOR.MINOR.PATCH`). A CHANGELOG entry is required for every release.
- Never expose internal Neuron types in the public API surface. The SDK defines its own public models (`ToolDefinition`, `Param`, `ToolResult`).
- The SDK does not perform LLM calls. It is a connectivity and registration layer only. Any AI logic belongs in the server or the Android app.
- Public API surface changes require a version bump. Removing or renaming a public method is a breaking change (MAJOR bump).

---

*Module: sdk | Project: Neuron 0.1.0-alpha | Last updated: March 2026*
