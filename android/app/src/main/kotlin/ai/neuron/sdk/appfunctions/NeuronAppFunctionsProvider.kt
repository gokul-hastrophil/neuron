package ai.neuron.sdk.appfunctions

/**
 * Base class for apps that want to expose capabilities to Neuron.
 *
 * Subclasses annotate their methods with @NeuronCapability.
 * CapabilityScanner discovers these at runtime via reflection.
 *
 * This maps to the Android AppFunctions provider pattern:
 * annotated methods become AppFunctions that AI agents can call directly,
 * without needing Accessibility access.
 */
abstract class NeuronAppFunctionsProvider {
    /** Unique provider ID. Typically the app's package name. */
    abstract val providerId: String

    /** Human-readable provider name. */
    abstract val displayName: String

    /** Provider version. */
    open val version: String = "1.0.0"

    /**
     * Called when the provider is registered with Neuron.
     * Override to initialize resources.
     */
    open fun onRegister() {}

    /**
     * Called when the provider is unregistered.
     * Override to clean up resources.
     */
    open fun onUnregister() {}
}
