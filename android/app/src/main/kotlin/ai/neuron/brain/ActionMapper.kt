package ai.neuron.brain

import ai.neuron.accessibility.model.GlobalActionType
import ai.neuron.accessibility.model.NeuronAction
import ai.neuron.accessibility.model.ScrollDirection
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import android.view.KeyEvent

object ActionMapper {

    fun mapToNeuronAction(action: LLMAction): NeuronAction? =
        when (action.actionType) {
            ActionType.TAP -> {
                val nodeId = action.targetId ?: return null
                NeuronAction.Tap(nodeId = nodeId)
            }
            ActionType.TYPE -> {
                val nodeId = action.targetId ?: return null
                val text = action.value ?: return null
                NeuronAction.TypeText(nodeId = nodeId, text = text)
            }
            ActionType.SWIPE -> {
                val direction = when (action.value?.lowercase()) {
                    "up" -> ScrollDirection.UP
                    "left" -> ScrollDirection.LEFT
                    "right" -> ScrollDirection.RIGHT
                    else -> ScrollDirection.DOWN
                }
                NeuronAction.Scroll(nodeId = "", direction = direction)
            }
            ActionType.NAVIGATE -> mapNavigate(action.value)
            ActionType.DONE,
            ActionType.ERROR,
            ActionType.CONFIRM,
            ActionType.WAIT,
            ActionType.LAUNCH,
            ActionType.TOOL_CALL,
            -> null
        }

    internal fun mapNavigate(value: String?): NeuronAction? {
        return when (value?.lowercase()) {
            "home" -> NeuronAction.GlobalAction(action = GlobalActionType.HOME)
            "back" -> NeuronAction.GlobalAction(action = GlobalActionType.BACK)
            "recents" -> NeuronAction.GlobalAction(action = GlobalActionType.RECENTS)
            "notifications" -> NeuronAction.GlobalAction(action = GlobalActionType.NOTIFICATIONS)
            "quick_settings", "quicksettings" -> NeuronAction.GlobalAction(action = GlobalActionType.QUICK_SETTINGS)
            "enter", "submit", "return" -> NeuronAction.PressKey(keyCode = KeyEvent.KEYCODE_ENTER)
            else -> NeuronAction.GlobalAction(action = GlobalActionType.HOME)
        }
    }
}
