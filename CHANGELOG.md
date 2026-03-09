# Changelog

All notable changes to the Neuron project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-alpha] - 2026-03-10

### Added
- **AccessibilityService core** — NeuronAccessibilityService with UI tree reading, action execution, and overlay
- **UITreeReader** — smart pruning, depth limits, JSON serialization of Android UI trees
- **ActionExecutor** — tap, type, swipe, scroll, launch, navigate actions via AccessibilityService
- **SensitivityGate** — detects banking/password/health screens, forces on-device processing (T4)
- **LLM Router** — tiered routing (T0-T4) with Gemini Flash, Ollama, OpenRouter providers
- **IntentClassifier** — command complexity classification (SIMPLE/MODERATE/COMPLEX)
- **PlanAndExecuteEngine** — multi-step task execution with verification and replanning
- **WorkingMemory** — in-memory task state with SharedPreferences persistence
- **Room DB long-term memory** — UserPreference, AppWorkflow, ContactAssociation entities with DAOs
- **LongTermMemory** — unified repository, auto-extracts preferences, injects workflow hints into LLM
- **MCP Server** — Python server exposing 7 tools (screenshot, UI tree, tap, type, swipe, launch, run_task)
- **SDK ToolRegistry** — register custom tools that LLM planner can invoke
- **AppFunctionsBridge** — queries installed app capabilities via AppFunctionsManager
- **Voice input pipeline** — SpeechRecognitionManager + VoiceInputController with hold-to-speak
- **Wake word detection** — Porcupine SDK integration with configurable keyword (default: JARVIS)
- **Enhanced overlay UI** — animated floating bubble with states (IDLE/LISTENING/THINKING/EXECUTING/DONE/ERROR)
- **ConfirmationGate** — blocks dangerous actions (send/delete/pay) without user confirmation
- **Onboarding wizard** — 4-step flow (welcome, permissions, test command, done)
- **Settings screen** — API keys, wake word config, privacy toggle, memory management, audit log
- **AppResolver** — fuzzy package matching with OEM fallback + intent-based resolution
- **Benchmark automation script** — `scripts/run_benchmark.sh` for 16-test integration validation
- **ProGuard/R8 optimization** — minify + shrink for release builds
- **Release signing config** — env-based keystore for CI/CD
- **153 unit tests** — JUnit5 (Kotlin) + pytest (Python), 100% pass rate
- **Documentation** — install guide, MCP guide, SDK quickstart, architecture docs

### Fixed
- "open YouTube" / "open X" commands now work via pattern matching (was routing to unimplemented T1)
- SpeechRecognizer no longer destroyed before async callbacks fire
- T4 sensitive context never falls through to cloud LLM
- OEM package resolution (Honor dialer/contacts/camera) via intent-based fallback
- Chrome search now submits via Enter key after typing
- Notification shade opens via NAVIGATE action instead of swipe
