# Skill: LLM Routing

## Overview

The LLM routing system is the decision engine of Neuron's L3 BRAIN layer. It selects the appropriate
model tier for each user request based on complexity, sensitivity, and latency requirements. The router
enforces privacy-first principles: sensitive data never leaves the device.

---

## Tier Definitions

| Tier | Model | Location | Latency Budget | Use Case | Cost/1K Tokens |
|------|-------|----------|----------------|----------|----------------|
| T0 | Porcupine | On-device | <10ms | Wake word detection only | $0 (one-time license) |
| T1 | Gemma 3n | On-device | <500ms | Single-step tasks, screen classification | $0 (on-device) |
| T2 | Gemini 2.5 Flash | Cloud | <2000ms | Multi-step execution, action selection | ~$0.0001 |
| T3 | Claude Sonnet 4.5 / Gemini 3 Pro | Cloud | <5000ms | Complex planning, replanning | ~$0.003 |
| T4 | Gemma 3n ONLY | On-device | <500ms | Sensitive data (passwords, banking, health) | $0 (on-device) |

### Tier Selection Criteria

```
T0: Wake word detected → no LLM needed, trigger listening pipeline
T1: Intent is single-action AND no complex reasoning needed
    Examples: "go home", "turn on wifi", "scroll down", "what app is this?"
T2: Intent requires 2-5 step plan OR needs to interpret screen content
    Examples: "send hi to Mom on WhatsApp", "search for pasta recipes"
T3: Intent requires multi-app coordination OR error recovery OR open-ended planning
    Examples: "book me an Uber to the airport", "find the cheapest flight to NYC and share it"
T4: ANY sensitive context detected → forced on-device regardless of complexity
    Examples: anything on a banking screen, password entry, OTP fields
```

---

## Router Implementation (Kotlin)

### Core Router

```kotlin
package ai.neuron.brain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay

enum class Tier { T0, T1, T2, T3, T4 }

enum class Complexity { TRIVIAL, SIMPLE, COMPLEX }

data class UserInput(
    val text: String,
    val isVoice: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

data class UITreeNode(
    val id: String?,
    val text: String?,
    val className: String?,
    val isPassword: Boolean,
    val isClickable: Boolean,
    val packageName: String?,
    val children: List<UITreeNode> = emptyList(),
)

@Singleton
class LLMRouter @Inject constructor(
    private val sensitivityGate: SensitivityGate,
    private val intentClassifier: IntentClassifier,
    private val gemmaClient: GemmaOnDeviceClient,
    private val geminiFlashClient: GeminiFlashClient,
    private val claudeClient: ClaudeClient,
    private val costTracker: LLMCostTracker,
) {
    companion object {
        private const val T1_TIMEOUT_MS = 500L
        private const val T2_TIMEOUT_MS = 2000L
        private const val T3_TIMEOUT_MS = 5000L
        private const val T4_TIMEOUT_MS = 500L
        private const val MAX_RETRIES = 3
    }

    /**
     * Route a user input to the appropriate LLM tier.
     *
     * Decision order:
     * 1. Sensitivity gate (forces T4 if sensitive context detected)
     * 2. Intent classification (determines complexity)
     * 3. Tier selection based on complexity
     * 4. Execution with timeout and fallback
     */
    suspend fun route(input: UserInput, screenState: UITreeNode): NeuronResult<ActionPlan> {
        // Step 1: ALWAYS check sensitivity gate first
        val sensitivityResult = sensitivityGate.evaluate(screenState)
        if (sensitivityResult.isSensitive) {
            return executeWithRetry(Tier.T4, input, screenState, T4_TIMEOUT_MS)
        }

        // Step 2: Classify intent complexity using on-device model (fast, ~50ms)
        val classification = intentClassifier.classify(input.text)

        // Step 3: Select tier based on complexity
        val tier = when (classification.complexity) {
            Complexity.TRIVIAL -> Tier.T1
            Complexity.SIMPLE -> Tier.T2
            Complexity.COMPLEX -> Tier.T3
        }

        // Step 4: Execute with fallback chain
        return executeWithFallback(tier, input, screenState)
    }

    /**
     * Execute request on a tier with automatic fallback to the next tier on failure.
     * Fallback chain: T1 -> T2 -> T3 -> error
     * T4 never falls back to cloud (privacy constraint).
     */
    private suspend fun executeWithFallback(
        tier: Tier,
        input: UserInput,
        screenState: UITreeNode,
    ): NeuronResult<ActionPlan> {
        val timeout = when (tier) {
            Tier.T1, Tier.T4 -> T1_TIMEOUT_MS
            Tier.T2 -> T2_TIMEOUT_MS
            Tier.T3 -> T3_TIMEOUT_MS
            Tier.T0 -> return NeuronResult.Error("T0 does not use LLM routing")
        }

        val result = executeWithRetry(tier, input, screenState, timeout)

        return when (result) {
            is NeuronResult.Success -> result
            is NeuronResult.Error -> {
                // Fallback to next tier (never escalate T4 to cloud)
                when (tier) {
                    Tier.T1 -> {
                        logFallback(Tier.T1, Tier.T2, result.message)
                        executeWithFallback(Tier.T2, input, screenState)
                    }
                    Tier.T2 -> {
                        logFallback(Tier.T2, Tier.T3, result.message)
                        executeWithFallback(Tier.T3, input, screenState)
                    }
                    Tier.T3 -> NeuronResult.Error(
                        "All LLM tiers exhausted. Last error: ${result.message}"
                    )
                    Tier.T4 -> NeuronResult.Error(
                        "On-device model failed for sensitive content. Cannot fallback to cloud."
                    )
                    Tier.T0 -> result // unreachable
                }
            }
        }
    }

    /**
     * Execute with exponential backoff retry.
     * Backoff schedule: 1s, 2s, 4s (max 3 retries).
     */
    private suspend fun executeWithRetry(
        tier: Tier,
        input: UserInput,
        screenState: UITreeNode,
        timeoutMs: Long,
    ): NeuronResult<ActionPlan> {
        var lastError: String = "Unknown error"

        repeat(MAX_RETRIES) { attempt ->
            try {
                val result = withTimeout(timeoutMs) {
                    callLLM(tier, input, screenState)
                }

                if (result is NeuronResult.Success) {
                    return result
                } else if (result is NeuronResult.Error) {
                    lastError = result.message
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                lastError = "Timeout after ${timeoutMs}ms on attempt ${attempt + 1}"
            } catch (e: Exception) {
                lastError = "Exception on attempt ${attempt + 1}: ${e.message}"
            }

            // Exponential backoff: 1s, 2s, 4s
            if (attempt < MAX_RETRIES - 1) {
                val backoffMs = 1000L * (1 shl attempt) // 1000, 2000, 4000
                delay(backoffMs)
            }
        }

        return NeuronResult.Error("Tier $tier failed after $MAX_RETRIES retries: $lastError")
    }

    /**
     * Dispatch to the correct LLM client based on tier.
     */
    private suspend fun callLLM(
        tier: Tier,
        input: UserInput,
        screenState: UITreeNode,
    ): NeuronResult<ActionPlan> {
        val startTime = System.currentTimeMillis()

        val result = when (tier) {
            Tier.T1, Tier.T4 -> gemmaClient.generate(input, screenState)
            Tier.T2 -> geminiFlashClient.generate(input, screenState)
            Tier.T3 -> claudeClient.generate(input, screenState)
            Tier.T0 -> return NeuronResult.Error("T0 is not an LLM tier")
        }

        val latencyMs = System.currentTimeMillis() - startTime

        // Track cost and latency
        if (result is NeuronResult.Success) {
            costTracker.record(
                LLMUsage(
                    tier = tier,
                    model = result.data.modelUsed,
                    inputTokens = result.data.inputTokens,
                    outputTokens = result.data.outputTokens,
                    latencyMs = latencyMs,
                    cost = calculateCost(tier, result.data.inputTokens, result.data.outputTokens),
                )
            )
        }

        return result
    }

    private fun calculateCost(tier: Tier, inputTokens: Int, outputTokens: Int): Double {
        return when (tier) {
            Tier.T1, Tier.T4 -> 0.0  // on-device, free
            Tier.T2 -> (inputTokens * 0.00000015) + (outputTokens * 0.0000006)  // Flash pricing
            Tier.T3 -> (inputTokens * 0.000003) + (outputTokens * 0.000015)     // Sonnet pricing
            Tier.T0 -> 0.0
        }
    }

    private fun logFallback(from: Tier, to: Tier, reason: String) {
        // Log to audit trail for debugging and cost analysis
        android.util.Log.w("LLMRouter", "Fallback $from -> $to: $reason")
    }
}
```

### Intent Classifier

```kotlin
package ai.neuron.brain

import javax.inject.Inject
import javax.inject.Singleton

data class IntentClassification(
    val complexity: Complexity,
    val confidence: Float,
    val suggestedTier: Tier,
    val reasoning: String,
)

@Singleton
class IntentClassifier @Inject constructor(
    private val gemmaClient: GemmaOnDeviceClient,
) {
    companion object {
        /**
         * Heuristic patterns for fast classification without LLM call.
         * If a pattern matches, skip the LLM and return immediately.
         */
        private val TRIVIAL_PATTERNS = listOf(
            Regex("(?i)^(go |open |launch |start )\\w+$"),           // "go home", "open settings"
            Regex("(?i)^(scroll |swipe |tap |click |press )"),        // direct actions
            Regex("(?i)^(turn on|turn off|enable|disable) \\w+$"),   // toggles
            Regex("(?i)^(what app|what screen|where am i)"),          // screen queries
        )

        private val COMPLEX_PATTERNS = listOf(
            Regex("(?i)(and then|after that|also|next)"),             // multi-step chaining
            Regex("(?i)(cheapest|best|compare|find.*and)"),           // reasoning required
            Regex("(?i)(book|order|pay|purchase|buy)"),               // transactional
            Regex("(?i)(schedule|remind|set up|configure)"),          // multi-step setup
        )
    }

    /**
     * Classify intent complexity. Uses heuristics first, falls back to on-device LLM.
     * Runs in <50ms for heuristic match, <200ms for LLM classification.
     */
    suspend fun classify(userIntent: String): IntentClassification {
        // Fast path: heuristic matching
        if (TRIVIAL_PATTERNS.any { it.containsMatchIn(userIntent) }) {
            return IntentClassification(
                complexity = Complexity.TRIVIAL,
                confidence = 0.9f,
                suggestedTier = Tier.T1,
                reasoning = "Matched trivial heuristic pattern",
            )
        }

        if (COMPLEX_PATTERNS.any { it.containsMatchIn(userIntent) }) {
            return IntentClassification(
                complexity = Complexity.COMPLEX,
                confidence = 0.8f,
                suggestedTier = Tier.T3,
                reasoning = "Matched complex heuristic pattern",
            )
        }

        // Slow path: ask on-device LLM for classification
        return classifyWithLLM(userIntent)
    }

    private suspend fun classifyWithLLM(userIntent: String): IntentClassification {
        val prompt = """
            Classify this user intent for a phone automation assistant.
            Respond with ONLY one word: TRIVIAL, SIMPLE, or COMPLEX.

            TRIVIAL = single action, no reasoning needed (tap, scroll, open app)
            SIMPLE = 2-5 steps, clear path (send message, search for something)
            COMPLEX = multi-app, reasoning, error recovery, open-ended planning

            Intent: "$userIntent"
        """.trimIndent()

        val response = gemmaClient.generateRaw(prompt)
        val complexity = when {
            response.contains("TRIVIAL", ignoreCase = true) -> Complexity.TRIVIAL
            response.contains("COMPLEX", ignoreCase = true) -> Complexity.COMPLEX
            else -> Complexity.SIMPLE
        }

        return IntentClassification(
            complexity = complexity,
            confidence = 0.75f,
            suggestedTier = when (complexity) {
                Complexity.TRIVIAL -> Tier.T1
                Complexity.SIMPLE -> Tier.T2
                Complexity.COMPLEX -> Tier.T3
            },
            reasoning = "LLM classification: $response",
        )
    }
}
```

---

## LLM Client Wrappers (Python)

These live in `server/brain/models.py` and are used by the Python backend for cloud LLM calls.

### Base Client and Implementations

```python
# server/brain/models.py

import time
import asyncio
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Optional

import httpx


@dataclass
class LLMResponse:
    """Standard response from any LLM client."""
    content: str
    model: str
    input_tokens: int
    output_tokens: int
    latency_ms: float
    raw_response: Optional[dict] = None


class BaseLLMClient(ABC):
    """Abstract base class for all LLM clients.

    All implementations must:
    - Accept a timeout parameter
    - Return LLMResponse with token counts
    - Raise LLMClientError on failure (not raw HTTP errors)
    """

    def __init__(self, timeout: float = 10.0):
        self.timeout = timeout
        self._client = httpx.AsyncClient(timeout=timeout)

    @abstractmethod
    async def complete(
        self,
        prompt: str,
        system: str = "",
        max_tokens: int = 1024,
        temperature: float = 0.0,
    ) -> LLMResponse:
        """Generate a completion. Must be implemented by subclasses."""
        ...

    async def close(self):
        """Clean up HTTP client resources."""
        await self._client.aclose()


class ClaudeClient(BaseLLMClient):
    """Anthropic Claude API client. Used for T3 tier (complex planning).

    Default timeout: 30s (complex tasks need more time).
    Default model: claude-sonnet-4-5-20250514
    """

    def __init__(
        self,
        api_key: str,
        model: str = "claude-sonnet-4-5-20250514",
        timeout: float = 30.0,
    ):
        super().__init__(timeout=timeout)
        self.api_key = api_key
        self.model = model

    async def complete(
        self,
        prompt: str,
        system: str = "",
        max_tokens: int = 1024,
        temperature: float = 0.0,
    ) -> LLMResponse:
        start = time.monotonic()

        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }

        body = {
            "model": self.model,
            "max_tokens": max_tokens,
            "temperature": temperature,
            "messages": [{"role": "user", "content": prompt}],
        }
        if system:
            body["system"] = system

        try:
            response = await self._client.post(
                "https://api.anthropic.com/v1/messages",
                headers=headers,
                json=body,
            )
            response.raise_for_status()
            data = response.json()

            return LLMResponse(
                content=data["content"][0]["text"],
                model=self.model,
                input_tokens=data["usage"]["input_tokens"],
                output_tokens=data["usage"]["output_tokens"],
                latency_ms=(time.monotonic() - start) * 1000,
                raw_response=data,
            )
        except httpx.HTTPStatusError as e:
            raise LLMClientError(
                f"Claude API error {e.response.status_code}: {e.response.text}",
                tier="T3",
                model=self.model,
            ) from e
        except httpx.TimeoutException as e:
            raise LLMClientError(
                f"Claude API timeout after {self.timeout}s",
                tier="T3",
                model=self.model,
            ) from e


class GeminiFlashClient(BaseLLMClient):
    """Google Gemini Flash API client. Used for T2 tier (multi-step execution).

    Default timeout: 15s.
    Default model: gemini-2.5-flash
    """

    def __init__(
        self,
        api_key: str,
        model: str = "gemini-2.5-flash",
        timeout: float = 15.0,
    ):
        super().__init__(timeout=timeout)
        self.api_key = api_key
        self.model = model

    async def complete(
        self,
        prompt: str,
        system: str = "",
        max_tokens: int = 1024,
        temperature: float = 0.0,
    ) -> LLMResponse:
        start = time.monotonic()

        body: dict = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "maxOutputTokens": max_tokens,
                "temperature": temperature,
            },
        }
        if system:
            body["systemInstruction"] = {"parts": [{"text": system}]}

        try:
            response = await self._client.post(
                f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:generateContent",
                params={"key": self.api_key},
                json=body,
            )
            response.raise_for_status()
            data = response.json()

            usage = data.get("usageMetadata", {})
            return LLMResponse(
                content=data["candidates"][0]["content"]["parts"][0]["text"],
                model=self.model,
                input_tokens=usage.get("promptTokenCount", 0),
                output_tokens=usage.get("candidatesTokenCount", 0),
                latency_ms=(time.monotonic() - start) * 1000,
                raw_response=data,
            )
        except httpx.HTTPStatusError as e:
            raise LLMClientError(
                f"Gemini API error {e.response.status_code}: {e.response.text}",
                tier="T2",
                model=self.model,
            ) from e
        except httpx.TimeoutException as e:
            raise LLMClientError(
                f"Gemini API timeout after {self.timeout}s",
                tier="T2",
                model=self.model,
            ) from e


class GeminiProClient(BaseLLMClient):
    """Google Gemini 3 Pro client. Used as T3 alternative/fallback alongside Claude.

    Default timeout: 30s.
    """

    def __init__(
        self,
        api_key: str,
        model: str = "gemini-3-pro",
        timeout: float = 30.0,
    ):
        super().__init__(timeout=timeout)
        self.api_key = api_key
        self.model = model

    async def complete(
        self,
        prompt: str,
        system: str = "",
        max_tokens: int = 2048,
        temperature: float = 0.0,
    ) -> LLMResponse:
        # Same pattern as GeminiFlashClient with different model and defaults
        start = time.monotonic()

        body: dict = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "maxOutputTokens": max_tokens,
                "temperature": temperature,
            },
        }
        if system:
            body["systemInstruction"] = {"parts": [{"text": system}]}

        response = await self._client.post(
            f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:generateContent",
            params={"key": self.api_key},
            json=body,
        )
        response.raise_for_status()
        data = response.json()

        usage = data.get("usageMetadata", {})
        return LLMResponse(
            content=data["candidates"][0]["content"]["parts"][0]["text"],
            model=self.model,
            input_tokens=usage.get("promptTokenCount", 0),
            output_tokens=usage.get("candidatesTokenCount", 0),
            latency_ms=(time.monotonic() - start) * 1000,
            raw_response=data,
        )


# --- Error types ---

class LLMClientError(Exception):
    """Raised when an LLM API call fails."""
    def __init__(self, message: str, tier: str = "", model: str = ""):
        super().__init__(message)
        self.tier = tier
        self.model = model
```

---

## Retry and Fallback Strategy

### Retry Logic

```
Attempt 1 → call LLM
  ↳ Success → return result
  ↳ Failure → wait 1s (exponential backoff base)

Attempt 2 → call LLM
  ↳ Success → return result
  ↳ Failure → wait 2s

Attempt 3 → call LLM
  ↳ Success → return result
  ↳ Failure → FALLBACK to next tier
```

- Max 3 retries per tier
- Exponential backoff: `1000ms * 2^attempt` (1s, 2s, 4s)
- Total max wait before fallback: 7 seconds
- Each retry respects the tier's timeout budget

### Fallback Chain

```
T1 (on-device) fails → retry T1 (3x) → fallback to T2 (cloud)
T2 (Flash) fails     → retry T2 (3x) → fallback to T3 (cloud)
T3 (Sonnet/Pro) fails→ retry T3 (3x) → return error to user
T4 (sensitive)       → retry T4 (3x) → return error (NEVER fallback to cloud)
```

### Python Retry Decorator

```python
# server/brain/retry.py

import asyncio
import functools
from typing import TypeVar, Callable

T = TypeVar("T")


async def with_retry(
    fn: Callable,
    max_retries: int = 3,
    base_delay: float = 1.0,
    max_delay: float = 8.0,
) -> T:
    """Execute an async function with exponential backoff retry.

    Args:
        fn: Async callable to execute
        max_retries: Maximum number of attempts
        base_delay: Initial delay in seconds (doubles each retry)
        max_delay: Maximum delay cap in seconds

    Raises:
        The last exception if all retries are exhausted.
    """
    last_exception = None

    for attempt in range(max_retries):
        try:
            return await fn()
        except Exception as e:
            last_exception = e
            if attempt < max_retries - 1:
                delay = min(base_delay * (2 ** attempt), max_delay)
                await asyncio.sleep(delay)

    raise last_exception
```

---

## Sensitivity Gate

### Full Implementation

```kotlin
package ai.neuron.brain

import javax.inject.Inject
import javax.inject.Singleton

enum class SensitivityLevel { GREEN, YELLOW, RED }

data class SensitivityResult(
    val level: SensitivityLevel,
    val reason: String,
    val detectedPatterns: List<String> = emptyList(),
) {
    val isSensitive: Boolean get() = level == SensitivityLevel.RED
}

@Singleton
class SensitivityGate @Inject constructor() {

    companion object {
        /**
         * Banking and payment app packages. RED classification.
         * Keep this list updated as new payment apps emerge.
         */
        val BANKING_PACKAGES = setOf(
            // Google
            "com.google.android.apps.walletnfcrel",    // Google Pay
            "com.google.android.apps.nbu.paisa.user",  // Google Pay (India)
            // Indian UPI
            "net.one97.paytm",                          // Paytm
            "com.phonepe.app",                          // PhonePe
            "com.dreamplug.androidapp",                 // CRED
            "com.mobikwik_new",                          // MobiKwik
            "com.freecharge.android",                    // Freecharge
            // Banks
            "com.sbi.SBIFreedomPlus",                   // SBI YONO
            "com.csam.icici.bank.imobile",              // ICICI iMobile
            "com.axis.mobile",                           // Axis Mobile
            "com.msf.kbank.mobile",                      // HDFC Mobile
            // International
            "com.venmo",                                 // Venmo
            "com.squareup.cash",                         // Cash App
            "com.paypal.android.p2pmobile",              // PayPal
            "com.zellepay.zelle",                        // Zelle
            // E-commerce with payment
            "in.amazon.mShop.android.shopping",          // Amazon
        )

        /**
         * Text patterns that indicate sensitive screen content.
         * Matched against all visible text nodes in the UI tree.
         */
        val SENSITIVE_TEXT_PATTERNS = listOf(
            Regex("(?i)\\b(pin|cvv|cvc|password|passcode|passkey)\\b"),
            Regex("(?i)\\b(otp|one.time.password|verification.code)\\b"),
            Regex("(?i)\\b(card.?number|account.?number|routing.?number)\\b"),
            Regex("(?i)\\b(social.?security|ssn|tax.?id)\\b"),
            Regex("(?i)\\b(credit.?card|debit.?card)\\b"),
            Regex("(?i)\\b(bank.?balance|net.?banking|upi.?pin)\\b"),
            Regex("(?i)\\b(private.?key|seed.?phrase|recovery.?phrase)\\b"),
        )

        /**
         * Health app packages. YELLOW classification (caution, prefer on-device).
         */
        val HEALTH_PACKAGES = setOf(
            "com.google.android.apps.fitness",
            "com.samsung.shealth",
            "com.myfitnesspal.android",
        )
    }

    /**
     * Evaluate the sensitivity of the current screen state.
     *
     * This is called BEFORE any LLM routing decision.
     * RED = force T4 (on-device only), YELLOW = prefer on-device, GREEN = any tier.
     */
    fun evaluate(screenState: UITreeNode): SensitivityResult {
        val detectedPatterns = mutableListOf<String>()

        // Check 1: Password field anywhere in the tree
        if (containsPasswordField(screenState)) {
            detectedPatterns.add("password_field")
            return SensitivityResult(
                level = SensitivityLevel.RED,
                reason = "Password field detected in UI tree",
                detectedPatterns = detectedPatterns,
            )
        }

        // Check 2: Banking/payment app package
        val packageName = screenState.packageName ?: ""
        if (packageName in BANKING_PACKAGES) {
            detectedPatterns.add("banking_package:$packageName")
            return SensitivityResult(
                level = SensitivityLevel.RED,
                reason = "Banking/payment app detected: $packageName",
                detectedPatterns = detectedPatterns,
            )
        }

        // Check 3: Sensitive text patterns in visible nodes
        val sensitiveTexts = findSensitiveText(screenState)
        if (sensitiveTexts.isNotEmpty()) {
            detectedPatterns.addAll(sensitiveTexts)
            return SensitivityResult(
                level = SensitivityLevel.RED,
                reason = "Sensitive text detected: ${sensitiveTexts.joinToString()}",
                detectedPatterns = detectedPatterns,
            )
        }

        // Check 4: Health apps (YELLOW - prefer on-device but not forced)
        if (packageName in HEALTH_PACKAGES) {
            return SensitivityResult(
                level = SensitivityLevel.YELLOW,
                reason = "Health app detected: $packageName",
                detectedPatterns = listOf("health_package:$packageName"),
            )
        }

        return SensitivityResult(
            level = SensitivityLevel.GREEN,
            reason = "No sensitive content detected",
        )
    }

    private fun containsPasswordField(node: UITreeNode): Boolean {
        if (node.isPassword) return true
        return node.children.any { containsPasswordField(it) }
    }

    private fun findSensitiveText(node: UITreeNode): List<String> {
        val matches = mutableListOf<String>()
        val text = node.text ?: ""

        for (pattern in SENSITIVE_TEXT_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                matches.add("text_pattern:${match.value}")
            }
        }

        for (child in node.children) {
            matches.addAll(findSensitiveText(child))
        }

        return matches
    }
}
```

---

## Cost Tracking

### Data Model

```kotlin
package ai.neuron.brain

import javax.inject.Inject
import javax.inject.Singleton

data class LLMUsage(
    val tier: Tier,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val latencyMs: Long,
    val cost: Double,
    val timestamp: Long = System.currentTimeMillis(),
)

@Singleton
class LLMCostTracker @Inject constructor() {
    private val _usageLog = mutableListOf<LLMUsage>()

    fun record(usage: LLMUsage) {
        _usageLog.add(usage)
        // Trim to last 1000 entries to avoid memory bloat
        if (_usageLog.size > 1000) {
            _usageLog.removeAt(0)
        }
    }

    /** Total cost in USD for the current session. */
    fun sessionCostUsd(): Double = _usageLog.sumOf { it.cost }

    /** Average latency per tier. */
    fun averageLatencyByTier(): Map<Tier, Double> =
        _usageLog.groupBy { it.tier }
            .mapValues { (_, entries) -> entries.map { it.latencyMs.toDouble() }.average() }

    /** Tier usage distribution (percentage). */
    fun tierDistribution(): Map<Tier, Double> {
        val total = _usageLog.size.toDouble()
        if (total == 0.0) return emptyMap()
        return _usageLog.groupBy { it.tier }
            .mapValues { (_, entries) -> entries.size / total * 100 }
    }

    /** Daily cost summary for the last N days. */
    fun dailyCostSummary(days: Int = 7): List<Pair<String, Double>> {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        return (0 until days).map { dayOffset ->
            val dayStart = now - (dayOffset + 1) * dayMs
            val dayEnd = now - dayOffset * dayMs
            val dayCost = _usageLog
                .filter { it.timestamp in dayStart..dayEnd }
                .sumOf { it.cost }
            val dateLabel = java.text.SimpleDateFormat("yyyy-MM-dd").format(dayStart)
            dateLabel to dayCost
        }.reversed()
    }
}
```

### Python Cost Tracking

```python
# server/brain/cost_tracker.py

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from collections import defaultdict


@dataclass
class UsageEntry:
    tier: str
    model: str
    input_tokens: int
    output_tokens: int
    latency_ms: float
    cost_usd: float
    timestamp: datetime = field(default_factory=datetime.utcnow)


class CostTracker:
    """Track LLM usage and costs across all tiers."""

    # Pricing per 1K tokens (input, output)
    PRICING = {
        "gemma-3n": (0.0, 0.0),                    # on-device
        "gemini-2.5-flash": (0.00015, 0.0006),     # per 1K tokens
        "claude-sonnet-4-5": (0.003, 0.015),        # per 1K tokens
        "gemini-3-pro": (0.00125, 0.005),           # per 1K tokens
    }

    def __init__(self):
        self._entries: list[UsageEntry] = []

    def record(self, tier: str, model: str, input_tokens: int, output_tokens: int, latency_ms: float):
        input_price, output_price = self.PRICING.get(model, (0.001, 0.002))
        cost = (input_tokens / 1000 * input_price) + (output_tokens / 1000 * output_price)

        self._entries.append(UsageEntry(
            tier=tier,
            model=model,
            input_tokens=input_tokens,
            output_tokens=output_tokens,
            latency_ms=latency_ms,
            cost_usd=cost,
        ))

    def total_cost(self, since: datetime | None = None) -> float:
        entries = self._entries
        if since:
            entries = [e for e in entries if e.timestamp >= since]
        return sum(e.cost_usd for e in entries)

    def summary(self) -> dict:
        by_tier = defaultdict(lambda: {"count": 0, "cost": 0.0, "tokens": 0})
        for e in self._entries:
            by_tier[e.tier]["count"] += 1
            by_tier[e.tier]["cost"] += e.cost_usd
            by_tier[e.tier]["tokens"] += e.input_tokens + e.output_tokens

        return {
            "total_cost_usd": self.total_cost(),
            "total_requests": len(self._entries),
            "by_tier": dict(by_tier),
        }
```

---

## Common Pitfalls

- **Never skip the sensitivity gate.** It must be the first check in every routing decision.
- **T4 must never fallback to cloud.** If the on-device model fails for sensitive content, return an error.
- **Timeout budgets are hard limits.** A T2 call must complete in <2000ms including network round-trip.
- **Always track costs.** Even on-device calls should be logged for latency analysis.
- **Intent classification itself must be fast.** Use heuristics first, LLM classification only as fallback.
- **Exponential backoff must not exceed the tier's total time budget.** For T2 (2s budget), you cannot do 3 retries with 1s+2s+4s backoff. Adjust retry count per tier.
