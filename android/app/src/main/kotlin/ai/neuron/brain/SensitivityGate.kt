package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensitivityGate
    @Inject
    constructor() {
        companion object {
            private val SENSITIVE_PACKAGES =
                setOf(
                    // Banking / Payments (India)
                    "net.one97.paytm",
                    "com.phonepe.app",
                    "com.google.android.apps.walletnfcrel",
                    "in.amazon.mShop.android.shopping",
                    "com.sbi.lotusintouch",
                    "com.snapwork.hdfc",
                    "com.csam.icici.bank.imobile",
                    "com.axis.mobile",
                    "com.msf.kbank.mobile",
                    "com.cred.android",
                    "com.baroda.mpassbook",
                    "com.pnbindia.pnbmbanking",
                    "com.canarabank.mobility",
                    "com.ubilogin.investinfo",
                    "com.indusind.mbanking",
                    "com.yesbank.mobilebanking",
                    // Additional UPI / Payments
                    "com.mobikwik_new",
                    "com.freecharge.android",
                    "com.bharatpe.app",
                    "com.airtel.android.mpassport",
                    // Cryptocurrency wallets
                    "io.metamask",
                    "com.wallet.crypto.trustapp",
                    "com.coinbase.android",
                    "com.binance.dev",
                    "piuk.blockchain.android",
                    "com.kraken.trade",
                    // Stock trading / Investment
                    "com.zerodha.kite",
                    "com.nextbillion.groww",
                    "com.msf.angelbrokingapp",
                    "in.upstox.pro",
                    "com.iifl.markets",
                    "com.etmoney.invest",
                    // International banking (US)
                    "com.chase.sig.android",
                    "com.wf.wellsfargomobile",
                    "com.infonow.bofa",
                    "com.citi.citimobile",
                    "com.usaa.mobile.android.usaa",
                    "com.capitalone.mobile",
                    "com.usbank.mobilebanking",
                    "com.tdbank",
                    "com.pnc.ecommerce.mobile",
                    // International banking (EU / UK)
                    "com.revolut.revolut",
                    "com.transferwise.android",
                    "com.barclays.android.barclaysmobilebanking",
                    "de.number26.android",
                    "co.uk.getmondo",
                    "uk.co.hsbc.hsbcukmobilebanking",
                    // International payments
                    "com.paypal.android.p2pmobile",
                    "com.venmo",
                    "com.squareup.cash",
                    "com.samsung.android.spay",
                    // Password managers
                    "com.agilebits.onepassword",
                    "com.x8bit.bitwarden",
                    "com.lastpass.lpandroid",
                    "com.dashlane",
                    // Authenticator / 2FA apps
                    "com.google.android.apps.authenticator2",
                    "com.azure.authenticator",
                    "com.authy.authy",
                    // Health
                    "com.google.android.apps.healthdata",
                    "com.practo.fabric",
                )

            private val SENSITIVE_TEXT_PATTERNS =
                listOf(
                    Regex("\\bPIN\\b", RegexOption.IGNORE_CASE),
                    Regex("\\bCVV\\b", RegexOption.IGNORE_CASE),
                    Regex("\\bOTP\\b", RegexOption.IGNORE_CASE),
                    Regex("\\bPassword\\b", RegexOption.IGNORE_CASE),
                    Regex("\\bPasscode\\b", RegexOption.IGNORE_CASE),
                    Regex("\\bSSN\\b", RegexOption.IGNORE_CASE),
                    Regex("\\bSeed\\s*phrase\\b", RegexOption.IGNORE_CASE),
                    Regex("\\bSecret\\s*key\\b", RegexOption.IGNORE_CASE),
                    Regex("\\bRecovery\\s*phrase\\b", RegexOption.IGNORE_CASE),
                )
        }

        fun isSensitive(uiTree: UITree): Boolean {
            if (uiTree.packageName in SENSITIVE_PACKAGES) return true
            return uiTree.nodes.any { hasAnySensitiveNode(it) }
        }

        private fun hasAnySensitiveNode(node: UINode): Boolean {
            // Password input fields are always sensitive
            if (node.password) return true
            // Check text, content description, and hint in editable fields
            if (node.editable) {
                if (matchesSensitiveText(node.text)) return true
                if (matchesSensitiveText(node.desc)) return true
                if (matchesSensitiveText(node.hintText)) return true
            }
            return node.children.any { hasAnySensitiveNode(it) }
        }

        private fun matchesSensitiveText(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            return SENSITIVE_TEXT_PATTERNS.any { it.containsMatchIn(text) }
        }
    }
