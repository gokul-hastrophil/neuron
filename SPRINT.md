# NEURON — 3-Week MVP Sprint Plan

**Sprint Goal:** Working Android AI agent — voice/text in, autonomous app control out  
**Team Size:** Assume 3-5 developers (1 Android senior, 1 Android mid, 1 AI/Python, 1 fullstack)  
**Work Hours:** 8h/day, 5 days/week

---

## WEEK 1 — THE NERVE (Days 1-5)
**Milestone:** Reliable Android UI read + write control via AccessibilityService

### Day 1 — Service Foundation + UI Reading
**Owner:** Android Senior

Morning (4h):
- [ ] Create Android project with correct package `ai.neuron`
- [ ] Configure `AndroidManifest.xml` with `BIND_ACCESSIBILITY_SERVICE` permission
- [ ] Write `accessibility_service_config.xml` (all capabilities enabled)
- [ ] Implement `NeuronAccessibilityService.kt` skeleton
- [ ] Verify service appears in Settings > Accessibility

Afternoon (4h):
- [ ] Implement `UITreeReader.kt` — `getRootInActiveWindow()` traversal
- [ ] Implement smart pruning algorithm (remove invisible/non-interactive nodes)
- [ ] Serialize to compact JSON: `{id, text, desc, class, bounds, clickable, scrollable, password}`
- [ ] Write unit tests for pruning logic
- [ ] Log full tree to Logcat for debugging

**Definition of Done:** UI tree of WhatsApp home screen prints to Logcat as valid JSON in < 50ms

---

### Day 2 — Action Execution
**Owner:** Android Senior + Android Mid

Morning (4h):
- [ ] Implement `ActionExecutor.kt`
- [ ] `tapByNodeId(id: String)` — find node + `performAction(ACTION_CLICK)`
- [ ] `tapByCoordinate(x: Int, y: Int)` — `dispatchGesture(MotionEvent)`
- [ ] `typeText(nodeId: String, text: String)` — `ACTION_SET_TEXT`
- [ ] `swipe(direction: SwipeDirection, duration: Long)` — `dispatchGesture()`

Afternoon (4h):
- [ ] `performGlobalAction(HOME / BACK / RECENTS / NOTIFICATIONS)`
- [ ] `scrollInView(nodeId: String, direction: Direction)` — `ACTION_SCROLL_FORWARD/BACKWARD`
- [ ] `longPress(nodeId: String)` — dispatched gesture
- [ ] Action result verification — capture new tree, check if action had effect
- [ ] Stale node detection — hash tree before/after, detect no-change

**Definition of Done:** Can programmatically: open WhatsApp → tap "New Chat" → search for "Mom" → tap result → type "test message" → tap send

---

### Day 3 — Screenshots + Overlay System
**Owner:** Android Mid

Morning (4h):
- [ ] Implement `ScreenCapture.kt`
- [ ] `takeScreenshot()` via `AccessibilityService.takeScreenshot()`
- [ ] Compress to JPEG quality 80, max 1024×1024
- [ ] Return as ByteArray + Base64 encoded string
- [ ] Handle API < 30 fallback (MediaProjection with permission dialog)

Afternoon (4h):
- [ ] Implement `OverlayManager.kt`
- [ ] Floating bubble: `TYPE_ACCESSIBILITY_OVERLAY` window
- [ ] Drag to position functionality
- [ ] Expand on tap: show command text field
- [ ] Status states: idle (gray) / thinking (pulse purple) / executing (blue spin) / done (green) / error (red)
- [ ] Dismiss/minimize gesture

**Definition of Done:** Floating bubble appears over YouTube, is draggable, opens text input on tap, shows status animation

---

### Day 4 — App Launcher + Debug Tools
**Owner:** Android Senior + Android Mid

Morning (4h):
- [ ] `AppLauncher.kt` — launch by package name via Intent
- [ ] `InstalledAppsRegistry.kt` — list all installed apps + package names + labels
- [ ] Fuzzy app name matching ("open maps" → `com.google.android.apps.maps`)
- [ ] Deep link launching via `ACTION_VIEW` Intents

Afternoon (4h):
- [ ] Debug overlay: colored bounding boxes on all detected UI elements
- [ ] Different colors: clickable (blue), scrollable (green), text input (yellow), image (gray)
- [ ] Toggle debug mode via notification
- [ ] Implement accessibility event logging (track what the service sees)
- [ ] Basic crash reporter for AccessibilityService crashes

**Definition of Done:** Debug mode shows colored boxes on every UI element on screen in real-time

---

### Day 5 — Integration Test + OEM Compatibility
**Owner:** Full Team

Morning (4h):
- [ ] Integration test: end-to-end "open WhatsApp, message Mom" flow
- [ ] Test on Pixel (stock Android)
- [ ] Test on Samsung Galaxy (One UI)
- [ ] Document compatibility issues found

Afternoon (4h):
- [ ] Fix top 3 OEM compatibility issues
- [ ] Write `ACCESSIBILITY_NOTES.md` — known quirks per OEM
- [ ] Set up Hilt dependency injection
- [ ] Week 1 retrospective + task update

**WEEK 1 MILESTONE CHECK:**
> ✅ Can Neuron read any screen? ✅ Can Neuron execute any action? ✅ Does it work on Samsung + Pixel?

---

## WEEK 2 — THE BRAIN (Days 6-10)
**Milestone:** LLM-powered autonomous task execution via natural language

### Day 6 — LLM Client + Routing
**Owner:** AI Engineer + Android Senior

Morning (4h):
- [ ] Set up Retrofit + OkHttp for API calls
- [ ] `GeminiFlashClient.kt` — Gemini 2.5 Flash API client
- [ ] `ClaudeClient.kt` — Anthropic Claude Sonnet 4.5 API client
- [ ] Response parsing — extract action JSON from LLM output
- [ ] Schema validation with Kotlinx Serialization

Afternoon (4h):
- [ ] `LLMRouter.kt` — tier-based routing logic (T0→T4)
- [ ] `SensitivityGate.kt` — password field + banking app detection
- [ ] `IntentClassifier.kt` — classify user intent (domain, complexity, sensitivity)
- [ ] API key management via BuildConfig + environment variables
- [ ] Retry logic with exponential backoff (3 retries, 2x backoff)

**Definition of Done:** `LLMRouter.route("send WhatsApp to Mom")` returns structured action plan JSON

---

### Day 7 — Prompt Engineering + ReAct Loop
**Owner:** AI Engineer

Morning (4h):
- [ ] Design system prompt for Android agent role
  ```
  You are Neuron, an AI agent that controls Android phones.
  You receive the current screen state as a UI tree JSON.
  You respond with a single action in this exact JSON format:
  {"action_type": "tap|type|swipe|launch|navigate|wait|done|error",
   "target_id": "resource-id or empty",
   "target_text": "visible text of element or empty",
   "value": "text to type or direction to swipe",
   "confidence": 0.0-1.0,
   "reasoning": "why this action"}
  ```
- [ ] Test prompt with 10 different screens and goals
- [ ] Refine prompt based on failure cases
- [ ] Document prompt versions in `docs/prompts/`

Afternoon (4h):
- [ ] `PlanAndExecuteEngine.kt` — the ReAct loop state machine
- [ ] States: IDLE → PLANNING → EXECUTING → VERIFYING → DONE/ERROR
- [ ] Max 20 steps per task, timeout 60s total
- [ ] Step logging to audit log
- [ ] Confidence threshold: if < 0.6, ask user for clarification

**Definition of Done:** "Open Settings and turn on dark mode" completes in < 5 steps without human interaction

---

### Day 8 — NeuronBrainService + Working Memory
**Owner:** Android Senior + AI Engineer

Morning (4h):
- [ ] `NeuronBrainService.kt` — Android ForegroundService that hosts the brain
- [ ] Notification: "Neuron is active" (required for foreground service)
- [ ] Receives commands via Binder (from overlay) + BroadcastReceiver
- [ ] Coroutine scope management (cancel on service stop)
- [ ] Service lifecycle: start → run → restart on crash

Afternoon (4h):
- [ ] `WorkingMemory.kt` — in-memory state holder
  - Current task goal, step index, action history (last 10), screen state hash
  - Serialize/deserialize for process death recovery (SharedPreferences)
- [ ] Wire overlay → brain service → executor → overlay feedback loop
- [ ] End-to-end test: type in overlay → brain executes → overlay shows result

**Definition of Done:** Full loop works: type "open Chrome and search for weather" → Neuron does it → shows "Done" in overlay

---

### Day 9 — Multi-Step + Confirmation Gate + Error Handling
**Owner:** Full Team

Morning (4h):
- [ ] Multi-step plan support — `PlanAndExecuteEngine` executes full plan JSON
- [ ] App switching between steps (Intent + AccessibilityService coordination)
- [ ] `ConfirmationOverlay.kt` — shows pending action with approve/cancel
- [ ] Detect irreversible action types in LLM response
- [ ] User approval flow integrated into execution loop

Afternoon (4h):
- [ ] Comprehensive error handling:
  - Element not found → scroll and retry
  - LLM parse failure → retry with different prompt
  - App crash during execution → graceful recovery
  - Network timeout → fallback or inform user
- [ ] Error messages displayed in overlay with recovery suggestions
- [ ] Edge case: app navigates away mid-task (notification received, call, etc.)

**Definition of Done:** "Order my usual coffee from Starbucks" pauses at checkout with confirmation overlay

---

### Day 10 — Voice Input + Integration Tests
**Owner:** Full Team

Morning (4h):
- [ ] `SpeechRecognitionService.kt` — Android `SpeechRecognizer` integration
- [ ] Hold-to-speak on floating button
- [ ] Visual waveform animation during recording
- [ ] Transcript displayed in overlay before submitting
- [ ] Edit before submit option

Afternoon (4h):
- [ ] Integration test suite — 10 tasks across 5 apps:
  - [ ] WhatsApp: "Send Mom a message saying I'll be late"
  - [ ] Chrome: "Search for best restaurants near me"
  - [ ] Settings: "Turn on Airplane mode"
  - [ ] Contacts: "Call John Smith"
  - [ ] Gmail: "Check my unread emails"
  - [ ] YouTube: "Play lofi music"
  - [ ] Maps: "Navigate home"
  - [ ] Calendar: "Add meeting tomorrow at 3pm"
  - [ ] Camera: "Take a photo"
  - [ ] Play Store: "Search for Spotify"
- [ ] Record pass/fail + success rate target: >70%

**WEEK 2 MILESTONE CHECK:**
> ✅ Does the agent complete tasks autonomously? ✅ Does confirmation work? ✅ >70% success rate?

---

## WEEK 3 — MEMORY + SDK + SHIP (Days 11-15)
**Milestone:** Beta-ready APK with memory, SDK skeleton, and MCP server

### Day 11 — Long-Term Memory (Room DB)
**Owner:** Android Mid + AI Engineer

Morning (4h):
- [ ] Set up Room DB with migrations
- [ ] `UserPreference` entity: `(id, category, key, value, confidence, updatedAt)`
- [ ] `AppWorkflow` entity: `(id, packageName, taskType, actionSequenceJson, successCount, lastUsed)`
- [ ] `ContactAssociation` entity: `(id, displayName, canonicalKey, lastUsed)`
- [ ] DAOs + Repository pattern

Afternoon (4h):
- [ ] Inject preferences into LLM prompts
- [ ] After successful task: auto-extract preferences (app choice, style, timing)
- [ ] `LongTermMemory.kt` — unified API for reading/writing memories
- [ ] Settings screen: view + delete stored memories

**Definition of Done:** Second time user runs "order coffee", Neuron pre-fills their usual without asking

---

### Day 12 — MCP Server (Python)
**Owner:** AI Engineer

Morning (4h):
- [ ] Set up Python FastAPI + MCP server
- [ ] `neuron_mcp_server.py` — exposes phone control via MCP protocol
- [ ] Tool: `neuron_read_ui_tree` — returns current screen state
- [ ] Tool: `neuron_take_screenshot` — returns base64 JPEG
- [ ] Tool: `neuron_tap` — taps coordinate or element
- [ ] ADB bridge: Python → ADB → NeuronPortAPK (Day 1 architecture)

Afternoon (4h):
- [ ] Tool: `neuron_type_text`
- [ ] Tool: `neuron_launch_app`
- [ ] Tool: `neuron_run_task` — full natural language task execution
- [ ] Authentication: secret token header
- [ ] Test with Claude Desktop: `"take a screenshot of my phone"`
- [ ] Write MCP server setup guide in `docs/api/mcp_guide.md`

**Definition of Done:** Claude Desktop connects to Neuron MCP server and can take phone screenshot

---

### Day 13 — SDK Skeleton + AppFunctions Bridge
**Owner:** Android Senior

Morning (4h):
- [ ] `NeuronSDK.kt` — public API surface
  ```kotlin
  NeuronSDK.registerTool(
      name = "send_email",
      description = "Send an email",
      params = listOf(Param("to"), Param("subject"), Param("body")),
      execute = { params -> ... }
  )
  ```
- [ ] `ToolRegistry.kt` — stores + discovers registered tools
- [ ] Tool discovery injected into LLM planner (planner sees all available tools)

Afternoon (4h):
- [ ] `AppFunctionsBridge.kt` — query `AppFunctionsManager` for installed app capabilities
- [ ] Index discovered AppFunctions into `ToolRegistry`
- [ ] Unified tool invocation: AppFunction vs AccessibilityService vs custom tool — same API
- [ ] Write SDK quick-start guide: `docs/onboarding/sdk_quickstart.md`

**Definition of Done:** Developer can register a custom tool in 10 lines of Kotlin and Neuron's brain uses it

---

### Day 14 — Polish + Settings + Onboarding
**Owner:** Full Team

Morning (4h):
- [ ] Onboarding flow: welcome → grant accessibility permission → test basic command → done
- [ ] Settings screen: API keys, privacy toggles, cloud/on-device toggle, memory management
- [ ] Permissions explanation screen (explain what Accessibility permission does and doesn't do)
- [ ] App sensitivity override: user can mark any app as "always on-device"

Afternoon (4h):
- [ ] Audit log UI: list of all past actions, filterable by app
- [ ] Performance optimization: cache UI trees for 200ms to reduce redundant reads
- [ ] APK size optimization: ProGuard rules, resource shrinking
- [ ] Build release variant

**Definition of Done:** New user can complete onboarding in < 3 minutes and run first task

---

### Day 15 — Beta APK Release + Documentation
**Owner:** Full Team

Morning (4h):
- [ ] Final integration test run — all 10 test tasks
- [ ] Fix critical bugs found in testing
- [ ] Sign release APK with debug keystore
- [ ] Upload to GitHub Releases
- [ ] Write installation guide (`docs/onboarding/install.md`)

Afternoon (4h):
- [ ] `README.md` — compelling public readme with demo GIF
- [ ] `CONTRIBUTING.md` — how to contribute
- [ ] API documentation auto-generation (Dokka for Kotlin, Sphinx for Python)
- [ ] Set up beta tester invite list
- [ ] Week 3 retrospective + Month 2 planning

**WEEK 3 MILESTONE CHECK — SHIP CRITERIA:**
> ✅ >70% task success rate | ✅ <30s average task time | ✅ Clean onboarding | ✅ MCP server working | ✅ APK < 50MB | ✅ No crashes in 1h test session

---

## Beyond Week 3 — Month 2 Preview

| Week | Theme | Key Deliverable |
|------|-------|-----------------|
| 4 | On-Device Brain | Gemma 3n via MediaPipe — T0/T1 tasks offline |
| 5 | Wake Word | "Hey Neuron" always-listening via Porcupine |
| 6 | Episodic Memory | sqlite-vec + EmbeddingGemma — task trace retrieval |
| 7 | Multi-App Tasks | Cross-app task sequences |
| 8 | Developer SDK Release | Python SDK on PyPI, Kotlin SDK on Maven |
| 9-12 | Platform Features | Proactive suggestions, scheduling, deeper integrations |
