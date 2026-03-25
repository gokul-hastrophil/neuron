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
                    uri = "geo:37.7749,-122.4194",
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
    }
}
