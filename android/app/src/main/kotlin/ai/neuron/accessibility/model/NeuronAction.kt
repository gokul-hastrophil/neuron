package ai.neuron.accessibility.model

import kotlinx.serialization.Serializable

@Serializable
sealed class NeuronAction {

    @Serializable
    data class Tap(val nodeId: String) : NeuronAction()

    @Serializable
    data class TapCoordinate(val x: Int, val y: Int) : NeuronAction()

    @Serializable
    data class TypeText(val nodeId: String, val text: String) : NeuronAction()

    @Serializable
    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val durationMs: Long = 300L,
    ) : NeuronAction()

    @Serializable
    data class Scroll(val nodeId: String, val direction: ScrollDirection) : NeuronAction()

    @Serializable
    data class LongPress(val nodeId: String, val durationMs: Long = 1000L) : NeuronAction()

    @Serializable
    data class LaunchApp(val packageName: String) : NeuronAction()

    @Serializable
    data class PressKey(val keyCode: Int) : NeuronAction()

    @Serializable
    data class GlobalAction(val action: GlobalActionType) : NeuronAction()
}

@Serializable
enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

@Serializable
enum class GlobalActionType {
    HOME, BACK, RECENTS, NOTIFICATIONS, QUICK_SETTINGS
}
