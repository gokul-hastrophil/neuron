# Bug Fixes & Root Causes

Auto-updated after each significant bug fix. Track symptoms, root causes, fixes, and prevention strategies.

Format: `### [YYYY-MM-DD] — [Bug Title]`

---

### 2026-03-08 — Retrofit HttpException on non-2xx responses with ResponseBody return type
Symptom: `HttpException: HTTP 429` thrown even though API was supposed to return raw body.
Root cause: Retrofit throws `HttpException` for non-2xx status codes even with `ResponseBody` return type. We assumed it would return the error body.
Fix: Wrap API call in `try { ... } catch (e: retrofit2.HttpException)` and extract error body via `e.response()?.errorBody()?.string()`.
Prevention: Always catch `HttpException` when using raw `ResponseBody` return type with Retrofit.

---

### 2026-03-08 — SensitivityGate false positive on Settings "Password" label
Symptom: Settings app forced to T4 (on-device only), no LLM cloud calls made.
Root cause: `SensitivityGate.hasAnySensitiveNode()` checked ALL text nodes for sensitive patterns ("Password", "PIN", etc.). Settings has a "Password" menu item label (non-editable).
Fix: Changed to only flag text patterns in `editable=true` fields. Password input fields (`password=true`) still flagged regardless.
Prevention: Sensitive text detection should only apply to input fields, not read-only labels. Test with real Settings UI trees.

---

### 2026-03-08 — Gemini 2.5 Flash thinking model multi-part response parsing failure
Symptom: `parseAsLLMResponse` returns null on valid Gemini responses. Fallback LLMResponse has null action.
Root cause: Gemini 2.5 Flash is a "thinking" model that returns multiple `parts`: `[{thought:true, text:"reasoning..."}, {text:'{"action_type":"launch"...}'}]`. Code took `parts.firstOrNull()?.text` which was the thinking text, not valid JSON.
Fix: Added `thought` boolean to `GeminiPart`, filter to `parts.lastOrNull { it.thought != true }`.
Prevention: When using thinking models (Gemini 2.5, Qwen with enable_thinking), always handle multi-part responses.

---

### 2026-03-08 — getLaunchIntentForPackage returns null on Android 11+ (API 30+)
Symptom: "No launch intent for package com.android.chrome" — all app launches fail.
Root cause: Android 11 package visibility restrictions. Without `QUERY_ALL_PACKAGES`, `getLaunchIntentForPackage()`, `resolveActivity()`, and `queryIntentActivities()` all return null.
Fix: Added `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` to AndroidManifest.xml.
Prevention: Always include QUERY_ALL_PACKAGES for agent apps that need to launch arbitrary apps. Note: Play Store may reject this — use `<queries>` blocks for production.

---

### 2026-03-08 — ADB broadcast targeting wrong package
Symptom: `am broadcast --es command 'open Chrome...'` delivers to Chrome package, not Neuron.
Root cause: Shell argument parsing interprets "Chrome" in the `--es` value as a package name. The `-p` flag wasn't being used.
Fix: Always use `-p ai.neuron` flag and wrap in double quotes: `adb shell "am broadcast -a ai.neuron.ACTION_TEXT_COMMAND -p ai.neuron --es command 'text here'"`.
Prevention: Always explicitly set target package with `-p` for ADB broadcasts.

---

### 2026-03-08 — UI tree stale after action dispatch
Symptom: After launching an app, next UI tree read still shows previous screen.
Root cause: UI transitions take time; AccessibilityService returns cached/stale tree if read too quickly.
Fix: Added 800ms `UI_SETTLE_DELAY_MS` delay after action dispatch before re-reading the UI tree.
Prevention: Always wait for UI to settle after dispatching actions, especially launches and navigation.

---

### 2026-03-08 — T3 timeout too short for OpenRouter fallback chain
Symptom: `LLM call timed out after 10000ms for tier T3` when using OpenRouter as fallback.
Root cause: T3 latency budget (10s) too tight when fallback chain burns ~2s on failed Gemini(429)+NVIDIA(404) before reaching OpenRouter (~3-10s depending on UI tree size).
Fix: Increased T3 `latencyBudgetMs` from 10,000 to 30,000ms in `LLMTier.kt`.
Prevention: Account for full fallback chain latency when setting tier budgets.

---

### 2026-03-08 — LLM returns wrong package name, launch fails
Symptom: LLM returns `com.android.calculator2` but Honor device has `com.hihonor.calculator`. Launch intent is null.
Root cause: `resolvePackageName()` accepted any string containing a dot as a valid package name without verifying launchability.
Fix: Added `getLaunchIntentForPackage()` check before accepting dot-containing values; falls back to label-based fuzzy matching if not launchable.
Prevention: Never trust LLM-provided package names — always verify against PackageManager.

---

### 2026-03-10 — Pattern matching bypassed for simple commands classified as T2/T3
Symptom: "show recent apps", "pull down notification shade", "go back" timeout (60s) instead of completing instantly.
Root cause: `IntentClassifier.SIMPLE_PATTERNS` didn't match multi-word variants. Classified as MODERATE → T2 → cloud. `matchCommandPattern()` only ran inside `handleOnDevice()` (T0/T1), never reached for T2/T3.
Fix: Moved pattern matching to top of `LLMRouter.route()`, before tier routing. Simple commands always pattern-match regardless of tier.
Prevention: Pattern matching must be the first check in the routing pipeline, independent of tier classification.

---

### 2026-03-10 — Fuzzy label match returns non-launchable packages (com.android.incallui)
Symptom: "open the phone dialer" fails with "No launch intent for package 'com.android.incallui'".
Root cause: AppResolver fuzzy match found `com.android.incallui` (label "Phone") but it has no MAIN/LAUNCHER activity — it's the in-call UI, not the dialer.
Fix: Added `getLaunchIntentForPackage()` check in fuzzy match loops. Non-launchable packages are skipped, falling through to intent-based resolution (ACTION_DIAL → actual dialer).
Prevention: Always verify launchability at every resolution stage, not just for KNOWN_APPS.

---

### 2026-03-10 — Pattern match greedily captures multi-step commands
Symptom: "open WhatsApp and show me my chats" → LAUNCH("whatsapp and show me my chats") → AppResolver can't resolve.
Root cause: Regex `^(?:open|launch|start)\s+(.+)$` captures everything after "open", including "and show me my chats".
Fix: Skip pattern matching for commands containing " and " — let cloud LLM handle multi-step commands.
Prevention: Pattern matching is for single-step deterministic commands only. Multi-step detection via "and" conjunction.

---
