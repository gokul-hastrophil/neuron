# NEURON — Claude Code Master Configuration

> **You are Claude Code working on the Neuron Project.**
> Read this entire file before touching any code. It is the single source of truth.

---

## 🧠 Project Identity

**Neuron** is an AI-powered agentic operating layer for Android smartphones.
It lets users control their entire phone through natural language and voice commands.
The AI plans, navigates, and executes tasks across any installed app — autonomously.

**Codename:** NEURON  
**Target:** Android only (API 26+, optimize for API 30+)  
**Primary Language:** Kotlin (Android), Python (server/MCP/AI)  
**Secondary:** Java (legacy Android APIs), TypeScript (SDK tooling)  
**Distribution:** APK sideload (Day 1), Play Store (accessibility path, later)

---

## 🏗️ Architecture Summary

### The Six Layers (NEVER violate this separation)

```
L1 INPUT     → Voice (Porcupine wake + whisper.cpp) + Text (overlay) + Triggers
L2 PERCEPTION → AccessibilityService UI tree + Screenshots + Gemma 3n screen classifier
L3 BRAIN     → Tiered LLM Router: Gemma 3n (T0/T1) → Gemini Flash (T2) → Claude/Gemini 3 (T3)
L4 MEMORY    → Room DB (structured) + sqlite-vec (vectors) + EmbeddingGemma (embeddings)
L5 NERVE     → AccessibilityService actions + Intents + AppFunctions + Shizuku
L6 OUTPUT    → TTS + Overlay UI + Notifications + Audit Log
PROTOCOL BUS → MCP Server (ws://localhost:7384) + AppFunctions + WebSocket API
```

### The Three Principles
1. **Privacy-first**: Sensitive data (passwords, banking, health) NEVER leaves device (T4 tier)
2. **Hybrid intelligence**: On-device for speed/privacy, cloud for complex reasoning
3. **Developer-friendly**: Every capability is accessible via MCP, SDK, or REST

---

## 📁 Repository Structure

```
neuron/
├── CLAUDE.md                   ← YOU ARE HERE
├── VISION.md                   ← Product vision + goals
├── PRD.md                      ← Product requirements document
├── SPRINT.md                   ← Active sprint tasks
├── README.md                   ← Public-facing project readme
│
├── android/                    ← Main Android application (Kotlin)
│   └── app/src/main/
│       ├── kotlin/ai/neuron/
│       │   ├── accessibility/  ← AccessibilityService core (THE NERVE)
│       │   │   ├── NeuronAccessibilityService.kt
│       │   │   ├── UITreeReader.kt
│       │   │   ├── ActionExecutor.kt
│       │   │   ├── ScreenCapture.kt
│       │   │   └── OverlayManager.kt
│       │   ├── brain/          ← LLM routing + planning engine (THE BRAIN)
│       │   │   ├── NeuronBrainService.kt
│       │   │   ├── LLMRouter.kt
│       │   │   ├── PlanAndExecuteEngine.kt
│       │   │   ├── IntentClassifier.kt
│       │   │   └── SensitivityGate.kt
│       │   ├── memory/         ← Memory system (working/semantic/episodic)
│       │   │   ├── WorkingMemory.kt
│       │   │   ├── LongTermMemory.kt
│       │   │   ├── EpisodicMemory.kt
│       │   │   └── RAGEngine.kt
│       │   ├── input/          ← Voice + text input
│       │   │   ├── WakeWordService.kt
│       │   │   ├── SpeechRecognitionService.kt
│       │   │   └── TextInputHandler.kt
│       │   ├── sdk/            ← Neuron Developer SDK (public API)
│       │   │   ├── NeuronSDK.kt
│       │   │   ├── ToolRegistry.kt
│       │   │   └── AppFunctionsBridge.kt
│       │   └── ui/             ← Settings + onboarding UI
│       │       ├── MainActivity.kt
│       │       ├── OnboardingActivity.kt
│       │       └── SettingsFragment.kt
│       └── res/
│
├── server/                     ← Python backend
│   ├── mcp/
│   │   ├── neuron_mcp_server.py    ← MCP server exposing Android control
│   │   └── mcp_tools.py            ← Tool definitions
│   ├── brain/
│   │   ├── brain_api.py            ← REST API for LLM routing
│   │   ├── planner.py              ← Plan-and-Execute implementation
│   │   └── models.py               ← LLM client wrappers
│   └── memory/
│       ├── memory_service.py       ← Memory API
│       └── vector_store.py         ← Vector DB operations
│
├── sdk/                        ← Developer SDK packages
│   ├── python/                 ← Python SDK for tool registration
│   │   └── neuron_sdk/
│   └── kotlin/                 ← Kotlin SDK artifact
│
├── agents/                     ← Claude Code sub-agent configs
│   ├── architect.md            ← Architecture decisions agent
│   ├── android_dev.md          ← Android development agent
│   ├── ai_engineer.md          ← AI/ML integration agent
│   └── qa.md                   ← Testing + QA agent
│
├── skills/                     ← Reusable skill definitions
│   ├── android_accessibility.md
│   ├── llm_routing.md
│   ├── memory_design.md
│   └── mcp_integration.md
│
├── memory/                     ← Project-level memory (persistent context)
│   ├── decisions.md            ← Architecture decision records (ADRs)
│   ├── project_memory.json     ← Structured project state
│   └── lessons_learned.md      ← What failed and why
│
├── scripts/
│   ├── setup_linux.sh          ← One-command Linux dev environment setup
│   ├── setup_dev.sh            ← After-clone dev setup
│   └── deploy_beta.sh          ← Beta APK build + deploy
│
├── docker/
│   ├── docker-compose.yml      ← Full dev stack
│   └── docker-compose.dev.yml  ← Dev-only override
│
├── docs/
│   ├── architecture/           ← Architecture diagrams + decisions
│   ├── api/                    ← MCP + REST API docs
│   └── onboarding/             ← Developer onboarding guide
│
└── .github/
    ├── workflows/
    │   ├── android-ci.yml      ← Build + test on every PR
    │   ├── release.yml         ← APK release on tag push
    │   └── ai-review.yml       ← Claude reviews PRs
    └── ISSUE_TEMPLATE/
```

---

## 🧩 Tech Stack (Exact Versions)

### Android
| Component | Library | Version |
|-----------|---------|---------|
| Language | Kotlin | 2.0+ |
| Min SDK | Android 8.0 | API 26 |
| Target SDK | Android 15 | API 35 |
| Build System | Gradle | 8.x |
| DI | Hilt | 2.51+ |
| Async | Kotlin Coroutines + Flow | 1.8+ |
| Local DB | Room | 2.6+ |
| HTTP | Retrofit + OkHttp | 2.9+ / 4.12+ |
| JSON | Kotlinx Serialization | 1.7+ |
| UI | Jetpack Compose | 1.7+ |
| On-Device LLM | MediaPipe LLM Inference | Latest |
| On-Device Embeddings | MediaPipe Embedding | Latest |
| Wake Word | Porcupine Android SDK | 3.x |
| Vector DB | sqlite-vec | 0.1.x |
| AppFunctions | androidx.appfunctions | Beta |

### Server (Python)
| Component | Library | Version |
|-----------|---------|---------|
| Runtime | Python | 3.12+ |
| MCP Server | mcp | 1.x |
| API Framework | FastAPI | 0.115+ |
| LLM (Anthropic) | anthropic | 0.40+ |
| LLM (Google) | google-generativeai | 0.8+ |
| LLM (OpenAI) | openai | 1.50+ |
| Async | asyncio + uvicorn | — |
| Vector Ops | numpy | 2.x |
| HTTP Client | httpx | 0.27+ |

### Infrastructure
| Component | Tool |
|-----------|------|
| Containers | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| APK Signing | Gradle Play Publisher |
| Code Quality | Ktlint + Detekt (Kotlin), Ruff (Python) |
| Testing | JUnit5 + Espresso + Pytest |
| Secrets | GitHub Secrets + local .env |

---

## 🔑 Environment Variables

Never hardcode. Always use environment variables. See `.env.example`.

```bash
# LLM APIs
ANTHROPIC_API_KEY=sk-ant-...
GEMINI_API_KEY=AIza...
OPENAI_API_KEY=sk-...  # optional fallback

# Neuron Server
NEURON_SERVER_PORT=8384
NEURON_MCP_PORT=7384
NEURON_SECRET_TOKEN=...  # generated on first run

# Android Debug
ADB_DEVICE_ID=...  # for direct device testing
ANDROID_SDK_ROOT=/home/$USER/Android/Sdk

# Memory
NEURON_DB_PATH=~/.neuron/memory.db
NEURON_VECTOR_DB_PATH=~/.neuron/vectors.db
```

---

## 🎯 Current Sprint (Week 1 — THE NERVE)

**Goal:** Reliable Android UI control via AccessibilityService

**Active Tasks (check SPRINT.md for status):**
- [ ] NeuronAccessibilityService — register + configure
- [ ] UITreeReader — traverse + prune + serialize to JSON
- [ ] ActionExecutor — tap, type, swipe, scroll, global nav
- [ ] ScreenCapture — takeScreenshot() → compressed JPEG
- [ ] OverlayManager — floating input + status bubble
- [ ] Debug overlay — colored bounding boxes on UI elements

**Definition of Done for Week 1:**
> Given a text command via overlay, Neuron can open WhatsApp, tap a contact, type a message, and tap send — zero human interaction after the initial command.

---

## 📐 Coding Standards

### Kotlin
```kotlin
// ✅ Always use coroutines for async work
viewModelScope.launch { 
    val result = uiTreeReader.getUITree()
}

// ✅ Use sealed classes for results
sealed class NeuronResult<out T> {
    data class Success<T>(val data: T) : NeuronResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : NeuronResult<Nothing>()
}

// ✅ Always handle the sensitivity gate before cloud calls
if (sensitivityGate.isSensitive(currentScreen)) {
    return executeOnDevice(action)
}

// ❌ Never hardcode package names or UI element IDs
// ❌ Never block the main thread
// ❌ Never send screenshots of password fields to cloud
```

### Python (server)
```python
# ✅ Always use async/await
async def route_to_llm(prompt: str, tier: LLMTier) -> str:
    ...

# ✅ Always validate LLM output before returning
def parse_action(raw: str) -> ActionResult:
    try:
        data = json.loads(raw)
        return ActionResult(**data)
    except (json.JSONDecodeError, ValidationError) as e:
        raise LLMParseError(f"Invalid action JSON: {e}")

# ❌ Never await an LLM call without a timeout
# ❌ Never trust raw LLM JSON without schema validation
```

---

## 🤖 LLM Routing Rules

When writing code that calls an LLM, ALWAYS follow this routing:

```
T0 — Wake word detection → Porcupine (on-device, no LLM)
T1 — Single-step tasks, screen classification → Gemma 3n (on-device)
T2 — Multi-step execution, action selection → Gemini 2.5 Flash (cloud)
T3 — Complex planning, replanning → Claude Sonnet 4.5 or Gemini 3 Pro (cloud)
T4 — Any task on: password fields, banking apps, health apps → Gemma 3n ONLY (force on-device)
```

**T4 detection** — these patterns ALWAYS trigger T4:
- `node.isPassword == true` in AccessibilityNodeInfo
- Package names matching: `com.google.android.apps.walletnfcrel`, `net.one97.paytm`, `com.phonepe.app`, `in.amazon.mShop`, any banking app
- Screen contains: "PIN", "CVV", "Password", "OTP" as text labels

---

## 🔌 MCP Server Integration

Neuron runs a local MCP server at `ws://localhost:7384/neuron-mcp`.

**Available MCP Tools:**
```json
{
  "tools": [
    "neuron_take_screenshot",
    "neuron_read_ui_tree",
    "neuron_tap",
    "neuron_type_text",
    "neuron_swipe",
    "neuron_launch_app",
    "neuron_go_home",
    "neuron_go_back",
    "neuron_run_task",
    "neuron_get_memory",
    "neuron_list_apps"
  ]
}
```

To add a new MCP tool, add it to `server/mcp/mcp_tools.py` and register in `neuron_mcp_server.py`.

---

## 🧠 Memory System

Three layers — always use the right one:

```kotlin
// Working memory — current task, clears on completion
workingMemory.setCurrentTask(task)
workingMemory.addAction(action)

// Long-term memory — persists across sessions
longTermMemory.savePreference(UserPreference(key, value))
longTermMemory.saveWorkflow(AppWorkflow(pkg, task, steps))

// Episodic memory — full task traces for RAG
episodicMemory.recordTrace(TaskTrace(goal, steps, outcome))
// Retrieve: episodicMemory.findSimilar(currentGoal, topK = 3)
```

---

## 🚫 Hard Rules (NEVER VIOLATE)

1. **Never send password/PIN/CVV field content to any cloud API**
2. **Never execute irreversible actions (send, pay, delete) without user confirmation**
3. **Never start the AccessibilityService without explicit user enablement**
4. **Never store raw API keys in code — use environment variables**
5. **Never block the main thread with synchronous LLM calls**
6. **Never assume an action succeeded — always verify with a new UI tree capture**
7. **Never send a screenshot to cloud without first checking the sensitivity gate**
8. **Always log every action to the audit log with timestamp and app package**

---

## 🔗 Key External Resources

- AndroidWorld benchmark: https://github.com/google-research/android_world
- DroidRun (reference agent): https://github.com/droidrun/droidrun
- AppFunctions docs: https://developer.android.com/ai/appfunctions
- MCP specification: https://modelcontextprotocol.io
- Porcupine Android: https://github.com/Picovoice/porcupine
- whisper.cpp Android: https://github.com/ggml-org/whisper.cpp
- sqlite-vec: https://github.com/asg017/sqlite-vec
- Mobile MCP: https://github.com/mobile-next/mobile-mcp

---

## 💡 How to Use Claude Code on This Project

```bash
# Start Claude Code in the project root
cd ~/neuron && claude

# For focused work on a module
cd ~/neuron/android && claude

# Common Claude Code commands for this project
> "implement UITreeReader with smart pruning"
> "write tests for ActionExecutor"
> "debug why the accessibility service isn't receiving events"
> "add a new MCP tool called neuron_scroll_to_element"
> "review the LLM routing logic in LLMRouter.kt"
```

When Claude Code starts, it reads this CLAUDE.md automatically and has full project context.

---

## Auto Memory Protocol

After completing any significant work, update the relevant memory file in `memory/knowledge/`:

1. **Feature completed** → append to `memory/knowledge/features.md`
2. **Bug fixed** → append to `memory/knowledge/bugs.md`
3. **New pattern discovered** → append to `memory/knowledge/patterns.md`
4. **OEM/device issue found** → append to `memory/knowledge/compatibility.md`
5. **Performance benchmark** → append to `memory/knowledge/performance.md`
6. **Session ending** → append summary to `memory/changelog/sessions.md`

**Format:** `### [Date] — [Title]\n[Content]\n---`

Keep entries concise (3-5 lines). Link to relevant files with paths.
Commit memory updates with message: `docs(memory): update [file] — [brief description]`

After test runs or implementation milestones, update `memory/reliability/scores.json` with current component status and coverage.

---

*Last updated: March 2026 | Version: 0.1.0-alpha*
