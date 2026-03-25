package ai.neuron.brain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parameterized intent builder for rich app launching beyond bare package launch.
 * Supports ACTION_SEND, ACTION_SENDTO, ACTION_VIEW with structured extras
 * from LLMAction.value JSON.
 */
@Singleton
class IntentTemplates
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val TAG = "IntentTemplates"

            private val json =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = false
                }
        }

        /**
         * Intent parameters as structured JSON from LLMAction.value.
         */
        @Serializable
        data class IntentParams(
            @SerialName("intent_type") val intentType: String? = null,
            @SerialName("package_name") val packageName: String? = null,
            val uri: String? = null,
            @SerialName("mime_type") val mimeType: String? = null,
            val text: String? = null,
            val subject: String? = null,
            @SerialName("extra_stream") val extraStream: String? = null,
        )

        /**
         * Try to parse LLMAction.value as structured IntentParams.
         * Returns null if the value is not valid JSON or is a plain package name.
         */
        fun parseParams(value: String?): IntentParams? {
            if (value == null) return null
            // Plain package names (contain dots but no braces) are not IntentParams
            if (!value.trimStart().startsWith("{")) return null
            return try {
                json.decodeFromString<IntentParams>(value)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Build an Intent from structured parameters. Returns null if intent_type is
         * not recognized or params are invalid.
         */
        fun buildIntent(params: IntentParams): Intent? {
            val intent =
                when (params.intentType?.uppercase()) {
                    "ACTION_SEND" -> buildSendIntent(params)
                    "ACTION_SENDTO" -> buildSendToIntent(params)
                    "ACTION_VIEW" -> buildViewIntent(params)
                    else -> {
                        Log.w(TAG, "Unknown intent_type: ${params.intentType}")
                        null
                    }
                }

            // Apply target package constraint if specified
            intent?.let {
                params.packageName?.let { pkg -> it.setPackage(pkg) }
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return intent
        }

        /**
         * Convenience: parse value and build intent in one call.
         * Returns null if value isn't structured or intent can't be built.
         */
        fun buildFromValue(value: String?): Intent? {
            val params = parseParams(value) ?: return null
            return buildIntent(params)
        }

        /**
         * Launch a parameterized intent. Returns true if launched successfully.
         */
        fun launchIntent(intent: Intent): Boolean {
            return try {
                context.startActivity(intent)
                Log.d(TAG, "Launched intent: ${intent.action}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch intent: ${e.message}", e)
                false
            }
        }

        private fun buildSendIntent(params: IntentParams): Intent {
            return Intent(Intent.ACTION_SEND).apply {
                type = params.mimeType ?: "text/plain"
                params.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                params.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                params.extraStream?.let {
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(it))
                }
            }
        }

        private fun buildSendToIntent(params: IntentParams): Intent? {
            val uri = params.uri ?: return null
            return Intent(Intent.ACTION_SENDTO, Uri.parse(uri)).apply {
                params.text?.let { putExtra("sms_body", it) }
                params.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            }
        }

        private fun buildViewIntent(params: IntentParams): Intent? {
            val uri = params.uri ?: return null
            return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        }
    }
