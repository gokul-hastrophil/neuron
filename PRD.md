# NEURON — Product Requirements Document (PRD)

**Version:** 0.1 — MVP  
**Target Release:** End of Week 3  
**Status:** ACTIVE

---

## P0 — Must Have (Week 3 MVP)

### P0.1 — AccessibilityService Core
- **What:** Android Accessibility Service that can read any app's UI and perform actions
- **Acceptance Criteria:**
  - [ ] Service activates via Settings > Accessibility without crash
  - [ ] `getRootInActiveWindow()` returns full UI tree within 50ms
  - [ ] UI tree serializes to compact JSON < 5KB for average screen
  - [ ] Smart pruning removes invisible/non-interactive nodes
  - [ ] `performAction(ACTION_CLICK)` successfully taps elements by node ID and by coordinate
  - [ ] `ACTION_SET_TEXT` types text into any text input field
  - [ ] `dispatchGesture()` performs swipe up/down/left/right
  - [ ] `performGlobalAction(HOME/BACK/RECENTS)` works
  - [ ] `takeScreenshot()` captures screen as JPEG < 200KB
  - [ ] All above work on Android 10, 12, 14, 15
  - [ ] All above work on Samsung One UI, Pixel stock Android

### P0.2 — Floating Overlay
- **What:** Always-on floating button that opens the Neuron command interface
- **Acceptance Criteria:**
  - [ ] Floating button appears over all apps after service activation
  - [ ] Button is draggable (user can position it)
  - [ ] Tap opens floating command text field
  - [ ] Text field accepts free-form natural language input
  - [ ] Submit button (or Enter key) sends command to brain
  - [ ] Status indicator shows: idle / thinking / executing / done / error
  - [ ] Works over all tested apps (WhatsApp, Chrome, YouTube, Settings)

### P0.3 — Cloud LLM Brain (Gemini Flash)
- **What:** Connect UI tree + user command → Gemini Flash → action JSON → execute
- **Acceptance Criteria:**
  - [ ] Formats prompt with system context + UI tree + user goal
  - [ ] Receives structured action JSON (action_type, target, value, confidence, reasoning)
  - [ ] Validates JSON schema before executing
  - [ ] Executes action via AccessibilityService
  - [ ] Captures new screen state after action
  - [ ] Loops until task complete or max 20 steps reached
  - [ ] Timeout: 60 seconds per task max, 10 seconds per LLM call max
  - [ ] Error recovery: retries 3x on failure, reports error on 3rd fail

### P0.4 — Confirmation Gate
- **What:** User must confirm before irreversible actions
- **Acceptance Criteria:**
  - [ ] Irreversible actions defined: send message, make payment, delete, submit form, place order
  - [ ] Confirmation overlay shows: what will happen + which app + undo information
  - [ ] User can approve or cancel
  - [ ] Cancelled actions are logged but not executed
  - [ ] Approved actions execute within 500ms

### P0.5 — Sensitivity Gate
- **What:** Block cloud processing for sensitive screens
- **Acceptance Criteria:**
  - [ ] Detects password fields via `AccessibilityNodeInfo.isPassword`
  - [ ] Detects banking apps via package name blocklist
  - [ ] On sensitive screen: uses on-device Gemma 3n only (or shows limitation message in MVP)
  - [ ] Never sends screenshot of sensitive screen to cloud
  - [ ] Audit log records every sensitivity gate trigger

### P0.6 — Voice Input (Basic)
- **What:** Voice command via Android SpeechRecognizer
- **Acceptance Criteria:**
  - [ ] Tap floating button → hold-to-speak mode
  - [ ] Android SpeechRecognizer captures voice → text
  - [ ] Text flows into same brain pipeline as typed commands
  - [ ] Visual feedback during recording (animated indicator)

### P0.7 — Audit Log
- **What:** Every action recorded in local SQLite
- **Acceptance Criteria:**
  - [ ] Schema: `action_id, timestamp, app_package, action_type, element_id, value, outcome, task_goal`
  - [ ] Viewable in Neuron settings screen
  - [ ] Exportable as JSON
  - [ ] Never synced to cloud without explicit user consent
  - [ ] Retained for 30 days by default (user-configurable)

---

## P1 — Should Have (Month 2)

### P1.1 — Wake Word
- Porcupine "Hey Neuron" always-on detection via ForegroundService
- Custom wake word training option

### P1.2 — On-Device LLM (Gemma 3n)
- MediaPipe LLM Inference integration
- T0/T1 tasks handled entirely on-device
- Offline mode: basic single-step tasks work without internet

### P1.3 — Long-Term Memory
- Room DB: `UserPreference`, `AppWorkflow`, `ContactAssociation` tables
- Injected into prompts: "User prefers Delta flights, usually orders Americano"

### P1.4 — Multi-App Task Support
- Tasks that span multiple apps ("Take the photo from Gallery and share it on Instagram with caption X")
- App switching via Intent + AccessibilityService

### P1.5 — AppFunctions Integration
- Discover installed apps' AppFunctions declarations
- Use AppFunctions API when available (faster, more reliable than accessibility simulation)

---

## P2 — Could Have (Month 3-6)

### P2.1 — Episodic Memory + RAG
- Full task trace recording
- Similar task retrieval for few-shot planning
- sqlite-vec + EmbeddingGemma on-device

### P2.2 — MCP Server
- Local WebSocket MCP server
- Allows Claude Code, Claude Desktop, Cursor to control the phone via Neuron

### P2.3 — Neuron Developer SDK
- `@NeuronTool` annotation for custom tool registration (Kotlin)
- Python SDK for server-side tool registration
- Published to Maven Central + PyPI

### P2.4 — Proactive Suggestions
- Screen monitoring → contextual suggestions
- "You just got a map link — want me to open it in navigation?"

### P2.5 — Task Scheduling
- "Do this at 6pm" / "Do this when I arrive home"
- WorkManager-based scheduled tasks

---

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Task start latency (cloud) | < 2 seconds from command submission |
| Action execution latency | < 200ms per action |
| Battery impact | < 3% additional drain/hour when idle |
| Memory footprint (service) | < 80MB RAM when idle |
| Crash-free sessions | > 99% |
| Accessibility service stability | 24h uptime without restart |
| APK size | < 50MB |

---

## Out of Scope (MVP)

- iOS support
- Root-only features
- Biometric authentication for Neuron
- Cloud sync of memory/preferences
- Multi-device support
- Video/camera control
- Call interception
- Notification automation (Phase 2)
