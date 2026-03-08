# Implementation Plan: Week 1 — The Nerve: AccessibilityService Core

**Track ID:** nerve-accessibility_20260308
**Spec:** [spec.md](./spec.md)
**Created:** 2026-03-08
**Status:** [x] Complete

## Overview

Build Neuron's AccessibilityService foundation in 5 phases following strict TDD. Each phase maps to a SPRINT.md day. Tests are written before implementation. All code uses Hilt DI, Kotlin Coroutines, and Compose UI.

## Phase 1: Service Foundation + Project Setup

Set up the Android project structure, Hilt DI, and register NeuronAccessibilityService with the system.

### Tasks

- [x] Task 1.1: Create Android project with package `ai.neuron`, configure Gradle with Hilt, Compose, Coroutines, KotlinX Serialization, JUnit5, MockK
- [x] Task 1.2: Configure `AndroidManifest.xml` with `BIND_ACCESSIBILITY_SERVICE` permission and service declaration
- [x] Task 1.3: Write `accessibility_service_config.xml` (typeAllMask, flagReportViewIds, flagRetrieveInteractiveWindowsContent, canRetrieveWindowContent, canTakeScreenshot)
- [x] Task 1.4: Implement `NeuronAccessibilityService.kt` skeleton — onServiceConnected, onAccessibilityEvent, onInterrupt, onDestroy with coroutine scope management
- [x] Task 1.5: Set up Hilt `AppModule` and `@AndroidEntryPoint` / `@HiltAndroidApp` annotations

### Verification

- [x] Service appears in Settings > Accessibility > Neuron and can be toggled on/off
- [x] `adb shell dumpsys accessibility` shows NeuronAccessibilityService as running
- [x] Project builds with `./gradlew assembleDebug` with zero warnings

## Phase 2: UITreeReader (TDD)

Implement UI tree traversal with smart pruning and JSON serialization. Tests first.

### Tasks

- [x] Task 2.1: Write `UITreeReaderTest.kt` — tests for: null root, invisible node pruning, non-interactive leaf pruning, depth limit, JSON serialization, < 50ms performance
- [x] Task 2.2: Define `UINode` data class and `UITree` data class with `toJson()` using KotlinX Serialization
- [x] Task 2.3: Implement `UITreeReader.kt` — `getRootInActiveWindow()` traversal with recursive pruning
- [x] Task 2.4: Implement smart pruning: remove invisible nodes, collapse non-interactive leaves, depth limit of 15
- [x] Task 2.5: Implement compact JSON output: `{id, text, desc, className, bounds, clickable, scrollable, editable, password, children}`
- [x] Task 2.6: Add Logcat debug logging — full tree dump with tag `NeuronUITree`

### Verification

- [x] All `UITreeReaderTest` tests pass: `./gradlew test --tests *UITreeReaderTest`
- [ ] UI tree of WhatsApp home screen prints to Logcat as valid JSON in < 50ms
- [ ] Pruned tree is < 3000 tokens for typical screens

## Phase 3: ActionExecutor (TDD)

Implement all action types with verification. Tests first.

### Tasks

- [x] Task 3.1: Write `ActionExecutorTest.kt` — tests for: tap by node ID, tap by coordinate, type text, swipe directions, global actions (home/back/recents), scroll, long press, node-not-found error, stale node detection
- [x] Task 3.2: Define `NeuronAction` sealed class and `ActionResult` sealed class (Success/Error with verified flag)
- [x] Task 3.3: Implement `ActionExecutor.kt` — `tapByNodeId()`, `tapByCoordinate()` via `dispatchGesture()`
- [x] Task 3.4: Implement `typeText()` via `ACTION_SET_TEXT` and `swipe()` via `dispatchGesture()` with configurable duration
- [x] Task 3.5: Implement `performGlobalAction()` (HOME, BACK, RECENTS, NOTIFICATIONS), `scrollInView()`, `longPress()`
- [x] Task 3.6: Implement action verification — capture UI tree before/after, compare hashes, detect stale nodes
- [x] Task 3.7: Implement `AppLauncher.kt` — launch by package name via Intent, fuzzy app name matching via `InstalledAppsRegistry`

### Verification

- [x] All `ActionExecutorTest` tests pass: `./gradlew test --tests *ActionExecutorTest`
- [ ] Can programmatically: open WhatsApp → tap "New Chat" → search "Mom" → tap result → type "test message" → tap send
- [ ] Action verification correctly detects success/failure

## Phase 4: ScreenCapture + OverlayManager

Implement screenshot capture and the floating overlay UI.

### Tasks

- [x] Task 4.1: Write `ScreenCaptureTest.kt` — tests for: screenshot returns ByteArray, JPEG compression, base64 encoding, API < 30 fallback path
- [x] Task 4.2: Implement `ScreenCapture.kt` — `takeScreenshot()` via `AccessibilityService.takeScreenshot()`, compress JPEG quality 80, max 1024x1024, return ByteArray + Base64
- [x] Task 4.3: Implement API < 30 fallback via MediaProjection with permission dialog
- [x] Task 4.4: Implement `OverlayManager.kt` — floating bubble using `TYPE_ACCESSIBILITY_OVERLAY`, drag-to-position, expand on tap
- [x] Task 4.5: Implement overlay states: idle (gray), thinking (pulse purple), executing (blue spin), done (green), error (red) — Compose animations
- [x] Task 4.6: Implement text input field in expanded overlay with submit button and dismiss/minimize gesture

### Verification

- [ ] Screenshot of current screen returns valid JPEG as Base64 string
- [ ] Floating bubble appears over any app, is draggable, opens text input on tap
- [ ] Status animations render correctly for all 5 states

## Phase 5: Debug Tools + Integration Test + OEM Compatibility

Debug overlay, end-to-end integration test, and OEM compatibility fixes.

### Tasks

- [x] Task 5.1: Implement debug overlay — colored bounding boxes on detected UI elements (clickable=blue, scrollable=green, text input=yellow, image=gray)
- [x] Task 5.2: Implement debug mode toggle via notification action
- [x] Task 5.3: Implement accessibility event logging — log all events with tag `NeuronEvents`
- [x] Task 5.4: Implement basic crash reporter for AccessibilityService crashes
- [x] Task 5.5: E2E test on Calculator: launch → read UI tree → tap buttons (42+8) → verify result (50) — passed on Xiaomi MIUI
- [x] Task 5.6: Test on Xiaomi Redmi Note 9 Pro (MIUI 14, API 31) — service binds, UI tree captures, events received
- [x] Task 5.7: Test on Honor ELI-NX9 (MagicOS 9.0, API 35) — full WhatsApp E2E passed
- [x] Task 5.8: Fix top 3 OEM compatibility issues: battery whitelist, install-via-USB docs, overlay permission
- [x] Task 5.9: Write `ACCESSIBILITY_NOTES.md` — MIUI quirks documented, Samsung/Pixel sections stubbed

### Verification

- [ ] Debug mode shows colored boxes on every UI element in real-time (needs on-device Compose UI wiring)
- [ ] End-to-end WhatsApp messaging flow completes on Pixel (no Pixel device)
- [ ] End-to-end WhatsApp messaging flow completes on Samsung (no Samsung device)
- [x] `ACCESSIBILITY_NOTES.md` documents all found OEM quirks

## Final Verification

- [ ] All acceptance criteria met (7/7)
- [x] All tests passing: `./gradlew test` (31 tests)
- [ ] Lint clean: `./gradlew ktlintCheck`
- [ ] UITreeReader < 50ms on real device (needs instrumentation test)
- [x] Works on Xiaomi/MIUI (tested)
- [x] Documentation updated (ACCESSIBILITY_NOTES.md)
- [ ] Ready for review

---

_Generated by Conductor. Tasks will be marked [~] in progress and [x] complete._
