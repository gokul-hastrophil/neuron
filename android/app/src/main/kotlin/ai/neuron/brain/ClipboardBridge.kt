package ai.neuron.brain

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clipboard-based data transfer between apps.
 * Used by AppSwitchHandler to pass text data across app boundaries.
 *
 * PRIVACY: Never clipboard passwords, PINs, or data from sensitive apps.
 * Automatically clears clipboard after retrieval to minimize exposure.
 *
 * SECURITY: pasteFromClipboard() scans content for sensitive patterns
 * before returning to prevent accidental ingestion of credentials.
 */
@Singleton
class ClipboardBridge
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "ClipboardBridge"
            private const val CLIP_LABEL = "neuron_transfer"
            const val MAX_CLIP_LENGTH = 5000

            private val SENSITIVE_CLIPBOARD_PATTERNS =
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

        private val clipboardManager: ClipboardManager
            get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        /**
         * Copy text to clipboard for cross-app transfer.
         * Returns false if the text is marked sensitive or exceeds limits.
         *
         * @param text The text to copy
         * @param isSensitive Whether this data came from a sensitive context
         * @return true if successfully copied
         */
        fun copyForTransfer(
            text: String,
            isSensitive: Boolean = false,
        ): Boolean {
            if (isSensitive) {
                Log.w(TAG, "Refused to clipboard sensitive data")
                return false
            }
            if (text.isBlank()) return false
            if (containsSensitiveContent(text)) {
                Log.w(TAG, "Refused to clipboard text containing sensitive patterns")
                return false
            }

            val clipped = text.take(MAX_CLIP_LENGTH)
            val clip = ClipData.newPlainText(CLIP_LABEL, clipped)
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "Copied ${clipped.length} chars to clipboard")
            return true
        }

        /**
         * Retrieve text from clipboard (from a previous app's copy).
         * Scans for sensitive content patterns and redacts if found.
         * Auto-clears the clipboard after retrieval to minimize exposure.
         */
        fun pasteFromClipboard(): String? {
            val clip = clipboardManager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val text = clip.getItemAt(0)?.text?.toString() ?: return null

            // Auto-clear after reading to minimize exposure window
            clearClipboard()

            if (containsSensitiveContent(text)) {
                Log.w(TAG, "Clipboard contained sensitive patterns — discarded")
                return null
            }

            return text
        }

        /**
         * Clear the clipboard after transfer to minimize data exposure.
         */
        fun clearClipboard() {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
            Log.d(TAG, "Clipboard cleared after transfer")
        }

        /**
         * Copy text and schedule clipboard clear after a short delay.
         * This limits the exposure window for transferred data.
         *
         * @param text The text to copy
         * @param isSensitive Whether this data came from a sensitive context
         * @param clearDelayMs Delay before auto-clearing clipboard (default 10s)
         * @return true if successfully copied (clipboard will be cleared after delay)
         */
        fun copyWithAutoClean(
            text: String,
            isSensitive: Boolean = false,
            clearDelayMs: Long = 10_000L,
        ): Boolean {
            val copied = copyForTransfer(text, isSensitive)
            if (copied) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    { clearClipboard() },
                    clearDelayMs,
                )
            }
            return copied
        }

        private fun containsSensitiveContent(text: String): Boolean {
            return SENSITIVE_CLIPBOARD_PATTERNS.any { it.containsMatchIn(text) }
        }
    }
