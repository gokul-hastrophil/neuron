package ai.neuron.accessibility

import ai.neuron.accessibility.model.UITree

class UITreeReader(
    private val service: NeuronAccessibilityService,
    private val maxDepth: Int = MAX_DEPTH,
) {

    fun getUITree(): UITree {
        TODO("Not yet implemented — TDD RED phase")
    }

    companion object {
        const val MAX_DEPTH = 15
    }
}
