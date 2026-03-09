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
