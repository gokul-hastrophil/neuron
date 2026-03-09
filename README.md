# Neuron — Android AI Agent

> *Control your entire Android phone with natural language.*

[![Android CI](https://github.com/gokul-hastrophil/neuron/actions/workflows/android-ci.yml/badge.svg)](https://github.com/gokul-hastrophil/neuron/actions)
[![Latest Release](https://img.shields.io/github/v/release/gokul-hastrophil/neuron?include_prereleases)](https://github.com/gokul-hastrophil/neuron/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

---

## What is Neuron?

Neuron is an AI-powered Android agent that autonomously controls your phone. Instead of manually navigating apps, tell Neuron what you want:

```
"Send Mom a WhatsApp message saying I'll be 20 minutes late"
"Order my usual lunch from Zomato"  
"Book me a cab to the airport for 6pm"
"Turn on Do Not Disturb until my meeting ends at 3pm"
```

Neuron understands your intent, plans the steps, and executes them across any installed app — no shortcuts to set up, no scripts to write.

---

## How It Works

```
Your Voice/Text
      ↓
  Neuron AI Brain (on-device + cloud)
      ↓
  Reads screen → Plans steps → Executes actions
      ↓
  Task Complete ✅
```

1. **Perception:** AccessibilityService reads any app's UI in real-time
2. **Planning:** LLM decomposes your goal into executable steps  
3. **Execution:** AI navigates, taps, types, and swipes — just like you would
4. **Memory:** Neuron learns your preferences and gets faster over time

---

## Installation (Sideload)

> **Requirements:** Android 8.0+ (API 26+) · ~40MB

1. [Download the latest APK](https://github.com/gokul-hastrophil/neuron/releases/latest)
2. On your phone: **Settings → Security → Install Unknown Apps**
3. Enable for your file manager or browser
4. Install the APK
5. Open Neuron → tap "Enable" → go to **Settings → Accessibility → Neuron** and turn it on
6. Return to Neuron and try your first command

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  LAYER 1: INPUT                                     │
│  Porcupine wake word · whisper.cpp STT · Overlay    │
├─────────────────────────────────────────────────────┤
│  LAYER 2: PERCEPTION                                │
│  AccessibilityService UI tree · Screenshots         │
├─────────────────────────────────────────────────────┤
│  LAYER 3: BRAIN                                     │
│  Gemma 3n (on-device) → Gemini Flash → Claude       │
├─────────────────────────────────────────────────────┤
│  LAYER 4: MEMORY                                    │
│  Room DB · sqlite-vec · EmbeddingGemma              │
├─────────────────────────────────────────────────────┤
│  LAYER 5: NERVE                                     │
│  AccessibilityService actions · AppFunctions        │
├─────────────────────────────────────────────────────┤
│  PROTOCOL BUS: MCP Server · WebSocket API · SDK     │
└─────────────────────────────────────────────────────┘
```

---

## Developer Integration

Neuron exposes itself as an MCP server — connect any AI tool to control Android:

```json
// Claude Desktop config (claude_desktop_config.json)
{
  "mcpServers": {
    "neuron": {
      "command": "python",
      "args": ["/path/to/neuron/server/mcp/neuron_mcp_server.py"],
      "env": {
        "NEURON_SECRET_TOKEN": "your-token"
      }
    }
  }
}
```

Then in Claude: *"Take a screenshot of my phone"* or *"Open WhatsApp and send John a message"*

### Neuron SDK (Kotlin)

Register custom tools that Neuron's AI brain can discover and invoke:

```kotlin
neuronSDK.init()
neuronSDK.registerTool(
    NeuronTool(
        name = "book_cab",
        description = "Book a cab using Uber",
        parameters = mapOf("destination" to "string", "time" to "string"),
        execute = { params ->
            val dest = params["destination"] ?: "home"
            bookUberTo(dest)  // your implementation
            "Cab booked to $dest"
        }
    )
)
```

See [SDK Quickstart](docs/onboarding/sdk_quickstart.md) for full guide.

---

## Privacy

- **Passwords, PINs, banking screens:** processed 100% on-device, never sent to cloud
- **All other tasks:** you control cloud vs. on-device in Settings
- **Audit log:** every action logged locally, never synced without consent
- **Open source:** read the code, verify the behavior

---

## Development Setup

```bash
git clone https://github.com/gokul-hastrophil/neuron
cd neuron
chmod +x scripts/setup_linux.sh && ./scripts/setup_linux.sh
source ~/.bashrc
# Fill in your API keys
cp .env.example .env && nano .env
# Start development
neuron-emulator   # Launch Android emulator
neuron-build      # Build APK
neuron-claude     # Start Claude Code with full project context
```

See [docs/onboarding/](docs/onboarding/) for detailed setup guide.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). All contributions welcome.

**Especially needed:**
- OEM compatibility testing (Xiaomi, Oppo, OnePlus)
- New app integrations
- On-device model performance testing
- Accessibility UX improvements

---

## License

Apache License 2.0 — see [LICENSE](LICENSE)

---

*Built with ❤️ by the Neuron team | Powered by Claude, Gemini, and open-source Android AI*
