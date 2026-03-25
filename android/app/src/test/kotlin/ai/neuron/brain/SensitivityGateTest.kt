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

        @Test
        fun should_detectSensitive_when_bankOfBarodaPackage() {
            val tree = UITree(packageName = "com.baroda.mpassbook", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_pnbPackage() {
            val tree = UITree(packageName = "com.pnbindia.pnbmbanking", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Crypto wallet detection")
    inner class CryptoWalletDetection {
        @Test
        fun should_detectSensitive_when_metamaskPackage() {
            val tree = UITree(packageName = "io.metamask", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_trustWalletPackage() {
            val tree = UITree(packageName = "com.wallet.crypto.trustapp", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_coinbasePackage() {
            val tree = UITree(packageName = "com.coinbase.android", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_binancePackage() {
            val tree = UITree(packageName = "com.binance.dev", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Stock trading app detection")
    inner class StockTradingDetection {
        @Test
        fun should_detectSensitive_when_zerodhaPackage() {
            val tree = UITree(packageName = "com.zerodha.kite", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_growwPackage() {
            val tree = UITree(packageName = "com.nextbillion.groww", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_angelOnePackage() {
            val tree = UITree(packageName = "com.msf.angelbrokingapp", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_upstoxPackage() {
            val tree = UITree(packageName = "in.upstox.pro", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Additional UPI/payment detection")
    inner class AdditionalUPIDetection {
        @Test
        fun should_detectSensitive_when_mobikwikPackage() {
            val tree = UITree(packageName = "com.mobikwik_new", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_freechargePackage() {
            val tree = UITree(packageName = "com.freecharge.android", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_bharatpePackage() {
            val tree = UITree(packageName = "com.bharatpe.app", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_airtelPaymentsPackage() {
            val tree = UITree(packageName = "com.airtel.android.mpassport", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("International banking detection")
    inner class InternationalBankingDetection {
        @Test
        fun should_detectSensitive_when_chasePackage() {
            val tree = UITree(packageName = "com.chase.sig.android", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_wellsFargoPackage() {
            val tree = UITree(packageName = "com.wf.wellsfargomobile", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_revolutPackage() {
            val tree = UITree(packageName = "com.revolut.revolut", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_wisePackage() {
            val tree = UITree(packageName = "com.transferwise.android", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_bankOfAmericaPackage() {
            val tree = UITree(packageName = "com.infonow.bofa", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_citiPackage() {
            val tree = UITree(packageName = "com.citi.citimobile", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_capitalOnePackage() {
            val tree = UITree(packageName = "com.capitalone.mobile", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_n26Package() {
            val tree = UITree(packageName = "de.number26.android", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_monzoPackage() {
            val tree = UITree(packageName = "co.uk.getmondo", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_venmoPackage() {
            val tree = UITree(packageName = "com.venmo", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_cashAppPackage() {
            val tree = UITree(packageName = "com.squareup.cash", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Password manager detection")
    inner class PasswordManagerDetection {
        @Test
        fun should_detectSensitive_when_1passwordPackage() {
            val tree = UITree(packageName = "com.agilebits.onepassword", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_bitwardenPackage() {
            val tree = UITree(packageName = "com.x8bit.bitwarden", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_lastpassPackage() {
            val tree = UITree(packageName = "com.lastpass.lpandroid", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_dashlanePackage() {
            val tree = UITree(packageName = "com.dashlane", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Authenticator app detection")
    inner class AuthenticatorDetection {
        @Test
        fun should_detectSensitive_when_googleAuthenticatorPackage() {
            val tree =
                UITree(
                    packageName = "com.google.android.apps.authenticator2",
                    nodes = listOf(UINode(id = "main")),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_microsoftAuthenticatorPackage() {
            val tree = UITree(packageName = "com.azure.authenticator", nodes = listOf(UINode(id = "main")))
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_authyPackage() {
            val tree = UITree(packageName = "com.authy.authy", nodes = listOf(UINode(id = "main")))
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
        fun should_notDetectSensitive_when_sensitiveTextInNonEditableDescription() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", desc = "PIN input field")),
                )
            assertFalse(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Content description detection in editable fields")
    inner class ContentDescriptionDetection {
        @Test
        fun should_detectSensitive_when_editableFieldHasPinDescription() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", desc = "Enter PIN", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_editableFieldHasCvvDescription() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", desc = "CVV security code", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_editableFieldHasPasswordDescription() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", desc = "Password field", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_editableFieldHasOtpDescription() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", desc = "OTP verification", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Hint text detection in editable fields")
    inner class HintTextDetection {
        @Test
        fun should_detectSensitive_when_editableFieldHasPinHint() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", hintText = "Enter your PIN", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_editableFieldHasPasswordHint() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", hintText = "Password", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_editableFieldHasCvvHint() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "field", hintText = "Enter CVV", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_notDetectSensitive_when_nonEditableFieldHasPasswordHint() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "label", hintText = "Password")),
                )
            assertFalse(gate.isSensitive(tree))
        }
    }

    @Nested
    @DisplayName("Additional sensitive text patterns")
    inner class AdditionalSensitivePatterns {
        @Test
        fun should_detectSensitive_when_passcodeInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "input", text = "Enter Passcode", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_ssnInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "input", text = "SSN", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_seedPhraseInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "input", text = "Enter seed phrase", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_secretKeyInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "input", text = "Secret key", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
        }

        @Test
        fun should_detectSensitive_when_recoveryPhraseInEditableField() {
            val tree =
                UITree(
                    packageName = "com.example.app",
                    nodes = listOf(UINode(id = "input", text = "Recovery phrase", editable = true)),
                )
            assertTrue(gate.isSensitive(tree))
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
