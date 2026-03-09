package ai.neuron.accessibility

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ConfirmationGateTest {

    private val gate = ConfirmationGate()

    @Nested
    @DisplayName("Actions requiring confirmation")
    inner class RequiresConfirmation {

        @Test
        fun should_requireConfirmation_when_actionExplicitlyFlagged() {
            val action = LLMAction(
                actionType = ActionType.TAP,
                targetId = "send_btn",
                targetText = "Send",
                confidence = 0.95,
                requiresConfirmation = true,
            )
            assertTrue(gate.requiresConfirmation(action))
        }

        @Test
        fun should_requireConfirmation_when_tapOnSend() {
            val action = LLMAction(
                actionType = ActionType.TAP,
                targetText = "Send",
                confidence = 0.9,
            )
            assertTrue(gate.requiresConfirmation(action))
        }

        @Test
        fun should_requireConfirmation_when_tapOnDelete() {
            val action = LLMAction(
                actionType = ActionType.TAP,
                targetText = "Delete",
                confidence = 0.9,
            )
            assertTrue(gate.requiresConfirmation(action))
        }

        @Test
        fun should_requireConfirmation_when_tapOnPay() {
            val action = LLMAction(
                actionType = ActionType.TAP,
                targetText = "Pay Now",
                confidence = 0.9,
            )
            assertTrue(gate.requiresConfirmation(action))
        }

        @Test
        fun should_requireConfirmation_when_sensitiveAction() {
            val action = LLMAction(
                actionType = ActionType.TAP,
                targetText = "Confirm Transfer",
                confidence = 0.9,
                sensitive = true,
            )
            assertTrue(gate.requiresConfirmation(action))
        }

        @Test
        fun should_requireConfirmation_when_lowConfidence() {
            val action = LLMAction(
                actionType = ActionType.TAP,
                targetId = "some_btn",
                targetText = "OK",
                confidence = 0.5,
            )
            assertTrue(gate.requiresConfirmation(action))
        }
    }

    @Nested
    @DisplayName("Actions NOT requiring confirmation")
    inner class NoConfirmation {

        @Test
        fun should_notRequireConfirmation_when_normalTap() {
            val action = LLMAction(
                actionType = ActionType.TAP,
                targetId = "chat_item",
                targetText = "Chat with Mom",
                confidence = 0.95,
            )
            assertFalse(gate.requiresConfirmation(action))
        }

        @Test
        fun should_notRequireConfirmation_when_typeAction() {
            val action = LLMAction(
                actionType = ActionType.TYPE,
                targetId = "input_field",
                value = "hello",
                confidence = 0.9,
            )
            assertFalse(gate.requiresConfirmation(action))
        }

        @Test
        fun should_notRequireConfirmation_when_launchAction() {
            val action = LLMAction(
                actionType = ActionType.LAUNCH,
                value = "com.whatsapp",
                confidence = 0.95,
            )
            assertFalse(gate.requiresConfirmation(action))
        }

        @Test
        fun should_notRequireConfirmation_when_navigateAction() {
            val action = LLMAction(
                actionType = ActionType.NAVIGATE,
                value = "home",
                confidence = 0.95,
            )
            assertFalse(gate.requiresConfirmation(action))
        }
    }
}
