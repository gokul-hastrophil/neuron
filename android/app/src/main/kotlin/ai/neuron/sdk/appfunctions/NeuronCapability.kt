package ai.neuron.sdk.appfunctions

/**
 * Annotation for methods in NeuronAppFunctionsProvider subclasses.
 * Marks a function as a Neuron capability that can be discovered
 * and called by the DualPathExecutor via AppFunctions.
 *
 * Usage:
 * ```kotlin
 * class MyAppProvider : NeuronAppFunctionsProvider() {
 *     @NeuronCapability(
 *         name = "order_food",
 *         description = "Order food from the app"
 *     )
 *     suspend fun orderFood(item: String, quantity: Int): String {
 *         // implementation
 *     }
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NeuronCapability(
    /** Unique name for this capability. Used in tool calling. */
    val name: String,
    /** Human-readable description. Used in LLM prompts. */
    val description: String,
)

/**
 * Metadata extracted from a @NeuronCapability annotation + reflection.
 */
data class CapabilityMetadata(
    val name: String,
    val description: String,
    val parameterNames: List<String>,
    val providerClass: String,
    val methodName: String,
)
