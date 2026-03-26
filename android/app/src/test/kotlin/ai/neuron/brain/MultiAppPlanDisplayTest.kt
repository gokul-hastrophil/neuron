package ai.neuron.brain

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MultiAppPlanDisplayTest {
    private lateinit var display: MultiAppPlanDisplay
    private lateinit var appResolver: AppResolver

    @BeforeEach
    fun setup() {
        appResolver = AppResolver(SensitivityGate())
        display = MultiAppPlanDisplay(appResolver)
    }

    @Nested
    @DisplayName("summarize")
    inner class Summarize {
        @Test
        fun should_detectMultiApp_when_multipleLaunchActions() {
            val actions =
                listOf(
                    LLMAction(actionType = ActionType.LAUNCH, value = "com.android.camera"),
                    LLMAction(actionType = ActionType.TAP, targetId = "shutter"),
                    LLMAction(actionType = ActionType.LAUNCH, value = "com.whatsapp"),
                    LLMAction(actionType = ActionType.TAP, targetId = "contact"),
                    LLMAction(actionType = ActionType.TYPE, value = "Photo", targetId = "input"),
                )
            val summary = display.summarize(actions)

            assertTrue(summary.isMultiApp)
            assertEquals(listOf("com.android.camera", "com.whatsapp"), summary.apps)
            assertEquals(5, summary.totalSteps)
        }

        @Test
        fun should_detectSingleApp_when_oneLaunchAction() {
            val actions =
                listOf(
                    LLMAction(actionType = ActionType.LAUNCH, value = "com.whatsapp"),
                    LLMAction(actionType = ActionType.TAP, targetId = "contact"),
                )
            val summary = display.summarize(actions)

            assertFalse(summary.isMultiApp)
            assertEquals(1, summary.apps.size)
        }

        @Test
        fun should_deduplicateConsecutiveLaunches_when_sameApp() {
            val actions =
                listOf(
                    LLMAction(actionType = ActionType.LAUNCH, value = "com.whatsapp"),
                    LLMAction(actionType = ActionType.LAUNCH, value = "com.whatsapp"),
                )
            val summary = display.summarize(actions)

            assertEquals(1, summary.apps.size)
            assertFalse(summary.isMultiApp)
        }

        @Test
        fun should_useReasoning_when_available() {
            val actions =
                listOf(
                    LLMAction(
                        actionType = ActionType.TAP,
                        targetId = "btn",
                        reasoning = "Tap the send button",
                    ),
                )
            val summary = display.summarize(actions)
            assertEquals("Tap the send button", summary.stepDescriptions.first())
        }

        @Test
        fun should_generateDescription_when_noReasoning() {
            val actions =
                listOf(
                    LLMAction(actionType = ActionType.TAP, targetId = "btn_submit", targetText = "Submit"),
                )
            val summary = display.summarize(actions)
            assertTrue(summary.stepDescriptions.first().contains("Submit"))
        }

        @Test
        fun should_detectDataTransfers_when_typeInMultiApp() {
            val actions =
                listOf(
                    LLMAction(actionType = ActionType.LAUNCH, value = "com.google.android.gm"),
                    LLMAction(actionType = ActionType.TAP, targetId = "address"),
                    LLMAction(actionType = ActionType.LAUNCH, value = "com.google.android.apps.maps"),
                    LLMAction(actionType = ActionType.TYPE, value = "123 Main St", targetId = "search"),
                )
            val summary = display.summarize(actions)

            assertTrue(summary.dataTransfers.isNotEmpty())
            assertTrue(summary.dataTransfers.first().contains("→"))
        }

        @Test
        fun should_returnEmptyApps_when_noLaunchActions() {
            val actions =
                listOf(
                    LLMAction(actionType = ActionType.TAP, targetId = "btn"),
                    LLMAction(actionType = ActionType.TYPE, value = "text", targetId = "input"),
                )
            val summary = display.summarize(actions)

            assertTrue(summary.apps.isEmpty())
            assertFalse(summary.isMultiApp)
        }
    }

    @Nested
    @DisplayName("formatForDisplay")
    inner class FormatForDisplay {
        @Test
        fun should_showAppSequence_when_multiApp() {
            val summary =
                MultiAppPlanDisplay.PlanSummary(
                    apps = listOf("Camera", "WhatsApp"),
                    totalSteps = 5,
                    stepDescriptions = emptyList(),
                    dataTransfers = listOf("Photo from Camera → WhatsApp"),
                    isMultiApp = true,
                )
            val formatted = display.formatForDisplay(summary)

            assertTrue(formatted.contains("Camera → WhatsApp"))
            assertTrue(formatted.contains("Steps: 5"))
            assertTrue(formatted.contains("Data shared:"))
        }

        @Test
        fun should_showSingleApp_when_notMultiApp() {
            val summary =
                MultiAppPlanDisplay.PlanSummary(
                    apps = listOf("WhatsApp"),
                    totalSteps = 3,
                    stepDescriptions = emptyList(),
                    dataTransfers = emptyList(),
                    isMultiApp = false,
                )
            val formatted = display.formatForDisplay(summary)

            assertTrue(formatted.contains("App: WhatsApp"))
            assertFalse(formatted.contains("Data shared:"))
        }
    }
}
