package ai.neuron.brain

import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.model.LLMAction
import android.util.Log
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestration layer for cross-app transitions within multi-app tasks.
 *
 * Responsibilities:
 * - Detect when a plan step targets a different app than the foreground
 * - Trigger ScreenExtractor before switch (capture data from current app)
 * - Launch target app via ActionDispatcher
 * - Wait for app launch verification (package in UI tree, 3s timeout)
 * - Inject CrossAppContext into next LLM prompt
 * - Handle failures: app not installed → error, crash → retry once, permission denied → abort
 */
@Singleton
class AppSwitchHandler
    @Inject
    constructor(
        private val screenExtractor: ScreenExtractor,
        private val crossAppContext: CrossAppContext,
        private val intentTemplates: IntentTemplates,
    ) {
        companion object {
            private const val TAG = "AppSwitchHandler"
            const val APP_LAUNCH_TIMEOUT_MS = 3000L
            const val APP_LAUNCH_POLL_INTERVAL_MS = 300L
            const val MAX_RETRY_COUNT = 1
        }

        /**
         * Represents the result of an app switch attempt.
         */
        sealed class SwitchResult {
            data class Success(val targetPackage: String) : SwitchResult()

            data class Failed(
                val targetPackage: String,
                val reason: String,
            ) : SwitchResult()
        }

        /**
         * Check if the current action requires switching to a different app.
         * Returns the target package name if a switch is needed, null otherwise.
         */
        fun detectAppSwitch(
            action: LLMAction,
            currentPackage: String,
            appResolver: AppResolver,
            pm: android.content.pm.PackageManager,
        ): String? {
            val targetValue = action.value ?: return null

            // Try structured intent params first
            val params = intentTemplates.parseParams(targetValue)
            if (params != null) {
                // Structured params found — use package from params only
                val pkg = params.packageName ?: return null
                return if (pkg != currentPackage) pkg else null
            }

            // Fall through to AppResolver only for plain package/name values
            val resolved = appResolver.resolve(targetValue, pm)
            if (resolved != null && resolved != currentPackage) {
                return resolved
            }

            return null
        }

        /**
         * Execute a full app switch:
         * 1. Extract data from current screen
         * 2. Launch target app
         * 3. Wait for target app to appear in foreground
         *
         * @param currentUITree Current screen's UI tree (for extraction)
         * @param targetPackage Package name of the app to switch to
         * @param launchAction Callback to launch the app (delegates to ActionDispatcher)
         * @param getUITree Callback to get fresh UI tree (for verification)
         * @return SwitchResult indicating success or failure
         */
        suspend fun executeSwitch(
            currentUITree: UITree,
            targetPackage: String,
            launchAction: suspend () -> Boolean,
            getUITree: suspend () -> UITree,
        ): SwitchResult {
            // Step 1: Extract data from current screen before leaving
            val extracted = screenExtractor.extractAndStore(currentUITree, crossAppContext)
            Log.d(TAG, "Extracted $extracted values from ${currentUITree.packageName} before switch")

            // Step 2: Launch target app (with retry)
            var launched = false
            var attempts = 0

            while (attempts <= MAX_RETRY_COUNT && !launched) {
                launched = launchAction()
                if (!launched) {
                    attempts++
                    if (attempts <= MAX_RETRY_COUNT) {
                        Log.w(TAG, "Launch attempt $attempts failed for $targetPackage, retrying...")
                        delay(APP_LAUNCH_POLL_INTERVAL_MS)
                    }
                }
            }

            if (!launched) {
                Log.e(TAG, "Failed to launch $targetPackage after $attempts attempts")
                return SwitchResult.Failed(targetPackage, "Failed to launch app after $attempts attempts")
            }

            // Step 3: Wait for target app to appear in foreground
            val verified = waitForApp(targetPackage, getUITree)
            if (!verified) {
                Log.w(TAG, "Target app $targetPackage did not appear in foreground within timeout")
                return SwitchResult.Failed(targetPackage, "App launched but did not appear in foreground within ${APP_LAUNCH_TIMEOUT_MS}ms")
            }

            crossAppContext.recordAppSwitch(targetPackage)
            Log.i(TAG, "Successfully switched to $targetPackage")
            return SwitchResult.Success(targetPackage)
        }

        /**
         * Get the current cross-app context for inclusion in LLM prompts.
         * Returns null if no cross-app data exists.
         */
        fun getPromptContext(): String? = crossAppContext.buildPromptContext()

        /**
         * Clear all cross-app state. Call after task completion.
         */
        fun clearContext() {
            crossAppContext.clear()
        }

        /**
         * Get list of apps visited during the current task.
         */
        fun getAppSequence(): List<String> = crossAppContext.getAppSequence()

        private suspend fun waitForApp(
            targetPackage: String,
            getUITree: suspend () -> UITree,
        ): Boolean {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < APP_LAUNCH_TIMEOUT_MS) {
                val tree = getUITree()
                if (tree.packageName == targetPackage) {
                    return true
                }
                delay(APP_LAUNCH_POLL_INTERVAL_MS)
            }
            return false
        }
    }
