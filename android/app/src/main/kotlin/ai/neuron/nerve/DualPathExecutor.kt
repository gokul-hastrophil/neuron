package ai.neuron.nerve

import ai.neuron.accessibility.ActionExecutor
import ai.neuron.accessibility.NeuronAccessibilityService
import ai.neuron.accessibility.model.NeuronAction
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dual-path execution layer: routes actions through AppFunctions (preferred,
 * policy-compliant) or Accessibility (fallback).
 *
 * This is the central execution gateway. All actions flow through here.
 *
 * Priority:
 * 1. AppFunctions — if the target app exposes the capability via Android AppFunctions API
 * 2. Accessibility — traditional UI tree manipulation via AccessibilityService
 *
 * Play Store compliance: AppFunctions path requires no AccessibilityService permission
 * and is explicitly encouraged by Google for AI agent interactions.
 */
@Singleton
class DualPathExecutor @Inject constructor(
    private val appFunctionsExecutor: AppFunctionsExecutor,
    private val accessibilityExecutor: AccessibilityExecutorAdapter,
) {
    /**
     * Result of an execution attempt, indicating which path was used.
     */
    data class ExecutionResult(
        val success: Boolean,
        val path: ExecutionPath,
        val message: String? = null,
    )

    enum class ExecutionPath {
        APP_FUNCTIONS,
        ACCESSIBILITY,
        PATTERN_MATCH,
    }

    /**
     * Execute an LLMAction through the best available path.
     */
    suspend fun execute(action: LLMAction, targetPackage: String?): ExecutionResult {
        // Step 1: Try AppFunctions if the action type is supported
        if (targetPackage != null && appFunctionsExecutor.isAvailable(targetPackage, action.actionType)) {
            Log.d(TAG, "Attempting AppFunctions path for $targetPackage:${action.actionType}")
            val result = appFunctionsExecutor.execute(targetPackage, action)
            if (result.success) {
                return ExecutionResult(
                    success = true,
                    path = ExecutionPath.APP_FUNCTIONS,
                    message = "Executed via AppFunctions",
                )
            }
            Log.w(TAG, "AppFunctions failed, falling back to Accessibility: ${result.message}")
        }

        // Step 2: Fall back to Accessibility
        Log.d(TAG, "Using Accessibility path for ${action.actionType}")
        val accessibilityResult = accessibilityExecutor.execute(action)
        return ExecutionResult(
            success = accessibilityResult,
            path = ExecutionPath.ACCESSIBILITY,
            message = if (accessibilityResult) "Executed via Accessibility" else "Accessibility execution failed",
        )
    }

    companion object {
        private const val TAG = "DualPathExecutor"
    }
}

/**
 * AppFunctions executor — calls app capabilities via Android AppFunctions API.
 *
 * Currently a capability-checking stub. When `androidx.appfunctions` reaches stable,
 * this will use AppFunctionsManager to discover and invoke app functions.
 */
@Singleton
class AppFunctionsExecutor @Inject constructor() {

    data class Result(val success: Boolean, val message: String? = null)

    /**
     * Registry of known AppFunctions-capable packages.
     * Will be populated by AppFunctionsBridge.discoverAndRegister().
     */
    private val registeredApps = mutableMapOf<String, Set<ActionType>>()

    fun registerApp(packageName: String, supportedActions: Set<ActionType>) {
        registeredApps[packageName] = supportedActions
    }

    fun isAvailable(packageName: String, actionType: ActionType): Boolean {
        return registeredApps[packageName]?.contains(actionType) == true
    }

    suspend fun execute(packageName: String, action: LLMAction): Result {
        // AppFunctions API stub — will be implemented when API is stable
        return Result(success = false, message = "AppFunctions API not yet available")
    }

    companion object {
        private const val TAG = "AppFunctionsExec"
    }
}

/**
 * Adapter wrapping the existing ActionExecutor for use in DualPathExecutor.
 * Converts LLMAction → NeuronAction → ActionExecutor.execute().
 *
 * Creates ActionExecutor on demand from the live AccessibilityService instance.
 */
@Singleton
class AccessibilityExecutorAdapter @Inject constructor() {

    suspend fun execute(action: LLMAction): Boolean {
        val service = NeuronAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "AccessibilityService not active, cannot execute ${action.actionType}")
            return false
        }
        val executor = ActionExecutor(service)

        // ActionMapper excludes LAUNCH — handle it here with the resolved package
        val neuronAction = if (action.actionType == ActionType.LAUNCH) {
            val packageName = action.value ?: return false
            NeuronAction.LaunchApp(packageName = packageName)
        } else {
            ai.neuron.brain.ActionMapper.mapToNeuronAction(action) ?: return false
        }

        val result = executor.execute(neuronAction)
        if (result is ai.neuron.accessibility.model.ActionResult.Error) {
            Log.e(TAG, "Accessibility execution failed: ${result.message}")
        }
        return result is ai.neuron.accessibility.model.ActionResult.Success
    }

    companion object {
        private const val TAG = "A11yExecAdapter"
    }
}
