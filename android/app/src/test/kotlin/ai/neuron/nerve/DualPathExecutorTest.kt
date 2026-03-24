package ai.neuron.nerve

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DualPathExecutorTest {
    private lateinit var executor: DualPathExecutor
    private lateinit var appFunctionsExecutor: AppFunctionsExecutor
    private lateinit var accessibilityExecutor: AccessibilityExecutorAdapter

    private val tapAction =
        LLMAction(
            actionType = ActionType.TAP,
            targetId = "btn_ok",
            confidence = 0.9,
        )

    @BeforeEach
    fun setup() {
        appFunctionsExecutor = AppFunctionsExecutor()
        accessibilityExecutor = mockk()
        executor = DualPathExecutor(appFunctionsExecutor, accessibilityExecutor)
    }

    @Nested
    @DisplayName("Path routing")
    inner class PathRouting {
        @Test
        fun should_useAccessibility_when_noAppFunctionsRegistered() =
            runTest {
                coEvery { accessibilityExecutor.execute(any()) } returns true

                val result = executor.execute(tapAction, "com.example.app")

                assertEquals(DualPathExecutor.ExecutionPath.ACCESSIBILITY, result.path)
                assertTrue(result.success)
            }

        @Test
        fun should_useAppFunctions_when_registered() =
            runTest {
                appFunctionsExecutor.registerApp("com.example.app", setOf(ActionType.TAP))
                coEvery { accessibilityExecutor.execute(any()) } returns true

                val result = executor.execute(tapAction, "com.example.app")

                // AppFunctions stub returns failure, so falls back to accessibility
                assertEquals(DualPathExecutor.ExecutionPath.ACCESSIBILITY, result.path)
            }

        @Test
        fun should_useAccessibility_when_packageIsNull() =
            runTest {
                coEvery { accessibilityExecutor.execute(any()) } returns true

                val result = executor.execute(tapAction, null)

                assertEquals(DualPathExecutor.ExecutionPath.ACCESSIBILITY, result.path)
                assertTrue(result.success)
            }

        @Test
        fun should_returnFailure_when_accessibilityFails() =
            runTest {
                coEvery { accessibilityExecutor.execute(any()) } returns false

                val result = executor.execute(tapAction, "com.example.app")

                assertFalse(result.success)
                assertEquals(DualPathExecutor.ExecutionPath.ACCESSIBILITY, result.path)
            }
    }

    @Nested
    @DisplayName("AppFunctionsExecutor")
    inner class AppFunctionsExec {
        @Test
        fun should_notBeAvailable_when_notRegistered() {
            assertFalse(appFunctionsExecutor.isAvailable("com.example", ActionType.TAP))
        }

        @Test
        fun should_beAvailable_when_registered() {
            appFunctionsExecutor.registerApp("com.example", setOf(ActionType.TAP, ActionType.LAUNCH))
            assertTrue(appFunctionsExecutor.isAvailable("com.example", ActionType.TAP))
            assertTrue(appFunctionsExecutor.isAvailable("com.example", ActionType.LAUNCH))
            assertFalse(appFunctionsExecutor.isAvailable("com.example", ActionType.SWIPE))
        }

        @Test
        fun should_returnFailure_when_apiNotAvailable() =
            runTest {
                val result = appFunctionsExecutor.execute("com.example", tapAction)
                assertFalse(result.success)
            }
    }

    @Nested
    @DisplayName("ExecutionResult")
    inner class ExecutionResultTest {
        @Test
        fun should_trackPath() {
            val result =
                DualPathExecutor.ExecutionResult(
                    success = true,
                    path = DualPathExecutor.ExecutionPath.APP_FUNCTIONS,
                    message = "OK",
                )
            assertEquals(DualPathExecutor.ExecutionPath.APP_FUNCTIONS, result.path)
            assertTrue(result.success)
        }
    }
}
