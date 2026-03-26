package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import ai.neuron.memory.AuditRepository
import android.content.Context
import android.content.pm.PackageManager
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AppSwitchHandlerTest {
    private lateinit var handler: AppSwitchHandler
    private lateinit var crossAppContext: CrossAppContext
    private lateinit var screenExtractor: ScreenExtractor
    private lateinit var sensitivityGate: SensitivityGate
    private lateinit var intentTemplates: IntentTemplates
    private lateinit var auditRepository: AuditRepository
    private val mockContext: Context = mockk(relaxed = true)

    private lateinit var promptSanitizer: PromptSanitizer

    @BeforeEach
    fun setup() {
        sensitivityGate = SensitivityGate()
        promptSanitizer = PromptSanitizer()
        crossAppContext = CrossAppContext(promptSanitizer, sensitivityGate)
        screenExtractor = ScreenExtractor(sensitivityGate)
        intentTemplates = IntentTemplates(mockContext)
        auditRepository = mockk(relaxed = true)
        handler = AppSwitchHandler(screenExtractor, crossAppContext, intentTemplates, auditRepository, sensitivityGate)
    }

    @Nested
    @DisplayName("detectAppSwitch")
    inner class DetectAppSwitch {
        private val appResolver by lazy { AppResolver(sensitivityGate) }
        private val mockPm: PackageManager = mockk(relaxed = true)

        @Test
        fun should_detectSwitch_when_structuredIntentWithDifferentPackage() {
            val action =
                LLMAction(
                    actionType = ActionType.LAUNCH,
                    value = """{"intent_type":"ACTION_SEND","package_name":"com.whatsapp","text":"Hello"}""",
                )
            val target = handler.detectAppSwitch(action, "com.google.android.gm", appResolver, mockPm)
            assertEquals("com.whatsapp", target)
        }

        @Test
        fun should_returnNull_when_samePackage() {
            val action =
                LLMAction(
                    actionType = ActionType.LAUNCH,
                    value = """{"intent_type":"ACTION_VIEW","package_name":"com.whatsapp"}""",
                )
            val target = handler.detectAppSwitch(action, "com.whatsapp", appResolver, mockPm)
            assertNull(target)
        }

        @Test
        fun should_returnNull_when_noValue() {
            val action = LLMAction(actionType = ActionType.TAP, targetId = "btn")
            val target = handler.detectAppSwitch(action, "com.app", appResolver, mockPm)
            assertNull(target)
        }
    }

    @Nested
    @DisplayName("executeSwitch")
    inner class ExecuteSwitch {
        @Test
        fun should_switchSuccessfully_when_appLaunchesAndAppears() =
            runTest {
                val currentTree =
                    UITree(
                        packageName = "com.google.android.gm",
                        nodes = listOf(UINode(id = "addr", text = "123 Main St")),
                    )
                val targetTree = UITree(packageName = "com.google.android.apps.maps")

                val result =
                    handler.executeSwitch(
                        currentUITree = currentTree,
                        targetPackage = "com.google.android.apps.maps",
                        launchAction = { true },
                        getUITree = { targetTree },
                    )

                assertTrue(result is AppSwitchHandler.SwitchResult.Success)
                assertEquals("com.google.android.apps.maps", (result as AppSwitchHandler.SwitchResult.Success).targetPackage)
            }

        @Test
        fun should_extractDataBeforeSwitch_when_normalApp() =
            runTest {
                val currentTree =
                    UITree(
                        packageName = "com.google.android.gm",
                        nodes = listOf(UINode(id = "address", text = "456 Oak Ave")),
                    )
                val targetTree = UITree(packageName = "com.maps")

                handler.executeSwitch(
                    currentUITree = currentTree,
                    targetPackage = "com.maps",
                    launchAction = { true },
                    getUITree = { targetTree },
                )

                assertTrue(crossAppContext.getAllValues().values.any { it == "456 Oak Ave" })
            }

        @Test
        fun should_fail_when_launchFails() =
            runTest {
                val currentTree = UITree(packageName = "com.app1")
                val targetTree = UITree(packageName = "com.app1") // never switches

                val result =
                    handler.executeSwitch(
                        currentUITree = currentTree,
                        targetPackage = "com.app2",
                        // Launch always fails
                        launchAction = { false },
                        getUITree = { targetTree },
                    )

                assertTrue(result is AppSwitchHandler.SwitchResult.Failed)
                assertTrue((result as AppSwitchHandler.SwitchResult.Failed).reason.contains("Failed to launch"))
            }

        @Test
        fun should_fail_when_appDoesNotAppearInForeground() =
            runTest {
                val currentTree = UITree(packageName = "com.app1")
                // Target app never becomes foreground
                val wrongTree = UITree(packageName = "com.app1")

                val result =
                    handler.executeSwitch(
                        currentUITree = currentTree,
                        targetPackage = "com.app2",
                        launchAction = { true },
                        getUITree = { wrongTree },
                    )

                assertTrue(result is AppSwitchHandler.SwitchResult.Failed)
                assertTrue((result as AppSwitchHandler.SwitchResult.Failed).reason.contains("did not appear"))
            }

        @Test
        fun should_retryOnce_when_firstLaunchFails() =
            runTest {
                var attempts = 0
                val currentTree = UITree(packageName = "com.app1")
                val targetTree = UITree(packageName = "com.app2")

                val result =
                    handler.executeSwitch(
                        currentUITree = currentTree,
                        targetPackage = "com.app2",
                        launchAction = {
                            attempts++
                            attempts > 1 // fails first, succeeds second
                        },
                        getUITree = { targetTree },
                    )

                assertTrue(result is AppSwitchHandler.SwitchResult.Success)
                assertEquals(2, attempts)
            }

        @Test
        fun should_notExtractFromSensitiveApp_when_banking() =
            runTest {
                val bankingTree =
                    UITree(
                        packageName = "net.one97.paytm",
                        nodes = listOf(UINode(id = "balance", text = "₹50,000")),
                    )
                val targetTree = UITree(packageName = "com.whatsapp")

                handler.executeSwitch(
                    currentUITree = bankingTree,
                    targetPackage = "com.whatsapp",
                    launchAction = { true },
                    getUITree = { targetTree },
                )

                // No extracted values from banking app
                assertTrue(crossAppContext.getAllValues().isEmpty())
            }
    }

    @Nested
    @DisplayName("Audit logging")
    inner class AuditLogging {
        @Test
        fun should_logSuccessfulSwitch_when_switchCompletes() =
            runTest {
                val currentTree = UITree(packageName = "com.app1", nodes = listOf(UINode(id = "t", text = "data")))
                val targetTree = UITree(packageName = "com.app2")

                handler.executeSwitch(
                    currentUITree = currentTree,
                    targetPackage = "com.app2",
                    launchAction = { true },
                    getUITree = { targetTree },
                )

                coVerify {
                    auditRepository.logAction(
                        actionType = "APP_SWITCH",
                        targetPackage = "com.app2",
                        command = match { it.contains("com.app1") && it.contains("com.app2") },
                        success = true,
                        reasoning = any(),
                        durationMs = any(),
                    )
                }
            }

        @Test
        fun should_logFailedSwitch_when_launchFails() =
            runTest {
                val currentTree = UITree(packageName = "com.app1")

                handler.executeSwitch(
                    currentUITree = currentTree,
                    targetPackage = "com.app2",
                    launchAction = { false },
                    getUITree = { UITree(packageName = "com.app1") },
                )

                coVerify {
                    auditRepository.logAction(
                        actionType = "APP_SWITCH",
                        targetPackage = "com.app2",
                        command = any(),
                        success = false,
                        reasoning = match { it.contains("Failed to launch") },
                        durationMs = any(),
                    )
                }
            }
    }

    @Nested
    @DisplayName("Context management")
    inner class ContextManagement {
        @Test
        fun should_returnNull_when_noContext() {
            assertNull(handler.getPromptContext())
        }

        @Test
        fun should_clearContext_when_clearCalled() =
            runTest {
                val tree =
                    UITree(
                        packageName = "com.app",
                        nodes = listOf(UINode(id = "txt", text = "Some data")),
                    )
                val targetTree = UITree(packageName = "com.app2")

                handler.executeSwitch(
                    currentUITree = tree,
                    targetPackage = "com.app2",
                    launchAction = { true },
                    getUITree = { targetTree },
                )

                handler.clearContext()
                assertNull(handler.getPromptContext())
                assertTrue(handler.getAppSequence().isEmpty())
            }
    }
}
