# Coding Patterns

Reusable patterns discovered and confirmed across multiple scenarios in the Neuron codebase.

When a pattern is used 2+ times, it should be documented here with context and examples.

Format: `### [Pattern Name]`

---

### Retrofit ResponseBody + Manual JSON Deserialization
When Retrofit doesn't have a converter factory for KotlinX Serialization, use `okhttp3.ResponseBody` as the API return type and deserialize manually with `json.decodeFromString(Serializer, rawBody.string())`. Always catch `retrofit2.HttpException` for non-2xx responses.
Used in: `GeminiFlashClient.kt`, `NvidiaQwenClient.kt`

---

### Dual-Path LLM Response Parsing
LLMs may return either a wrapped response (`LLMResponse` with nested `action`) or a bare action JSON (`LLMAction`). Parse both:
1. Try `LLMResponse.fromJson(text)` — if `action != null`, return it
2. Try `json.decodeFromString(LLMAction.serializer(), text)` — wrap in `LLMResponse(action = action)`
3. Return null if neither works

Also strip markdown code fences (```` ```json ... ``` ````) before parsing.
Used in: `GeminiFlashClient.parseAsLLMResponse()`, `NvidiaQwenClient.parseAsLLMResponse()`

---

### Gemini Model Fallback Chain on Rate Limit
When using Gemini free tier (20 req/day per model), try multiple models in sequence on HTTP 429:
```
gemini-2.0-flash → gemini-2.5-flash → gemini-2.0-flash-lite
```
Check `result.message.contains("HTTP 429")` to detect rate limiting and continue to next model.
Used in: `GeminiFlashClient.generate()`

---

### App Name Resolution Chain
LLMs may return app names as human-readable names ("Chrome", "Settings") or package names ("com.android.chrome"). Resolution chain:
1. If contains `.` → treat as package name directly
2. Check `KNOWN_APPS` map (16 common apps) for case-insensitive match
3. Query `PackageManager.getInstalledApplications()` and match by label
Used in: `BrainModule.resolvePackageName()`

---

### Multi-Fallback App Launch (Android 11+)
`getLaunchIntentForPackage` may return null on API 30+. Try in order:
1. `packageManager.getLaunchIntentForPackage(pkg)`
2. `Intent(ACTION_MAIN).setPackage(pkg)` → `resolveActivity()`
3. `Intent(ACTION_MAIN, CATEGORY_LAUNCHER)` → `queryIntentActivities()` → find matching package
Always add `FLAG_ACTIVITY_NEW_TASK`.
Used in: `ActionExecutor.kt`

---

### SensitivityGate: Editable-Only Text Matching
Only flag sensitive text patterns ("password", "PIN", "CVV", "OTP") in `editable=true` nodes. Non-editable labels like "Change Password" menu items should NOT trigger sensitivity. Password input fields (`password=true`) always trigger regardless of text content.
Used in: `SensitivityGate.kt`

---
