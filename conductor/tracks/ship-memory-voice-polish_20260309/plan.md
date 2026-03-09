# Implementation Plan: Week 3 — Ship: Memory, Voice, Overlay, Polish

**Track ID:** ship-memory-voice-polish_20260309
**Spec:** [spec.md](./spec.md)
**Created:** 2026-03-09
**Status:** [ ] Not Started

## Overview

Seven-phase implementation covering integration fixes, long-term memory, MCP server, SDK skeleton, voice input + overlay, onboarding/settings polish, and beta release with final benchmark.

---

## Phase 0: Integration Fixes (lift benchmark from 64% → 70%+)

Fix the 3 specific failures identified in the 16-test integration benchmark (ITR-001).

### Tasks

- [x] Task 0.1: Add intent-based app resolution fallback — use standard Intent actions (ACTION_DIAL, ACTION_CAMERA_BUTTON, ACTION_SET_ALARM) when package name resolution fails, so OEM devices (Honor dialer = com.hihonor.contacts) resolve correctly
- [x] Task 0.2: Add "press Enter to submit" hint to system prompt — when the LLM types text in a search/URL bar, the prompt should instruct it to submit via NAVIGATE ENTER or TAP on a suggestion, preventing the TYPE-repeat loop
- [x] Task 0.3: Add NAVIGATE NOTIFICATIONS action mapping — map SWIPE DOWN from top / "open notifications" commands to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
- [x] Task 0.4: Add NAVIGATE ENTER key action — map "press enter" / "submit" to InputConnection dispatch of KEYCODE_ENTER for search bar submission
- [ ] Task 0.5: Re-run 16-test benchmark on device and verify >70% pass rate (requires ADB device)

### Verification

- [ ] T11 "open the phone dialer" passes (intent-based resolution)
- [ ] T09 "open Chrome and search for weather" completes TYPE → ENTER → DONE
- [ ] T16 "pull down notification shade" passes (NAVIGATE NOTIFICATIONS)
- [ ] Overall pass rate >=70% (>=10/14 excluding T1 skips)

---

## Phase 1: Long-Term Memory (Room DB)

Persistent memory so Neuron learns user preferences and caches successful workflows.

### Tasks

- [x] Task 1.1: Set up Room DB with `NeuronDatabase`, migration strategy, and Hilt integration
- [x] Task 1.2: Create `UserPreference` entity + DAO — `(id, category, key, value, confidence, updatedAt)`
- [x] Task 1.3: Create `AppWorkflow` entity + DAO — `(id, packageName, taskType, actionSequenceJson, successCount, failCount, avgLatencyMs, lastUsed)`
- [x] Task 1.4: Create `ContactAssociation` entity + DAO — `(id, displayName, canonicalKey, packageName, lastUsed)`
- [x] Task 1.5: Implement `LongTermMemory.kt` — unified repository for read/write across all entities
- [x] Task 1.6: Auto-extract preferences after successful task completion — detect app choices, preferred contacts, common workflows
- [x] Task 1.7: Inject cached workflows into LLM prompt context — if a similar task was completed before, include the action sequence as a hint
- [x] Task 1.8: Write unit tests for Room DAOs and LongTermMemory repository (target: 15+ tests)

### Verification

- [ ] Room DB creates on first launch, survives app restart
- [ ] After "open calculator" succeeds, re-running the same command uses cached workflow (skips LLM call or uses it as hint)
- [ ] All DAO unit tests pass
- [ ] SharedPreferences WorkingMemory still functions alongside Room LongTermMemory

---

## Phase 2: MCP Server (Python)

Python MCP server exposing Neuron's phone control capabilities over WebSocket.

### Tasks

- [x] Task 2.1: Set up Python project structure — `server/mcp/`, FastAPI + uvicorn, dependencies in `requirements.txt`
- [x] Task 2.2: Implement MCP tool: `neuron_read_ui_tree` — ADB bridge to capture and return current UI tree JSON
- [x] Task 2.3: Implement MCP tool: `neuron_take_screenshot` — ADB screencap → base64 JPEG
- [x] Task 2.4: Implement MCP tools: `neuron_tap`, `neuron_type_text`, `neuron_swipe` — ADB input commands
- [x] Task 2.5: Implement MCP tool: `neuron_launch_app` — ADB am start with package resolution
- [x] Task 2.6: Implement MCP tool: `neuron_run_task` — send natural language command via ADB broadcast to NeuronBrainService
- [x] Task 2.7: Add token-based authentication (X-Neuron-Token header)
- [x] Task 2.8: Write MCP server setup guide in `docs/api/mcp_guide.md`
- [x] Task 2.9: Write pytest tests for MCP tools (mocked ADB, target: 10+ tests)

### Verification

- [ ] `python -m server.mcp.neuron_mcp_server` starts on ws://localhost:7384
- [ ] Claude Desktop (or MCP inspector) connects and can list tools
- [ ] `neuron_take_screenshot` returns valid base64 JPEG from connected device
- [ ] `neuron_run_task("open calculator")` triggers NeuronBrainService and calculator opens

---

## Phase 3: SDK Skeleton + Tool Registry

Public API for developers to register custom tools that Neuron's brain can invoke.

### Tasks

- [x] Task 3.1: Implement `NeuronSDK.kt` — public API: `init()`, `registerTool()`, `unregisterTool()`, `listTools()`
- [x] Task 3.2: Implement `ToolRegistry.kt` — in-memory registry with tool name, description, parameter schema, and execute callback
- [x] Task 3.3: Inject registered tools into LLM system prompt — planner sees all available tools and can invoke them
- [x] Task 3.4: Implement `AppFunctionsBridge.kt` — query AppFunctionsManager for installed app capabilities, index into ToolRegistry
- [x] Task 3.5: Write SDK quick-start guide in `docs/onboarding/sdk_quickstart.md`
- [x] Task 3.6: Write unit tests for ToolRegistry (register, unregister, invoke, duplicate detection — target: 8+ tests)

### Verification

- [ ] Developer can register a custom tool in <15 lines of Kotlin
- [ ] LLM planner can see and select registered tools in its action space
- [ ] ToolRegistry handles duplicate names, missing params, and unregistration correctly
- [ ] All SDK unit tests pass

---

## Phase 4: Voice Input + Overlay Polish

Hands-free voice input and improved overlay UX.

### Tasks

- [x] Task 4.1: Implement `SpeechRecognitionManager.kt` — Android SpeechRecognizer with streaming partial results, state machine (IDLE→LISTENING→PROCESSING→IDLE/ERROR)
- [x] Task 4.2: Add hold-to-speak interaction via `VoiceInputController.kt` — press/hold starts recording, release submits, bridges speech→overlay
- [x] Task 4.3: Show transcript in overlay before submitting — partialTranscript exposed via StateFlow, VoiceInputController provides it to overlay
- [x] Task 4.4: Add visual waveform/pulse animation during recording — rmsDb StateFlow exposed for waveform visualization, LISTENING overlay state added
- [x] Task 4.5: Implement overlay status states — HIDDEN, IDLE, LISTENING, THINKING, EXECUTING, DONE, ERROR in OverlayManager.OverlayState
- [x] Task 4.6: Add "cancel" gesture — VoiceInputController.onCancel() aborts recognition and returns overlay to IDLE
- [x] Task 4.7: Implement `ConfirmationGate.kt` — checks requiresConfirmation flag, sensitive flag, low confidence (<0.7), and dangerous button keywords (send/delete/pay/transfer)

### Verification

- [ ] Hold-to-speak on overlay → speech recognized → command executes → overlay shows result
- [ ] Overlay states animate correctly through full lifecycle
- [ ] Confirmation gate triggers for "send message" and "delete" actions
- [ ] Voice input works while another app is in foreground

---

## Phase 5: Onboarding + Settings + Polish

First-run experience and user settings.

### Tasks

- [ ] Task 5.1: Implement onboarding flow — Welcome → Grant Accessibility Permission → Test Basic Command → Done
- [ ] Task 5.2: Add permissions explanation screen — explain what AccessibilityService reads and what it does NOT do
- [ ] Task 5.3: Implement Settings screen — API key entry (Gemini, Ollama, OpenRouter), privacy toggles (cloud on/off), T4 app sensitivity overrides
- [ ] Task 5.4: Add memory management in Settings — view stored preferences/workflows, delete individual entries or clear all
- [ ] Task 5.5: Implement audit log UI — list of past actions filterable by app and date
- [ ] Task 5.6: Performance optimization — cache UI trees for 200ms, ProGuard rules, resource shrinking
- [ ] Task 5.7: APK size audit — target <50MB, remove unused resources, optimize native libs

### Verification

- [ ] New user completes onboarding in <3 minutes
- [ ] Settings persist across app restart
- [ ] Audit log shows all actions from integration test with timestamps and package names
- [ ] Release APK <50MB, idle RAM <80MB

---

## Phase 6: Beta Release + Final Benchmark

Signed APK, documentation, and final validation.

### Tasks

- [ ] Task 6.1: Re-run full 16-test integration benchmark (with Gemini if quota available) — document results
- [ ] Task 6.2: Fix any critical bugs found in final benchmark
- [ ] Task 6.3: Sign release APK with release keystore
- [ ] Task 6.4: Upload APK to GitHub Releases with changelog
- [ ] Task 6.5: Write installation guide (`docs/onboarding/install.md`)
- [ ] Task 6.6: Update README.md — project overview, architecture diagram, demo instructions, getting started
- [ ] Task 6.7: Generate API documentation (Dokka for Kotlin, docstrings for Python MCP)
- [ ] Task 6.8: Run 1-hour soak test — continuous task execution, verify no crashes or memory leaks

### Verification

- [ ] Integration benchmark >70% pass rate
- [ ] Release APK installs cleanly on Honor ELI-NX9 and Xiaomi Redmi Note 9 Pro
- [ ] README has clear setup instructions and demo flow
- [ ] No crashes in 1-hour soak test
- [ ] All unit tests pass (target: 130+ total)

---

## Final Verification

- [ ] All 11 acceptance criteria from spec.md met
- [ ] All tests passing (unit + integration)
- [ ] Documentation complete (README, install guide, MCP guide, SDK quickstart)
- [ ] Release APK on GitHub Releases
- [ ] Memory files updated (features, reliability, sessions)
- [ ] Ready for Month 2 planning

---

_Generated by Conductor. Tasks will be marked [~] in progress and [x] complete._
