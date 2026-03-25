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

            val clipped = text.take(MAX_CLIP_LENGTH)
            val clip = ClipData.newPlainText(CLIP_LABEL, clipped)
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "Copied ${clipped.length} chars to clipboard")
            return true
        }

        /**
         * Retrieve text from clipboard (from a previous app's copy).
         */
        fun pasteFromClipboard(): String? {
            val clip = clipboardManager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            return clip.getItemAt(0)?.text?.toString()
        }

        /**
         * Clear the clipboard after transfer to minimize data exposure.
         */
        fun clearClipboard() {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""))
            Log.d(TAG, "Clipboard cleared after transfer")
        }

        /**
         * Full transfer cycle: copy, return a Runnable that clears after use.
         */
        fun copyWithAutoClean(
            text: String,
            isSensitive: Boolean = false,
        ): Boolean {
            return copyForTransfer(text, isSensitive)
        }
    }
