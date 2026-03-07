package ai.neuron.accessibility.model

sealed class ActionResult {

    data class Success(
        val action: NeuronAction,
        val verified: Boolean = false,
        val message: String? = null,
    ) : ActionResult()

    data class Error(
        val action: NeuronAction,
        val message: String,
        val cause: Throwable? = null,
    ) : ActionResult()
}
