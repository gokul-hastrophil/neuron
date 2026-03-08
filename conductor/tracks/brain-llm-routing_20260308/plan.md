# Implementation Plan: Week 2 — The Brain: LLM Routing + Planning Engine

**Track ID:** brain-llm-routing_20260308
**Spec:** [spec.md](./spec.md)
**Created:** 2026-03-08
**Status:** [ ] Not Started

## Overview

Build Neuron's AI brain in 6 phases following strict TDD for must-TDD components (LLMRouter, SensitivityGate, IntentClassifier, PlanAndExecuteEngine). LLM clients use Retrofit + OkHttp, all async via coroutines, sealed result types throughout.

## Phase 1: LLM Client Foundation

Set up API clients for Gemini Flash and Claude, response parsing, and action schema validation.

### Tasks

- [ ] Task 1.1: Add API key configuration — `BuildConfig` fields for `ANTHROPIC_API_KEY` and `GEMINI_API_KEY` via `local.properties` or environment variables
- [ ] Task 1.2: Define `LLMResponse` data class and `NeuronActionPlan` schema using KotlinX Serialization — action_type, target_id, target_text, value, confidence, reasoning
- [ ] Task 1.3: Implement `GeminiFlashClient.kt` — Retrofit interface for Gemini 2.5 Flash API, request/response models, coroutine suspend functions
- [ ] Task 1.4: Implement `ClaudeClient.kt` — Retrofit interface for Anthropic Claude API, message format, coroutine suspend functions
- [ ] Task 1.5: Implement `LLMClientManager.kt` — unified interface over both clients, timeout (T2: 2s, T3: 5s), retry with exponential backoff (3 retries, 2x)
- [ ] Task 1.6: Add OkHttp logging interceptor for debug builds, sanitize API keys from logs

### Verification

- [ ] Both clients compile and can be instantiated via Hilt
- [ ] Response parsing handles valid JSON, malformed JSON, and timeout gracefully

## Phase 2: SensitivityGate + LLMRouter (TDD)

Must-TDD components. Tests first for sensitivity detection and tier routing.

### Tasks

- [ ] Task 2.1: Write `SensitivityGateTest.kt` — tests for: password field detection, banking app packages (PayTM, PhonePe, GPay, etc.), health app packages, PIN/CVV/OTP text labels, non-sensitive screens pass through, mixed screens (sensitive + non-sensitive nodes)
- [ ] Task 2.2: Implement `SensitivityGate.kt` — `isSensitive(uiTree: UITree): Boolean`, package name matching, node field scanning, text label pattern matching
- [ ] Task 2.3: Write `LLMRouterTest.kt` — tests for: T1 routing for simple single-step tasks, T2 routing for multi-step execution, T3 routing for complex planning, T4 forced when sensitivity gate triggers, fallback when primary tier fails, timeout handling per tier
- [ ] Task 2.4: Implement `LLMRouter.kt` — `route(command: String, uiTree: UITree, classifier: IntentClassification): NeuronResult<LLMResponse>`, tier selection logic, sensitivity override to T4, fallback chain
- [ ] Task 2.5: Define `LLMTier` enum (T0-T4) with latency budgets and model mappings

### Verification

- [ ] All `SensitivityGateTest` tests pass
- [ ] All `LLMRouterTest` tests pass
- [ ] Sensitivity gate correctly blocks cloud routing for banking/password screens

## Phase 3: IntentClassifier + Prompt Engineering (TDD)

Classify user commands by complexity and domain, design system prompts.

### Tasks

- [ ] Task 3.1: Write `IntentClassifierTest.kt` — tests for: single-step commands ("go home") → SIMPLE, multi-step commands ("message Mom on WhatsApp") → MODERATE, complex commands ("find cheapest flight to NYC") → COMPLEX, ambiguous commands → ASK_USER, domain classification (messaging, navigation, settings, search, etc.)
- [ ] Task 3.2: Implement `IntentClassifier.kt` — `classify(command: String): IntentClassification` with complexity level (SIMPLE/MODERATE/COMPLEX) and domain, keyword-based heuristics for initial version
- [ ] Task 3.3: Define `IntentClassification` data class — complexity, domain, estimatedSteps, suggestedTier
- [ ] Task 3.4: Write system prompt v1 for Android agent role — UI tree JSON input, single action JSON output, action_type enum, confidence score, reasoning
- [ ] Task 3.5: Write system prompt v2 for multi-step planning — full plan JSON array output, step dependencies, verification conditions
- [ ] Task 3.6: Create `docs/prompts/` directory with versioned prompt files and test cases

### Verification

- [ ] All `IntentClassifierTest` tests pass
- [ ] System prompts produce valid action JSON when tested with sample UI trees
- [ ] Prompt versions documented in `docs/prompts/`

## Phase 4: PlanAndExecuteEngine (TDD)

Must-TDD component. ReAct loop state machine that drives autonomous task execution.

### Tasks

- [ ] Task 4.1: Write `PlanAndExecuteEngineTest.kt` — tests for: state transitions (IDLE→PLANNING→EXECUTING→VERIFYING→DONE), max step limit (20), total timeout (60s), confidence below threshold triggers ASK_USER, successful single-step task, successful multi-step task, error recovery (element not found → retry), graceful termination on unrecoverable error
- [ ] Task 4.2: Define `EngineState` sealed class — Idle, Planning, Executing, Verifying, WaitingForUser, Done, Error
- [ ] Task 4.3: Implement `PlanAndExecuteEngine.kt` — `execute(command: String): Flow<EngineState>`, ReAct loop: observe UI tree → send to LLM → parse action → execute via ActionExecutor → verify → repeat
- [ ] Task 4.4: Implement step logging — each step records: stepIndex, uiTreeHash, llmTier, action, result, durationMs to audit log
- [ ] Task 4.5: Implement replanning — if verification fails after action, re-observe and send updated UI tree to LLM for next action
- [ ] Task 4.6: Implement abort conditions — max steps exceeded, total timeout, repeated failures (same action 3x), user cancellation

### Verification

- [ ] All `PlanAndExecuteEngineTest` tests pass
- [ ] State machine transitions are correct and observable via Flow
- [ ] Audit log captures every step with timing

## Phase 5: NeuronBrainService + WorkingMemory

Foreground service hosting the brain, working memory for in-session state, wiring to overlay and executor.

### Tasks

- [ ] Task 5.1: Write `WorkingMemoryTest.kt` — tests for: store/retrieve current task, action history (last 10), screen state hash, serialization roundtrip via SharedPreferences, clear on task completion
- [ ] Task 5.2: Implement `WorkingMemory.kt` — in-memory state holder with SharedPreferences backup for process death recovery
- [ ] Task 5.3: Implement `NeuronBrainService.kt` — foreground service with persistent notification "Neuron is active", coroutine scope, Hilt injection
- [ ] Task 5.4: Implement command reception — Binder interface for overlay to send commands, BroadcastReceiver for ADB testing
- [ ] Task 5.5: Wire the full loop: OverlayManager → NeuronBrainService → PlanAndExecuteEngine → ActionExecutor → UITreeReader → back to overlay with state updates
- [ ] Task 5.6: Implement `ConfirmationGate.kt` — detect irreversible actions (send, pay, delete, post) in LLM response, pause execution, show confirmation overlay, resume or cancel based on user choice

### Verification

- [ ] All `WorkingMemoryTest` tests pass
- [ ] Brain service starts as foreground service with notification
- [ ] Full loop: type command → brain plans → executor acts → overlay shows Done/Error
- [ ] Confirmation gate pauses on "send message" type actions

## Phase 6: Integration + Error Handling + Benchmark

End-to-end testing, error recovery, and benchmark across apps.

### Tasks

- [ ] Task 6.1: Implement error recovery strategies — element not found: scroll and retry, LLM parse failure: retry with simplified prompt, app crash: relaunch and retry, network timeout: show error in overlay
- [ ] Task 6.2: Implement error display in overlay — error message, recovery suggestion, retry button
- [ ] Task 6.3: On-device integration test: "Open Settings and turn on dark mode" — verify < 5 steps
- [ ] Task 6.4: On-device integration test: "Open Chrome and search for weather" — verify full loop
- [ ] Task 6.5: Run 10-task benchmark across available apps — record pass/fail, measure success rate
- [ ] Task 6.6: Performance profiling — measure per-step latency, LLM call timing, total task time
- [ ] Task 6.7: Update `ACCESSIBILITY_NOTES.md` with brain-related OEM quirks found during testing

### Verification

- [ ] Error recovery works for common failure modes
- [ ] Integration tests pass on at least 1 device
- [ ] Benchmark results documented with success rate

## Final Verification

- [ ] All acceptance criteria met (7/7)
- [ ] All tests passing: `./gradlew test`
- [ ] Lint clean: `./gradlew ktlintCheck`
- [ ] >70% success rate on benchmark
- [ ] No API keys in committed code
- [ ] System prompts versioned in `docs/prompts/`
- [ ] Ready for review

---

_Generated by Conductor. Tasks will be marked [~] in progress and [x] complete._
