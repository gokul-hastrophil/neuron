package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensitivityGate @Inject constructor() {

    companion object {
        private val SENSITIVE_PACKAGES = setOf(
            // Banking / Payments
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
            // Health
            "com.google.android.apps.healthdata",
            "com.practo.fabric",
        )

        private val SENSITIVE_TEXT_PATTERNS = listOf(
            Regex("\\bPIN\\b", RegexOption.IGNORE_CASE),
            Regex("\\bCVV\\b", RegexOption.IGNORE_CASE),
            Regex("\\bOTP\\b", RegexOption.IGNORE_CASE),
            Regex("\\bPassword\\b", RegexOption.IGNORE_CASE),
        )
    }

    fun isSensitive(uiTree: UITree): Boolean {
        if (uiTree.packageName in SENSITIVE_PACKAGES) return true
        return uiTree.nodes.any { hasAnySensitiveNode(it) }
    }

    private fun hasAnySensitiveNode(node: UINode): Boolean {
        // Password input fields are always sensitive
        if (node.password) return true
        // Only flag text patterns in editable fields (actual inputs, not labels)
        if (node.editable && matchesSensitiveText(node.text)) return true
        return node.children.any { hasAnySensitiveNode(it) }
    }

    private fun matchesSensitiveText(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return SENSITIVE_TEXT_PATTERNS.any { it.containsMatchIn(text) }
    }
}
