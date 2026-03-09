# Session Summaries

Auto-appended after each work session. Track what was accomplished, discoveries, blockers, and next steps.

Format: `### [YYYY-MM-DD] — [Session Title]`

---

### 2026-03-08 — Project Scaffolding & Memory System Setup
**Work done**:
- Set up repository structure with full directory tree
- Created CLAUDE.md, SPRINT.md, PRD.md, VISION.md
- Configured agent files and skill references
- Initialized MCP server skeleton
- Set up CI/CD workflows (GitHub Actions)
- Created auto-memory system with 14 foundational files

**Discoveries**:
- Memory system enables knowledge persistence across sessions
- Action schema provides Kotlin/Python contract
- Daily startup agent can automate tech radar updates

**Blockers**:
- None for scaffolding phase

**Next**:
- Begin Week 1 Sprint: "THE NERVE" (AccessibilityService implementation)
- Run daily startup script to begin tracking tech radar
- Start implementing NeuronAccessibilityService

---

### 2026-03-08 — Week 2 Brain: LLM Routing + Integration Testing (Phase 6)
**Work done**:
- Fixed 7 bugs during on-device integration testing (see `memory/knowledge/bugs.md`)
- Fixed Retrofit HttpException handling for both GeminiFlashClient and NvidiaQwenClient
- Fixed SensitivityGate false positive on Settings "Password" label (editable-only text matching)
- Fixed Gemini 2.5 Flash thinking model multi-part response parsing
- Added QUERY_ALL_PACKAGES permission for app launches on API 30+
- Added multi-fallback app launch chain (getLaunchIntentForPackage → resolveActivity → queryIntentActivities)
- Added Gemini model fallback chain (2.0-flash → 2.5-flash → 2.0-flash-lite) for rate limit resilience
- Added 800ms UI settle delay after action dispatch
- Added KNOWN_APPS map (16 apps) and PackageManager label lookup for app name resolution
- Improved system prompt with explicit LAUNCH vs TAP rules
- Updated ACCESSIBILITY_NOTES.md with 6 brain-related OEM quirks and performance profile
- 104 unit tests all passing

**Discoveries**:
- Gemini free tier: 20 requests/day per account (not per model) — blocks sustained testing
- NVIDIA NIM Qwen API: 404 since testing began — T3 fallback non-functional
- Gemini 2.5 Flash returns multi-part thinking responses — must filter thought parts
- Non-unique resource IDs in Settings dashboard break node targeting
- UI tree stale for ~800ms after app transitions

**Blockers**:
- Gemini API quota exhausted (20/day free tier) — need paid key for 10-task benchmark
- NVIDIA Qwen T3 endpoint down (404) — no T3 fallback available
- Tasks 6.3, 6.4, 6.5 blocked by API quota

**Next**:
- Get paid Gemini API key or wait for daily quota reset
- Re-run integration tests (Settings dark mode, Chrome weather)
- Run 10-task benchmark (Task 6.5)
- Investigate NVIDIA NIM endpoint status
- Consider alternative T3 provider (OpenRouter, Together AI)

---

### 2026-03-08 — Phase 6 Completion: OpenRouter Integration + 5-Task Benchmark
**Work done**:
- Added OpenRouter as T3 fallback provider (Llama 3.3 70B, free, ~3s latency)
- Added OpenRouterClient.kt with model fallback chain (Llama 3.3 → Gemma 3 27B → Mistral Small)
- Updated LLMClientManager: T3 → NVIDIA first, then OpenRouter on failure
- Improved resolvePackageName: verify launchability, fuzzy label matching fallback
- Increased T3 latency budget from 10s → 30s for fallback chain
- Added Accept header to NVIDIA client
- Suppressed QUERY_ALL_PACKAGES lint error (intentional)
- Ran 5-task benchmark: 2/5 PASS, 3/5 PARTIAL (action pipeline 100% working)
- First full end-to-end success: "open the calculator app" — LAUNCH + DONE

**Discoveries**:
- NVIDIA NIM 404 is a known server-side issue (forums confirm widespread outages)
- OpenRouter Llama 3.3 70B: valid action JSON, but can't track UI state changes as well as Gemini
- Gemini free tier: ALL 4 models share same daily quota — no per-model separation
- Package name resolution critical: LLMs return generic AOSP names, OEM devices use custom packages
- T3 fallback chain total latency: ~5s (Gemini 429s ~2s + NVIDIA 404 ~0.2s + OpenRouter ~3s)

**Blockers**:
- Gemini daily quota exhausted — need paid key for 70%+ benchmark target
- NVIDIA NIM down — widespread issue, not our bug

**Next**:
- Re-run benchmark with Gemini when quota resets (expect 70%+ success rate)
- Add rate-limit-aware routing to skip Gemini entirely when quota exhausted
- Consider adding "press enter/submit" hint to prompt for search bar scenarios
- Week 3 planning (Ship: memory, voice, polish)

---

### 2026-03-08 — Ollama Cloud Integration + Routing Chain Upgrade
**Work done**:
- Created OllamaCloudClient.kt — Qwen3-VL 235B thinking model via `https://ollama.com/v1/chat/completions`
- Wired into LLMClientManager: T2 Gemini→Ollama fallback, T3 Ollama→NVIDIA→OpenRouter
- Updated T2 budget 15s→20s to accommodate Gemini 429 overhead + Ollama ~15s
- Integration tested: "open calculator" — full LAUNCH→DONE cycle via Ollama Cloud
- Integration tested: "open Chrome search weather" — Chrome launched, TAP step failed (node ID hallucination)
- All 104 unit tests passing

**Discoveries**:
- Ollama Cloud Qwen3-VL 235B: ~14-15s latency, good action selection, can hallucinate node IDs
- Thinking model returns reasoning separately — response parsing handles it correctly
- T2 20s budget tight: Gemini 429 chain ~4s + Ollama ~15s = 19s (barely fits)
- Node ID hallucination is cross-model issue (Llama 3.3 and Qwen3-VL both do it)

**Next**:
- Re-test with Gemini when quota resets (expect much faster — 1-3s latency)
- Consider node ID enumeration in system prompt to reduce hallucination
- Week 3 planning

---

### 2026-03-09 — Week 3 Ship: Phases 0-6 Implementation (45/48 tasks)
**Work done**:
- **Phase 0 (Integration Fixes)**: Intent-based app resolution, NAVIGATE enter/submit, NAVIGATE notifications, PressKey action
- **Phase 1 (Long-Term Memory)**: Room DB with 3 entities (UserPreference, AppWorkflow, ContactAssociation), LongTermMemory repository, MemoryExtractor auto-capture, workflow hints in LLM prompts
- **Phase 2 (MCP Server)**: 14 pytest tests for existing MCP server, setup guide
- **Phase 3 (SDK)**: NeuronSDK, ToolRegistry, AppFunctionsBridge stub, tool_call action type, ToolRegistry injected into LLM system prompt, 12 SDK tests
- **Phase 4 (Voice + Overlay)**: SpeechRecognitionManager state machine, VoiceInputController hold-to-speak, ConfirmationGate for irreversible actions, LISTENING overlay state, 28 new tests
- **Phase 5 (UI Polish)**: OnboardingScreen 4-step wizard, SettingsScreen with API keys + privacy, AuditLogScreen, MainScreen hub, ProGuard rules
- **Phase 6 (Release Prep)**: Signing config, install guide, README SDK example update
- Total: 153 unit tests passing, 10 commits

**Key files created**:
- `android/.../AppResolver.kt`, `ActionMapper.kt`, `ConfirmationGate.kt`
- `android/.../memory/{NeuronDatabase,LongTermMemory,MemoryExtractor}.kt` + entities + DAOs
- `android/.../input/{SpeechRecognitionManager,VoiceInputController}.kt`
- `android/.../sdk/{NeuronSDK,ToolRegistry,NeuronTool,AppFunctionsBridge}.kt`
- `android/.../ui/{MainScreen,onboarding/OnboardingScreen,settings/SettingsScreen,audit/AuditLogScreen}.kt`
- `docs/onboarding/{install.md,sdk_quickstart.md}`, `docs/api/mcp_guide.md`

**Remaining (3 tasks, all require physical device)**:
- Task 0.5: Re-run 16-test benchmark on device
- Task 6.1/6.2: Final benchmark + bug fixes
- Task 6.4: GitHub Release upload
- Task 6.8: 1-hour soak test

---
