package ai.neuron.brain

import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.client.LLMClientManager
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.IntentClassification
import ai.neuron.brain.model.LLMAction
import ai.neuron.brain.model.LLMResponse
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import ai.neuron.memory.LongTermMemory
import ai.neuron.sdk.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LLMRouter @Inject constructor(
    private val sensitivityGate: SensitivityGate,
    private val clientManager: LLMClientManager,
    private val longTermMemory: LongTermMemory,
    private val toolRegistry: ToolRegistry,
) {
    companion object {
        private val FALLBACK_CHAIN = mapOf(
            LLMTier.T2 to LLMTier.T3,
        )
    }

    suspend fun route(
        command: String,
        uiTree: UITree,
        classification: IntentClassification,
    ): NeuronResult<LLMResponse> {
        // Always try deterministic pattern matching first, regardless of tier.
        // This avoids sending simple commands (open app, go back, show recents) to cloud.
        val patternAction = matchCommandPattern(command)
        if (patternAction != null) {
            val effectiveAction = if (patternAction.actionType == ActionType.LAUNCH &&
                isAppAlreadyOpen(patternAction.value, uiTree.packageName)
            ) {
                LLMAction(
                    actionType = ActionType.DONE,
                    reasoning = "${patternAction.value} is already open",
                    confidence = 1.0,
                )
            } else {
                patternAction
            }

            return NeuronResult.Success(
                LLMResponse(
                    action = effectiveAction,
                    tier = "T0",
                    modelId = "pattern-match",
                ),
            )
        }

        val effectiveTier = if (sensitivityGate.isSensitive(uiTree)) {
            LLMTier.T4
        } else {
            classification.suggestedTier
        }

        return when (effectiveTier) {
            LLMTier.T4 -> handleSensitive(command, tier = effectiveTier)
            LLMTier.T0, LLMTier.T1 -> handleOnDevice(command, uiTree, effectiveTier)
            LLMTier.T2, LLMTier.T3 -> handleCloud(command, uiTree, effectiveTier)
        }
    }

    private fun handleSensitive(
        command: String,
        tier: LLMTier,
    ): NeuronResult<LLMResponse> {
        // T4: sensitive context — NEVER send to cloud. On-device only.
        // Pattern match what we can; otherwise block with explanation.
        val patternAction = matchCommandPattern(command)
        return NeuronResult.Success(
            LLMResponse(
                action = patternAction ?: LLMAction(
                    actionType = ActionType.DONE,
                    reasoning = "Sensitive context detected — on-device processing only (Gemma 3n not yet available)",
                    confidence = 0.5,
                ),
                tier = tier.name,
                modelId = "on-device",
            ),
        )
    }

    private suspend fun handleOnDevice(
        command: String,
        uiTree: UITree,
        tier: LLMTier,
    ): NeuronResult<LLMResponse> {
        // Pattern matching already handled in route() — if we're here, no pattern matched.
        // T1 (Gemma 3n) not yet integrated — fall through to T2 cloud.
        return handleCloud(command, uiTree, LLMTier.T2)
    }

    private fun isAppAlreadyOpen(requestedApp: String?, foregroundPackage: String): Boolean {
        if (requestedApp == null) return false
        val lower = requestedApp.lowercase()
        // Check if the foreground package contains the requested app name
        val fgLower = foregroundPackage.lowercase()
        return fgLower.contains(lower) || lower.contains(fgLower.substringAfterLast('.'))
    }

    private fun matchCommandPattern(command: String): LLMAction? {
        val cmd = command.trim().lowercase()

        // "open <app>" → LAUNCH (strip articles: "the", "my", "a")
        // Skip multi-step commands containing "and" (e.g., "open WhatsApp and show me chats")
        if (!cmd.contains(" and ")) {
            val openMatch = Regex("^(?:open|launch|start)\\s+(.+)$").find(cmd)
            if (openMatch != null) {
                val raw = openMatch.groupValues[1].trim()
                val appName = raw.removePrefix("the ").removePrefix("my ").removePrefix("a ").trim()
                return LLMAction(
                    actionType = ActionType.LAUNCH,
                    value = appName,
                    reasoning = "Launching $appName",
                    confidence = 0.95,
                )
            }
        }

        // "go home" / "go back"
        if (cmd == "go home" || cmd == "home") {
            return LLMAction(actionType = ActionType.NAVIGATE, value = "home", reasoning = "Going home", confidence = 1.0)
        }
        if (cmd == "go back" || cmd == "back") {
            return LLMAction(actionType = ActionType.NAVIGATE, value = "back", reasoning = "Going back", confidence = 1.0)
        }
        if (cmd == "recents" || cmd == "show recents" || cmd == "open recents" ||
            cmd == "recent apps" || cmd == "show recent apps"
        ) {
            return LLMAction(actionType = ActionType.NAVIGATE, value = "recents", reasoning = "Opening recents", confidence = 1.0)
        }
        if (cmd == "notifications" || cmd == "show notifications" || cmd == "open notifications" ||
            cmd == "pull down the notification shade" || cmd == "pull down notifications" ||
            cmd == "notification shade" || cmd == "open notification shade"
        ) {
            return LLMAction(actionType = ActionType.NAVIGATE, value = "notifications", reasoning = "Opening notifications", confidence = 1.0)
        }
        // "go to the home screen" → home
        if (cmd.contains("home screen") || cmd == "go to home") {
            return LLMAction(actionType = ActionType.NAVIGATE, value = "home", reasoning = "Going home", confidence = 1.0)
        }

        return null
    }

    private suspend fun handleCloud(
        command: String,
        uiTree: UITree,
        tier: LLMTier,
    ): NeuronResult<LLMResponse> {
        val systemPrompt = buildSystemPrompt(command)
        val workflowHint = getWorkflowHint(command, uiTree.packageName)
        val userMessage = buildUserMessage(command, uiTree, workflowHint)

        val result = clientManager.generate(tier, systemPrompt, userMessage)

        if (result is NeuronResult.Error) {
            val fallbackTier = FALLBACK_CHAIN[tier]
            if (fallbackTier != null) {
                return clientManager.generate(fallbackTier, systemPrompt, userMessage)
            }
        }

        return result
    }

    private fun buildSystemPrompt(command: String): String {
        return """You are Neuron, an AI agent that controls Android phones.
            |Given a UI tree (JSON) and a user command, output a SINGLE next action as JSON.
            |
            |Action schema: {"action_type": "tap|type|swipe|launch|navigate|wait|done|error", "target_id": "node_resource_id", "target_text": "visible_text", "value": "text_or_package", "confidence": 0.0-1.0, "reasoning": "why"}
            |
            |RULES:
            |1. To open an app, ALWAYS use action_type "launch" with value set to the Android package name (e.g. "com.android.chrome", "com.android.settings"). NEVER use "tap" to open apps.
            |2. For "tap", target_id MUST match an exact id from the UI tree. If no matching node exists, lower your confidence.
            |3. For "type", target_id must be an editable field's id, and value is the text to type.
            |4. For "navigate", value is one of: "home", "back", "recents", "notifications".
            |5. Output only the JSON object. No markdown, no explanation outside the JSON.
            |6. If the task appears complete based on the UI tree, use action_type "done".
            |7. ALWAYS include "confidence" (0.0-1.0) in your response. Set 0.9+ when certain, 0.7-0.9 when likely correct, below 0.7 only when unsure.
            |8. Check the "package_name" field in the UI tree. If the requested app is already in the foreground, do NOT launch it again — proceed to the next step of the task.
            |9. Analyze what's visible in the UI tree to determine what step of the task has already been completed.
            |10. After typing text in a search bar or URL bar, you MUST submit it: use action_type "navigate" with value "enter" to press Enter, OR use "tap" on a search suggestion/button. Do NOT just type and stop — always follow TYPE with a submit action.
            |11. To open the notification shade, use action_type "navigate" with value "notifications". Do NOT use "swipe" for this.
            |12. If a custom tool is listed below that matches the user's intent, prefer using it by emitting action_type "tool_call" with value set to the tool name and target_text set to JSON-encoded parameters.
        """.trimMargin() + "\n" + toolRegistry.toPromptSnippet()
    }

    private fun buildUserMessage(command: String, uiTree: UITree, workflowHint: String?): String {
        val hint = if (workflowHint != null) {
            "\n|Previous successful workflow: $workflowHint\n|Use this as a hint but verify against the current UI tree.\n|"
        } else {
            ""
        }
        return """Command: $command
            |
            |Current foreground app: ${uiTree.packageName}
            |$hint
            |UI Tree:
            |${uiTree.toJson()}
        """.trimMargin()
    }

    private suspend fun getWorkflowHint(command: String, currentPackage: String): String? {
        // Try to find a cached workflow matching this command
        val taskKeyword = command.lowercase().trim()
            .split("\\s+".toRegex())
            .filter { it.length > 2 }
            .take(3)
            .joinToString("_")

        val workflow = longTermMemory.getCachedWorkflow(currentPackage, taskKeyword)
        if (workflow != null && workflow.successCount > 0) {
            return workflow.actionSequenceJson
        }
        return null
    }
}
