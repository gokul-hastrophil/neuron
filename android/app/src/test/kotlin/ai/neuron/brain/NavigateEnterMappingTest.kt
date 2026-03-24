package ai.neuron.brain

import ai.neuron.accessibility.model.GlobalActionType
import ai.neuron.accessibility.model.NeuronAction
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Navigate action mapping")
class NavigateEnterMappingTest {
    @Nested
    @DisplayName("NAVIGATE enter")
    inner class NavigateEnter {
        @Test
        fun should_mapToPressKey_when_navigateEnter() {
            val action =
                LLMAction(
                    actionType = ActionType.NAVIGATE,
                    value = "enter",
                    confidence = 0.9,
                    reasoning = "Submit search",
                )
            val result = ActionMapper.mapToNeuronAction(action)
            assertNotNull(result)
            assertTrue(result is NeuronAction.PressKey, "Expected PressKey, got $result")
            assertEquals(android.view.KeyEvent.KEYCODE_ENTER, (result as NeuronAction.PressKey).keyCode)
        }

        @Test
        fun should_mapToPressKey_when_navigateSubmit() {
            val action =
                LLMAction(
                    actionType = ActionType.NAVIGATE,
                    value = "submit",
                    confidence = 0.9,
                    reasoning = "Submit form",
                )
            val result = ActionMapper.mapToNeuronAction(action)
            assertNotNull(result)
            assertTrue(result is NeuronAction.PressKey)
            assertEquals(android.view.KeyEvent.KEYCODE_ENTER, (result as NeuronAction.PressKey).keyCode)
        }
    }

    @Nested
    @DisplayName("NAVIGATE existing actions")
    inner class ExistingNavigate {
        @Test
        fun should_mapToGlobalAction_when_navigateHome() {
            val action =
                LLMAction(
                    actionType = ActionType.NAVIGATE,
                    value = "home",
                    confidence = 0.9,
                    reasoning = "Go home",
                )
            val result = ActionMapper.mapToNeuronAction(action)
            assertNotNull(result)
            assertTrue(result is NeuronAction.GlobalAction)
            assertEquals(GlobalActionType.HOME, (result as NeuronAction.GlobalAction).action)
        }

        @Test
        fun should_mapToGlobalAction_when_navigateNotifications() {
            val action =
                LLMAction(
                    actionType = ActionType.NAVIGATE,
                    value = "notifications",
                    confidence = 0.9,
                    reasoning = "Open notifications",
                )
            val result = ActionMapper.mapToNeuronAction(action)
            assertNotNull(result)
            assertTrue(result is NeuronAction.GlobalAction)
            assertEquals(GlobalActionType.NOTIFICATIONS, (result as NeuronAction.GlobalAction).action)
        }

        @Test
        fun should_mapToGlobalAction_when_navigateBack() {
            val action =
                LLMAction(
                    actionType = ActionType.NAVIGATE,
                    value = "back",
                    confidence = 0.9,
                    reasoning = "Go back",
                )
            val result = ActionMapper.mapToNeuronAction(action)
            assertNotNull(result)
            assertTrue(result is NeuronAction.GlobalAction)
            assertEquals(GlobalActionType.BACK, (result as NeuronAction.GlobalAction).action)
        }
    }
}
