# Feature Implementation Notes

Auto-updated after each feature completion. Track approaches, key decisions, files changed, and gotchas.

Format: `### [YYYY-MM-DD] — [Feature Name]`

---

### 2026-03-08 — OpenRouter T3 Fallback Provider
**Approach**: Added OpenRouter (OpenAI-compatible API) as fallback when both Gemini (429) and NVIDIA (404) fail.
**Key decisions**: Reused NvidiaRequest/NvidiaResponse serialization models (same OpenAI format). 3-model chain: Llama 3.3 70B → Gemma 3 27B → Mistral Small. LLMClientManager tries NVIDIA first, OpenRouter on any failure.
**Files changed**: `OpenRouterClient.kt` (new), `LLMClientManager.kt`, `BrainModule.kt`, `build.gradle.kts`, `LLMTier.kt` (T3 budget 10s→30s)
**Gotchas**: T3 timeout must account for full fallback chain latency (Gemini 429s + NVIDIA 404 + OpenRouter ~3-10s). OpenRouter response time varies with UI tree size.

---

### 2026-03-08 — Ollama Cloud T2/T3 Fallback Provider (Qwen3-VL 235B)
**Approach**: Added Ollama Cloud (`https://ollama.com/v1/chat/completions`) with Qwen3-VL 235B thinking model. T2: Gemini → Ollama Cloud fallback. T3: Ollama Cloud → NVIDIA → OpenRouter.
**Key decisions**: Reused NvidiaRequest/NvidiaResponse (OpenAI-compatible). 235B thinking model produces higher quality reasoning but ~14-15s latency. T2 budget increased 15s→20s to fit fallback chain.
**Files changed**: `OllamaCloudClient.kt` (new), `LLMClientManager.kt` (T2+T3 routing), `BrainModule.kt` (DI), `build.gradle.kts` (BuildConfig), `LLMTier.kt` (T2 20s)
**Gotchas**: Thinking model returns reasoning in separate field (stripped by API). 14-15s latency tight for T2 20s budget with Gemini 429 overhead (~4s). Node ID hallucination still an issue.

---

### 2026-03-08 — Fuzzy Package Name Resolution
**Approach**: Instead of trusting LLM-provided package names, verify launchability via `getLaunchIntentForPackage()` first. If not launchable, fall back to installed app label matching.
**Key decisions**: Partial match as last resort (label.contains or name.contains). KNOWN_APPS map still takes priority for common apps.
**Files changed**: `BrainModule.kt` (resolvePackageName method)
**Gotchas**: LLMs return generic AOSP package names (com.android.calculator2) that don't exist on OEM devices (Honor uses com.hihonor.calculator). Must always verify against actual device.

---

### 2026-03-09 — Intent-Based App Resolution
**Approach**: 4-step chain: KNOWN_APPS → package validation → fuzzy label → intent-based. Intent fallback maps keywords (call, photo, alarm, browser) to standard Android actions (ACTION_DIAL, ACTION_IMAGE_CAPTURE).
**Files**: `AppResolver.kt` (new, extracted from BrainModule), `AppResolverTest.kt` (25 tests)
**Gotchas**: Android Intent stubs don't store action/data in JVM tests — test keyword matching separately from PM resolution.

---

### 2026-03-09 — Room DB Long-Term Memory
**Approach**: 3 entities (UserPreference, AppWorkflow, ContactAssociation) with Room DAOs. LongTermMemory repository with upsert logic. MemoryExtractor auto-captures preferences after LAUNCH actions and serializes action sequences.
**Files**: `memory/{NeuronDatabase,LongTermMemory,MemoryExtractor}.kt`, `entity/*.kt`, `dao/*.kt`, `di/MemoryModule.kt`
**Gotchas**: Room needs KSP `room.schemaLocation` arg. Use `fallbackToDestructiveMigration()` for alpha phase.

---

### 2026-03-09 — SDK Tool Registry + LLM Prompt Injection
**Approach**: NeuronTool data class + ToolRegistry in-memory map. ToolRegistry.toPromptSnippet() appended to LLM system prompt. New TOOL_CALL action type for LLM to invoke tools. ActionDispatcher intercepts tool_call before AccessibilityService.
**Files**: `sdk/{NeuronTool,ToolRegistry,NeuronSDK,AppFunctionsBridge}.kt`, `LLMRouter.kt`, `BrainModule.kt`, `LLMResponse.kt`
**Gotchas**: TOOL_CALL must be added to every exhaustive `when` on ActionType (ActionMapper, BrainModule mapToNeuronAction).

---

### 2026-03-09 — Voice Input + Confirmation Gate
**Approach**: SpeechRecognitionManager wraps Android SpeechRecognizer with state machine (IDLE→LISTENING→PROCESSING→IDLE/ERROR). VoiceInputController bridges speech→overlay for hold-to-speak. ConfirmationGate checks dangerous keywords + low confidence + sensitive flag.
**Files**: `input/{SpeechRecognitionManager,VoiceInputController}.kt`, `ConfirmationGate.kt`, `OverlayManager.kt` (LISTENING state added)
**Gotchas**: SpeechRecognizer must be created and used on main thread. RmsDb exposed via StateFlow for waveform visualization.

---

### 2026-03-09 — Onboarding + Settings UI
**Approach**: Compose-only screens. OnboardingScreen: 4-step wizard with permissions explanation card. SettingsScreen: API key fields (PasswordVisualTransformation), cloud toggle, memory clear dialog. AuditLogScreen: LazyColumn of timestamped entries.
**Files**: `ui/{MainScreen,onboarding/OnboardingScreen,settings/SettingsScreen,audit/AuditLogScreen}.kt`, `MainActivity.kt` (rewired with navigation)
**Gotchas**: SharedPreferences for settings persistence (simple, no DataStore needed for alpha). Screen navigation via simple string state, not NavHost (minimal for now).

---

### 2026-03-11 — L7 CHARACTER: Character Engine + Emotional Voice (Month 1 SOUL)
**Approach**: 7-phase TDD build of the L7 CHARACTER layer. Data models → CharacterEngine → Rendering → Emotional TTS → Personality prompts → UI → Integration. All phases follow Red-Green-Refactor discipline.
**Key decisions**:
- `CharacterEngine` accepts `CoroutineScope` (not Dispatcher) for testability — tests pass `this` from `runTest`
- Emotion transitions: 100ms debounce + 30s auto-decay to NEUTRAL + history capped at 10
- `CharacterRenderer` interface abstracts Live2D (deferred, needs Cubism SDK license) vs `ComposeCharacterRenderer` (Compose Canvas with blob + pixel characters)
- `CharacterRendererFactory` detects Live2D at runtime via `Class.forName`, falls back to Compose
- SSML prosody via `SsmlBuilder` with per-emotion pitch/rate/volume shifts, `<break>` for THINKING
- `LLMRouter.route()` got optional `characterContext: String? = null` — backward compatible
- `ResponseEmotionExtractor` uses keyword heuristic with priority: CONCERNED > THINKING > EXCITED > HAPPY > NEUTRAL
- `PersonalityPromptBuilder` generates full system prompt from traits + emotion + interaction count
- `CharacterSystemPrompt.buildCompact()` for T4 models (<200 chars)
**Files created** (30+): `character/model/{EmotionState,PersonalityProfile,SpeakingStyle,CharacterType,VoiceProfile}.kt`, `character/{CharacterEngine,di/CharacterModule}.kt`, `character/renderer/{CharacterRenderer,ComposeCharacterRenderer,CharacterRendererFactory,CharacterRenderView,EmotionVisuals}.kt`, `character/voice/{SsmlBuilder,VoiceSynthesizer,EmotionalTTSEngine}.kt`, `character/prompt/{PersonalityPromptBuilder,CharacterSystemPrompt,ResponseEmotionExtractor}.kt`, `character/ui/{CharacterViewModel,CharacterGalleryScreen,CharacterCustomizationScreen,CharacterSettingsSection,CharacterUiState}.kt`, `memory/entity/CharacterState.kt`, `memory/dao/CharacterDao.kt`
**Files modified**: `LLMRouter.kt` (characterContext param), `proguard-rules.pro` (character keep rules), `NeuronDatabase.kt` (v1→v2 migration)
**Tests**: 165 new tests across 7 test files (33+19+12+18+18+9+11 = 120 character + 45 integration). Total: 318 tests, 0 failures
**Gotchas**:
- Float precision in `TtsEmotion.toSsmlProsody()`: `0.85f - 1.0f` gives `-14.999998`, not `-15`. Fix: `kotlin.math.round()`
- `MutableList.removeFirst()` is Java 21 — use `removeAt(0)` for Java 17 compat
- `advanceUntilIdle()` in coroutine tests triggers the 30s decay job — use `advanceTimeBy(200)` instead
- ViewModel emotion state must be a pass-through `StateFlow` from engine, not a snapshot copy

---

### 2026-03-12 — C1: HITL ExecutionMode (Play Store Compliance)
**Approach**: Added `ExecutionMode` enum (AUTONOMOUS/SUPERVISED/PLAN_APPROVE) to satisfy Google Play's Jan 28, 2026 AccessibilityService policy banning autonomous AI actions. `ConfirmationCallback` interface lets overlay UI suspend the engine until user approves/rejects each step.
**Key decisions**: Build flavors (`sideload`/`playstore`) with different default modes via BuildConfig. SUPERVISED shows step-by-step confirmation with amber "?" bubble. PLAN_APPROVE shows full plan for single approval. DONE/ERROR actions bypass confirmation (safe).
**Files created**: `ExecutionMode.kt`
**Files modified**: `PlanAndExecuteEngine.kt` (ConfirmationCallback, mode check before dispatch), `EngineState.kt` (+ConfirmingAction, +AwaitingPlanApproval), `OverlayManager.kt` (CONFIRMING state, Yes/No buttons), `SettingsScreen.kt` (ExecutionModeSelector), `MainActivity.kt` (execution_mode prefs), `build.gradle.kts` (sideload/playstore flavors), `NeuronAccessibilityService.kt` (+when branches), `NeuronBrainService.kt` (+when branches)
**Tests**: 8 (ExecutionModeTest.kt)
**Gotchas**: Adding states to `EngineState` sealed class breaks all exhaustive `when` blocks — must update every consumer.

---

### 2026-03-12 — A3: RLM UITree Tool Architecture
**Approach**: MIT RLM-inspired tool-callable UITree access replacing monolithic JSON dumps. LLM receives tool definitions (~200 tokens) instead of full tree (2000+ tokens). On-demand querying during planning.
**Key decisions**: `UINodeSummary` data class (compact representation), 5 query methods: `getClickableNodes()`, `getNodeByText()`, `getNodeChildren()`, `searchNodes()`, `getScreenSummary()`. `toPromptSnippet()` generates compact tool description for system prompts. `flattenTree()` recursive traversal.
**Files created**: `UITreeTools.kt`
**Tests**: 20 (UITreeToolsTest.kt)
**Gotchas**: Test nodes need both clickable and non-clickable entries to verify filtering. `getScreenSummary()` returns top-level summary + clickable node list — ideal for FunctionGemma context.

---

### 2026-03-12 — A1: FunctionGemma T1 On-Device Function Calling
**Approach**: Replaced regex pattern matching at T1 with Google's FunctionGemma (270M params) for structured function calling. `InferenceEngine` interface abstracts LiteRT-LM runtime (allows mock testing). `NeuronToolSchema` defines 7 tools (tap/type/launch/navigate/swipe/done/error) as structured definitions.
**Key decisions**: FunctionGemma outputs `function_name(param="value")` format — parsed by regex. Baseline confidence 0.85. Tier "T1", modelId "function-gemma". `NeuronToolSchema.toActionType()` is case-insensitive. `toPromptSnippet()` compact format for system prompts. `toJson()` for Gemini functionDeclarations compatibility.
**Files created**: `FunctionGemmaClient.kt`, `NeuronToolSchema.kt`
**Tests**: 25 (FunctionGemmaClientTest.kt: 15, NeuronToolSchemaTest.kt: 10)
**Gotchas**: `parseNamedArgs()` must handle both single and double quotes. `parseToolCall()` uses `DOT_MATCHES_ALL` regex option for multiline robustness.

---

### 2026-03-12 — A4+C2: AppFunctions Dual-Path Execution
**Approach**: `DualPathExecutor` as central execution gateway. Routes through AppFunctions (preferred, policy-compliant) then falls back to Accessibility. `AppFunctionsExecutor` is a capability-checking stub (ready for `androidx.appfunctions` stable release). `AccessibilityExecutorAdapter` bridges LLMAction → NeuronAction → ActionExecutor.
**Key decisions**: `ExecutionResult` tracks which `ExecutionPath` was used (APP_FUNCTIONS/ACCESSIBILITY/PATTERN_MATCH) for analytics and migration tracking. `registeredApps` map populated by future `AppFunctionsBridge.discoverAndRegister()`. All three classes are `@Singleton` with `@Inject` constructor for Hilt.
**Files created**: `DualPathExecutor.kt` (contains DualPathExecutor, AppFunctionsExecutor, AccessibilityExecutorAdapter)
**Tests**: 8 (DualPathExecutorTest.kt)
**Gotchas**: `ActionResult` is in `ai.neuron.accessibility.model` package, not nested in `ActionExecutor`. AppFunctions stub always returns failure — tests verify fallback to accessibility.

---

### 2026-03-24 — AccessibilityService + Nerve Layer Stabilization Review

**Component Status Assessment:**

| Component | Status | Issues Found |
|-----------|--------|-------------|
| NeuronAccessibilityService | Functional | No reconnection logic; manual dependency construction (no @AndroidEntryPoint) |
| OverlayManager | Solid | OverlayLifecycleOwner not cleaned up on hide() (lifecycle events not dispatched) |
| UITreeReader | Good | Correct node recycling, proper pruning logic |
| UITreeTools | Good | Clean RLM-inspired tool-based tree access |
| ActionExecutor | Had bugs | Node leak in findNodeById; double-recycle risk in executeTap; no performAction return check |
| ScreenCapture | Had crash | bitmap.width/height accessed after bitmap.recycle() |
| DualPathExecutor (Nerve) | Was dead code | AccessibilityExecutorAdapter had null actionExecutor, BrainModule bypassed it entirely |

**Fixes Applied (3 critical):**
1. `ScreenCapture.kt`: Save bitmap dimensions before recycle — prevented guaranteed `IllegalStateException` crash
2. `ActionExecutor.kt`: Recycle non-returned nodes from `findAccessibilityNodeInfosByViewId`; fix double-recycle in `executeTap`; check `performAction` return value
3. `DualPathExecutor.kt` + `BrainModule.kt`: Rewired `ActionDispatcher` to route through `DualPathExecutor` (AppFunctions → Accessibility fallback). `AccessibilityExecutorAdapter` now creates `ActionExecutor` on demand from live service instance.

**Remaining Issues (not fixed this round):**
- OverlayManager.hide() should dispatch ON_PAUSE/ON_STOP/ON_DESTROY lifecycle events
- NeuronAccessibilityService lacks reconnection logic when system kills and restarts
- NeuronBrainService exported=true in manifest without intent-filter protection
- NeuronAccessibilityService doesn't use @AndroidEntryPoint (Hilt injection)

---

### 2026-03-14 — v2 Phase 2: Developer Foundation (5 Components)
**Approach**: Implemented 5 components in dependency order: A5 Structured Tool Calling → C3 Plugin Architecture → B1 SKILL.md System → B5 AppFunctions Provider SDK → A2 Gemma 3n T4.
**Key decisions**:
- `StructuredToolCallParser` unified parser handles Gemini functionCall, OpenAI tool_calls, FunctionGemma func(), and legacy JSON formats
- `NeuronToolSchema` extended with `toGeminiToolsJson()` and `toOpenAIToolsJson()` for provider-native formats
- GeminiFlashClient and OllamaCloudClient updated to use StructuredToolCallParser before legacy parsing
- `NeuronPlugin` interface with `PluginManager` lifecycle (register/load/unload/unregister), error isolation
- `CharacterPlugin` wraps existing CharacterEngine, registers set_emotion and get_character_info tools
- `SkillManifest` parses SKILL.md YAML frontmatter, `SkillValidator` checks permissions/schema, `SkillLoader` registers tools with ToolRegistry
- `@NeuronCapability` annotation + `CapabilityScanner` reflection-based discovery for AppFunctions providers
- `Gemma3nClient` with multimodal InferenceEngine (text + image), T1.5 and T4 tiers
- LLMRouter upgraded: FunctionGemma T1 → Gemma 3n T1.5 → cloud T2 fallback chain; T4 uses Gemma 3n
**Files created**: `StructuredToolCallParser.kt`, `NeuronPlugin.kt`, `NeuronContext.kt`, `PluginManager.kt`, `CharacterPlugin.kt`, `SkillManifest.kt`, `SkillValidator.kt`, `SkillLoader.kt`, `NeuronCapability.kt`, `NeuronAppFunctionsProvider.kt`, `CapabilityScanner.kt`, `Gemma3nClient.kt`
**Files modified**: `NeuronToolSchema.kt` (+toGeminiToolsJson, +toOpenAIToolsJson), `GeminiFlashClient.kt` (+StructuredToolCallParser), `OllamaCloudClient.kt` (+StructuredToolCallParser), `BrainModule.kt` (DI wiring), `LLMRouter.kt` (+T1.5, +T4 Gemma 3n)
**Tests**: 108 new tests. Total: 487 tests, 0 failures
**Gotchas**: PluginManager.loadAll() must check loadPlugin() return value before adding to loaded list. ToolRegistry.register() throws on duplicate — catch in SkillLoader. SKILL.md YAML parser is hand-rolled (no external YAML lib dependency).

---
