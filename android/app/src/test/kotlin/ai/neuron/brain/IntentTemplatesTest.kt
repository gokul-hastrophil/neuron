package ai.neuron.brain

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IntentTemplatesTest {
    private lateinit var templates: IntentTemplates
    private val mockContext: Context = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        templates = IntentTemplates(mockContext)
    }

    @Nested
    @DisplayName("parseParams")
    inner class ParseParams {
        @Test
        fun should_parseValidJson_when_structuredParams() {
            val json = """{"intent_type":"ACTION_SEND","package_name":"com.whatsapp","text":"Hello"}"""
            val params = templates.parseParams(json)
            assertNotNull(params)
            assertEquals("ACTION_SEND", params!!.intentType)
            assertEquals("com.whatsapp", params.packageName)
            assertEquals("Hello", params.text)
        }

        @Test
        fun should_returnNull_when_plainPackageName() {
            val result = templates.parseParams("com.whatsapp")
            assertNull(result)
        }

        @Test
        fun should_returnNull_when_nullInput() {
            assertNull(templates.parseParams(null))
        }

        @Test
        fun should_returnNull_when_invalidJson() {
            assertNull(templates.parseParams("{broken json"))
        }

        @Test
        fun should_handlePartialParams_when_missingOptionalFields() {
            val json = """{"intent_type":"ACTION_VIEW","uri":"https://maps.google.com"}"""
            val params = templates.parseParams(json)
            assertNotNull(params)
            assertEquals("ACTION_VIEW", params!!.intentType)
            assertNull(params.packageName)
            assertEquals("https://maps.google.com", params.uri)
        }

        @Test
        fun should_parseAllFields_when_fullySpecified() {
            val json =
                "{\"intent_type\":\"ACTION_SEND\"," +
                    "\"package_name\":\"com.app\"," +
                    "\"uri\":\"content://foo\"," +
                    "\"mime_type\":\"image/jpeg\"," +
                    "\"text\":\"body\"," +
                    "\"subject\":\"subj\"," +
                    "\"extra_stream\":\"content://img\"}"
            val params = templates.parseParams(json)!!
            assertEquals("ACTION_SEND", params.intentType)
            assertEquals("com.app", params.packageName)
            assertEquals("content://foo", params.uri)
            assertEquals("image/jpeg", params.mimeType)
            assertEquals("body", params.text)
            assertEquals("subj", params.subject)
            assertEquals("content://img", params.extraStream)
        }
    }

    @Nested
    @DisplayName("buildIntent")
    inner class BuildIntent {
        // Note: Intent getters return default values in unit tests (no Robolectric).
        // We verify non-null returns for valid params and null for invalid params.

        @Test
        fun should_returnNonNull_when_actionSend() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_SEND",
                    packageName = "com.whatsapp",
                    mimeType = "text/plain",
                    text = "Hello from Neuron",
                )
            assertNotNull(templates.buildIntent(params))
        }

        @Test
        fun should_returnNonNull_when_actionSendTo() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_SENDTO",
                    uri = "sms:+1234567890",
                    text = "Hello via SMS",
                )
            assertNotNull(templates.buildIntent(params))
        }

        @Test
        fun should_returnNonNull_when_actionView() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_VIEW",
                    uri = "https://maps.google.com",
                )
            assertNotNull(templates.buildIntent(params))
        }

        @Test
        fun should_returnNull_when_unknownIntentType() {
            val params = IntentTemplates.IntentParams(intentType = "ACTION_UNKNOWN")
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_returnNull_when_sendToWithoutUri() {
            val params = IntentTemplates.IntentParams(intentType = "ACTION_SENDTO")
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_returnNull_when_viewWithoutUri() {
            val params = IntentTemplates.IntentParams(intentType = "ACTION_VIEW")
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_returnNonNull_when_caseInsensitiveIntentType() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "action_send",
                    mimeType = "text/plain",
                )
            assertNotNull(templates.buildIntent(params))
        }
    }

    @Nested
    @DisplayName("URI scheme validation — intent injection prevention")
    inner class UriSchemeValidation {
        @Test
        fun should_blockContentUri_when_actionView() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_VIEW",
                    uri = "content://com.android.contacts/contacts",
                )
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_blockFileUri_when_actionView() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_VIEW",
                    uri = "file:///data/data/com.app/databases/db",
                )
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_blockJavascriptUri_when_actionView() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_VIEW",
                    uri = "javascript:alert(document.cookie)",
                )
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_allowHttpsUri_when_actionView() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_VIEW",
                    uri = "https://example.com",
                )
            assertNotNull(templates.buildIntent(params))
        }

        @Test
        fun should_allowGeoUri_when_actionView() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_VIEW",
                    uri = "geo:37.7749,-122.4194",
                )
            assertNotNull(templates.buildIntent(params))
        }

        @Test
        fun should_allowMarketUri_when_actionView() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_VIEW",
                    uri = "market://details?id=com.app",
                )
            assertNotNull(templates.buildIntent(params))
        }

        @Test
        fun should_blockContentUri_when_actionSendTo() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_SENDTO",
                    uri = "content://sms/inbox",
                )
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_allowSmsUri_when_actionSendTo() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_SENDTO",
                    uri = "sms:+1234567890",
                )
            assertNotNull(templates.buildIntent(params))
        }

        @Test
        fun should_allowMailtoUri_when_actionSendTo() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_SENDTO",
                    uri = "mailto:user@example.com",
                )
            assertNotNull(templates.buildIntent(params))
        }

        @Test
        fun should_blockSchemeWithNoColon_when_actionView() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_VIEW",
                    uri = "notavaliduri",
                )
            assertNull(templates.buildIntent(params))
        }
    }

    @Nested
    @DisplayName("Package name validation")
    inner class PackageNameValidation {
        @Test
        fun should_rejectMalformedPackage_when_noDotsPresent() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_SEND",
                    packageName = "malicious",
                    text = "test",
                )
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_rejectPackage_when_containsShellChars() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_SEND",
                    packageName = "com.app; rm -rf /",
                    text = "test",
                )
            assertNull(templates.buildIntent(params))
        }

        @Test
        fun should_acceptValidPackage_when_wellFormed() {
            val params =
                IntentTemplates.IntentParams(
                    intentType = "ACTION_SEND",
                    packageName = "com.whatsapp",
                    text = "test",
                )
            assertNotNull(templates.buildIntent(params))
        }
    }

    @Nested
    @DisplayName("MIME type validation (Fix 2)")
    inner class MimeTypeValidation {
        @Test
        fun should_allowTextPlain_when_standardMimeType() {
            assertEquals("text/plain", templates.sanitizeMimeType("text/plain"))
        }

        @Test
        fun should_allowTextHtml_when_textSubtype() {
            assertEquals("text/html", templates.sanitizeMimeType("text/html"))
        }

        @Test
        fun should_allowImageJpeg_when_imageType() {
            assertEquals("image/jpeg", templates.sanitizeMimeType("image/jpeg"))
        }

        @Test
        fun should_allowImagePng_when_imageType() {
            assertEquals("image/png", templates.sanitizeMimeType("image/png"))
        }

        @Test
        fun should_allowVideoMp4_when_videoType() {
            assertEquals("video/mp4", templates.sanitizeMimeType("video/mp4"))
        }

        @Test
        fun should_allowAudioMpeg_when_audioType() {
            assertEquals("audio/mpeg", templates.sanitizeMimeType("audio/mpeg"))
        }

        @Test
        fun should_allowApplicationPdf_when_pdfType() {
            assertEquals("application/pdf", templates.sanitizeMimeType("application/pdf"))
        }

        @Test
        fun should_allowApplicationJson_when_jsonType() {
            assertEquals("application/json", templates.sanitizeMimeType("application/json"))
        }

        @Test
        fun should_fallbackToTextPlain_when_unknownMimeType() {
            assertEquals("text/plain", templates.sanitizeMimeType("application/x-evil"))
        }

        @Test
        fun should_fallbackToTextPlain_when_applicationOctetStream() {
            assertEquals("text/plain", templates.sanitizeMimeType("application/octet-stream"))
        }

        @Test
        fun should_fallbackToTextPlain_when_arbitraryMimeType() {
            assertEquals("text/plain", templates.sanitizeMimeType("vnd.android.cursor.dir/contact"))
        }

        @Test
        fun should_fallbackToTextPlain_when_nullMimeType() {
            assertEquals("text/plain", templates.sanitizeMimeType(null))
        }

        @Test
        fun should_handleCaseInsensitive_when_upperCaseMimeType() {
            assertEquals("TEXT/PLAIN", templates.sanitizeMimeType("TEXT/PLAIN"))
        }

        @Test
        fun should_handleWhitespace_when_mimeTypeHasSpaces() {
            assertEquals(" text/plain ", templates.sanitizeMimeType(" text/plain "))
        }

        @Test
        fun should_fallbackToTextPlain_when_emptyString() {
            assertEquals("text/plain", templates.sanitizeMimeType(""))
        }
    }

    @Nested
    @DisplayName("buildFromValue")
    inner class BuildFromValue {
        @Test
        fun should_buildIntent_when_validJson() {
            val json = """{"intent_type":"ACTION_VIEW","uri":"https://example.com"}"""
            assertNotNull(templates.buildFromValue(json))
        }

        @Test
        fun should_returnNull_when_plainString() {
            assertNull(templates.buildFromValue("com.whatsapp"))
        }

        @Test
        fun should_returnNull_when_null() {
            assertNull(templates.buildFromValue(null))
        }

        @Test
        fun should_returnNull_when_maliciousContentUri() {
            val json = """{"intent_type":"ACTION_VIEW","uri":"content://com.android.settings/database/secure"}"""
            assertNull(templates.buildFromValue(json))
        }
    }
}
