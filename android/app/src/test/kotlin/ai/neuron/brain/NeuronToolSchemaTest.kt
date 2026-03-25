package ai.neuron.brain

import ai.neuron.brain.model.ActionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NeuronToolSchemaTest {
    private lateinit var schema: NeuronToolSchema

    @BeforeEach
    fun setup() {
        schema = NeuronToolSchema()
    }

    @Nested
    @DisplayName("Tool definitions")
    inner class ToolDefinitions {
        @Test
        fun should_defineSevenTools() {
            assertEquals(7, schema.tools.size)
        }

        @Test
        fun should_includeTapTool() {
            val tap = schema.tools.find { it.name == "tap" }
            assertNotNull(tap)
            assertTrue(tap!!.parameters.any { it.name == "target_id" })
            assertTrue(tap.parameters.any { it.name == "target_text" })
        }

        @Test
        fun should_includeLaunchTool() {
            val launch = schema.tools.find { it.name == "launch" }
            assertNotNull(launch)
            assertTrue(launch!!.parameters.any { it.name == "value" })
        }

        @Test
        fun should_includeNavigateToolWithEnums() {
            val nav = schema.tools.find { it.name == "navigate" }
            assertNotNull(nav)
            val valueParam = nav!!.parameters.find { it.name == "value" }
            assertNotNull(valueParam?.enumValues)
            assertTrue(valueParam!!.enumValues!!.contains("home"))
            assertTrue(valueParam.enumValues!!.contains("back"))
        }
    }

    @Nested
    @DisplayName("toActionType")
    inner class ToActionType {
        @Test
        fun should_mapKnownNames() {
            assertEquals(ActionType.TAP, schema.toActionType("tap"))
            assertEquals(ActionType.TYPE, schema.toActionType("type"))
            assertEquals(ActionType.LAUNCH, schema.toActionType("launch"))
            assertEquals(ActionType.NAVIGATE, schema.toActionType("navigate"))
            assertEquals(ActionType.SWIPE, schema.toActionType("swipe"))
            assertEquals(ActionType.DONE, schema.toActionType("done"))
            assertEquals(ActionType.ERROR, schema.toActionType("error"))
        }

        @Test
        fun should_beCaseInsensitive() {
            assertEquals(ActionType.TAP, schema.toActionType("TAP"))
            assertEquals(ActionType.LAUNCH, schema.toActionType("Launch"))
        }

        @Test
        fun should_returnNull_when_unknownName() {
            assertNull(schema.toActionType("unknown"))
            assertNull(schema.toActionType(""))
        }
    }

    @Nested
    @DisplayName("toPromptSnippet")
    inner class PromptSnippet {
        @Test
        fun should_listAllTools() {
            val snippet = schema.toPromptSnippet()
            assertTrue(snippet.contains("tap("))
            assertTrue(snippet.contains("launch("))
            assertTrue(snippet.contains("navigate("))
            assertTrue(snippet.contains("done("))
        }

        @Test
        fun should_includeParameterTypes() {
            val snippet = schema.toPromptSnippet()
            assertTrue(snippet.contains("string"))
        }
    }

    @Nested
    @DisplayName("toJson")
    inner class ToJson {
        @Test
        fun should_serializeToJson() {
            val json = schema.toJson()
            assertTrue(json.contains("\"name\":\"tap\""))
            assertTrue(json.contains("\"name\":\"launch\""))
        }
    }

    @Nested
    @DisplayName("JSON escaping (injection prevention)")
    inner class JsonEscaping {
        @Test
        fun should_escapeDoubleQuotes() {
            val escaped = NeuronToolSchema.escapeJson("say \"hello\"")
            assertEquals("say \\\"hello\\\"", escaped)
        }

        @Test
        fun should_escapeBackslashes() {
            val escaped = NeuronToolSchema.escapeJson("path\\to\\file")
            assertEquals("path\\\\to\\\\file", escaped)
        }

        @Test
        fun should_escapeNewlines() {
            val escaped = NeuronToolSchema.escapeJson("line1\nline2")
            assertEquals("line1\\nline2", escaped)
        }

        @Test
        fun should_escapeControlCharacters() {
            val escaped = NeuronToolSchema.escapeJson("tab\there")
            assertEquals("tab\\there", escaped)
        }

        @Test
        fun should_notAlterSafeStrings() {
            val safe = "Tap a UI element by its resource ID"
            assertEquals(safe, NeuronToolSchema.escapeJson(safe))
        }

        @Test
        fun should_produceValidGeminiJson_when_builtInTools() {
            val json = schema.toGeminiToolsJson()
            // Should be valid JSON array — verify balanced brackets
            assertTrue(json.startsWith("["))
            assertTrue(json.endsWith("]"))
            assertFalse(json.contains("null"))
        }

        @Test
        fun should_produceValidOpenAIJson_when_builtInTools() {
            val json = schema.toOpenAIToolsJson()
            assertTrue(json.startsWith("["))
            assertTrue(json.endsWith("]"))
            assertTrue(json.contains("\"type\":\"function\""))
        }
    }
}
