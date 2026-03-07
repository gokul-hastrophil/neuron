package ai.neuron.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class NeuronAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "NeuronAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Will be used by UITreeReader and event-driven actions in later tasks
    }

    override fun onInterrupt() {
        Log.w(TAG, "NeuronAccessibilityService interrupted")
    }

    override fun onDestroy() {
        Log.i(TAG, "NeuronAccessibilityService destroyed")
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NeuronAS"

        @Volatile
        var instance: NeuronAccessibilityService? = null
            private set
    }
}
