package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SensitivityGateTest {
    private lateinit var gate: SensitivityGate

    @BeforeEach
    fun setup() {
        gate = SensitivityGate()
    }

    @Nested
    @DisplayName("Password field detection")
    inner class PasswordFieldDetection {
        @Test
        fun should_detectSensitive_when_passwordFieldPresent() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes =
                        listOf(
                            UINode(id = "username", text = "john", editable = true),
                            UINode(id = "password", password = true, editable = true),
                        ),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_passwordFieldNestedInChildren() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes =
                        listOf(
                            UINode(
                                id = "form",
                                children =
                                    listOf(
                                        UINode(id = "password_field", password = true, editable = true),
                                    ),
                            ),
                        ),
                )
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Banking app detection")
    inner class BankingAppDetection {
        @Test
        fun should_detectSensitive_when_paytmPackage() {
            val tree = UITree(packageName = "net.one97.paytm", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_phonePePackage() {
            val tree = UITree(packageName = "com.phonepe.app", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_gpayPackage() {
            val tree =
                UITree(
                    packageName = "com.google.android.apps.walletnfcrel",
                    nodes = listOf(UINode(id = "main")),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_amazonPayPackage() {
            val tree =
                UITree(
                    packageName = "in.amazon.mShop.android.shopping",
                    nodes = listOf(UINode(id = "main")),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_sbiPackage() {
            val tree =
                UITree(
                    packageName = "com.sbi.lotusintouch",
                    nodes = listOf(UINode(id = "main")),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_hdfcPackage() {
            val tree =
                UITree(
                    packageName = "com.snapwork.hdfc",
                    nodes = listOf(UINode(id = "main")),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_iciciPackage() {
            val tree =
                UITree(
                    packageName = "com.csam.icici.bank.imobile",
                    nodes = listOf(UINode(id = "main")),
                )
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Health app detection")
    inner class HealthAppDetection {
        @Test
        fun should_detectSensitive_when_googleHealthPackage() {
            val tree =
                UITree(
                    packageName = "com.google.android.apps.healthdata",
                    nodes = listOf(UINode(id = "main")),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_practoPackage() {
            val tree =
                UITree(
                    packageName = "com.practo.fabric",
                    nodes = listOf(UINode(id = "main")),
                )
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Sensitive text in editable fields")
    inner class SensitiveEditableFields {
        @Test
        fun should_detectSensitive_when_pinInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "pin_input", text = "Enter PIN", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_cvvInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "cvv_input", text = "CVV", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_otpInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "otp_input", text = "Enter OTP", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_passwordInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "pw_input", text = "Password", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_editableFieldCaseInsensitive() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "input", text = "enter your otp here", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_notDetectSensitive_when_sensitiveTextInNonEditableLabel() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "label", text = "Password & security")),
                )
            assertFalse(gate.isSensitive(tree))
        }

        @Test
        fun should_notDetectSensitive_when_sensitiveTextInDescription() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", desc = "PIN input field")),
                )
            assertFalse(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Non-sensitive screens")
    inner class NonSensitiveScreens {
        @Test
        fun should_notDetectSensitive_when_normalApp() {
            val tree =
                UITree(
                    packageName = "com.whatsapp",
                    nodes =
                        listOf(
                            UINode(id = "chat_list", text = "Chats", clickable = true),
                            UINode(id = "search", text = "Search", clickable = true),
                        ),
                )
            assertFalse(gate.isSensitive(tree))
        }

        @Test
        fun should_notDetectSensitive_when_settingsApp() {
            val tree =
                UITree(
                    packageName = "com.android.settings",
                    nodes =
                        listOf(
                            UINode(id = "wifi", text = "Wi-Fi", clickable = true),
                            UINode(id = "bluetooth", text = "Bluetooth", clickable = true),
                        ),
                )
            assertFalse(gate.isSensitive(tree))
        }

        @Test
        fun should_notDetectSensitive_when_emptyTree() {
            val tree = UITree.empty()
            assertFalse(gate.isSensitive(tree))
        }

        @Test
        fun should_notDetectSensitive_when_editableButNotPassword() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes =
                        listOf(
                            UINode(id = "search_box", text = "Search", editable = true),
                        ),
                )
            assertFalse(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Mixed screens")
    inner class MixedScreens {
        @Test
        fun should_detectSensitive_when_mixOfSensitiveAndNonSensitive() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes =
                        listOf(
                            UINode(id = "title", text = "Login"),
                            UINode(id = "username", text = "Email", editable = true),
                            UINode(id = "password", password = true, editable = true),
                            UINode(id = "submit", text = "Sign In", clickable = true),
                        ),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_deeplyNestedPasswordField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes =
                        listOf(
                            UINode(
                                id = "root",
                                children =
                                    listOf(
                                        UINode(
                                            id = "container",
                                            children =
                                                listOf(
                                                    UINode(
                                                        id = "form",
                                                        children =
                                                            listOf(
                                                                UINode(id = "pw", password = true),
                                                            ),
                                                    ),
                                                ),
                                        ),
                                    ),
                            ),
                        ),
                )
            assertTrue(gate.isSensitive(tree))
        }
    }
}
