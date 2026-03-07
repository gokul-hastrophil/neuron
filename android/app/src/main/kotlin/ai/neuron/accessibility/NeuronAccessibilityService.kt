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
        if (event == null) return
        if (debugEventLogging) {
            Log.v(
                TAG_EVENTS,
                "event=${event.eventType} pkg=${event.packageName} " +
                    "class=${event.className} text=${event.text}",
            )
        }
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
        private const val TAG_EVENTS = "NeuronEvents"

        @Volatile
        var instance: NeuronAccessibilityService? = null
            private set

        var debugEventLogging: Boolean = false
    }
}
