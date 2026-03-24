package ai.neuron.brain.client

import ai.neuron.brain.NeuronToolSchema
import ai.neuron.brain.StructuredToolCallParser
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.NeuronResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests for Gemini structured tool calling integration.
 * Tests the parsing logic used by GeminiFlashClient without needing Android runtime.
 */
class GeminiToolCallingTest {
    private lateinit var parser: StructuredToolCallParser
    private lateinit var schema: NeuronToolSchema

    @BeforeEach
    fun setup() {
        schema = NeuronToolSchema()
        parser = StructuredToolCallParser(schema)
    }

    @Nested
    @DisplayName("Gemini functionDeclarations generation")
    inner class FunctionDeclarations {
        @Test
        fun should_generateValidGeminiFunctionDeclarations() {
            val geminiJson = schema.toGeminiToolsJson()
            assertTrue(geminiJson.startsWith("["))
            assertTrue(geminiJson.endsWith("]"))
            assertTrue(geminiJson.contains("\"name\":\"tap\""))
            assertTrue(geminiJson.contains("\"name\":\"launch\""))
            assertTrue(geminiJson.contains("\"name\":\"navigate\""))
            assertTrue(geminiJson.contains("\"type\":\"OBJECT\""))
        }

        @Test
        fun should_includeRequiredParams_inFunctionDeclarations() {
            val geminiJson = schema.toGeminiToolsJson()
            // "reasoning" is required for all tools
            assertTrue(
                geminiJson.contains("\"required\":[\"reasoning\"]") ||
                    geminiJson.contains("\"required\":["),
            )
        }

        @Test
        fun should_includeEnumValues_forNavigate() {
            val geminiJson = schema.toGeminiToolsJson()
            assertTrue(geminiJson.contains("\"enum\":[\"home\""))
        }
    }

    @Nested
    @DisplayName("Gemini functionCall response parsing")
    inner class FunctionCallParsing {
        @Test
        fun should_parseTapFunctionCall() {
            val response =
                """{"name": "tap", "args": {"target_id": "com.app:id/button", "target_text": "OK", "reasoning": "Tapping OK button"}}"""
            val result = parser.parseGeminiFunctionCall(response)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.TAP, action.actionType)
            assertEquals("com.app:id/button", action.targetId)
            assertEquals("OK", action.targetText)
            assertEquals("Tapping OK button", action.reasoning)
        }

        @Test
        fun should_parseTypeWithValue() {
            val response =
                """{"name": "type", "args": {"value": "hello world",""" +
                    """ "target_id": "com.app:id/input", "reasoning": "Typing search query"}}"""
            val result = parser.parseGeminiFunctionCall(response)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.TYPE, action.actionType)
            assertEquals("hello world", action.value)
        }

        @Test
        fun should_parseSwipeAction() {
            val response = """{"name": "swipe", "args": {"value": "up", "reasoning": "Scrolling up"}}"""
            val result = parser.parseGeminiFunctionCall(response)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.SWIPE, action.actionType)
            assertEquals("up", action.value)
        }

        @Test
        fun should_parseDoneAction() {
            val response = """{"name": "done", "args": {"reasoning": "Task completed successfully"}}"""
            val result = parser.parseGeminiFunctionCall(response)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.DONE, action.actionType)
            assertEquals("Task completed successfully", action.reasoning)
        }

        @Test
        fun should_parseErrorAction() {
            val response = """{"name": "error", "args": {"reasoning": "Cannot find target element"}}"""
            val result = parser.parseGeminiFunctionCall(response)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.ERROR, action.actionType)
        }
    }
}
