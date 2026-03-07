package ai.neuron.accessibility

import ai.neuron.accessibility.model.ActionResult
import ai.neuron.accessibility.model.NeuronAction

class ActionExecutor(
    private val service: NeuronAccessibilityService,
) {

    fun execute(action: NeuronAction): ActionResult {
        TODO("Not yet implemented — RED phase")
    }

    companion object {
        private const val TAG = "NeuronAction"
    }
}
