package ai.neuron.sdk

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NeuronSDK
    @Inject
    constructor(
        private val toolRegistry: ToolRegistry,
    ) {
        companion object {
            private const val TAG = "NeuronSDK"
        }

        private var initialized = false

        fun init() {
            if (initialized) {
                Log.w(TAG, "SDK already initialized")
                return
            }
            initialized = true
            Log.i(TAG, "Neuron SDK initialized")
        }

        fun registerTool(tool: NeuronTool) {
            check(initialized) { "Call NeuronSDK.init() before registering tools" }
            toolRegistry.register(tool)
            Log.i(TAG, "Tool registered: ${tool.name}")
        }

        fun unregisterTool(name: String): Boolean {
            val removed = toolRegistry.unregister(name)
            if (removed) Log.i(TAG, "Tool unregistered: $name")
            return removed
        }

        fun listTools(): List<NeuronTool> = toolRegistry.listTools()
    }
