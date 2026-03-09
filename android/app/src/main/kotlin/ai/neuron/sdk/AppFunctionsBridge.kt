package ai.neuron.sdk

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFunctionsBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val toolRegistry: ToolRegistry,
) {

    companion object {
        private const val TAG = "NeuronAppFunctions"
    }

    suspend fun discoverAndRegister() {
        // AppFunctions API is in beta — stub for now.
        // When available, this will:
        // 1. Query AppFunctionsManager for all apps exposing functions
        // 2. Convert each AppFunction to a NeuronTool
        // 3. Register into ToolRegistry
        Log.i(TAG, "AppFunctions discovery: API not yet available, skipping")
    }
}
