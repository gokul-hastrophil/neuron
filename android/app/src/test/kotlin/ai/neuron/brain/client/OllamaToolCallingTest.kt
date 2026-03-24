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
 * Tests for OllamaCloud/OpenAI structured tool calling integration.
 * Tests the parsing logic used by OllamaCloudClient without needing Android runtime.
 */
class OllamaToolCallingTest {
    private lateinit var parser: StructuredToolCallParser
    private lateinit var schema: NeuronToolSchema

    @BeforeEach
    fun setup() {
        schema = NeuronToolSchema()
        parser = StructuredToolCallParser(schema)
    }

    @Nested
    @DisplayName("OpenAI tools generation")
    inner class ToolsGeneration {
        @Test
        fun should_generateValidOpenAITools() {
            val openAIJson = schema.toOpenAIToolsJson()
            assertTrue(openAIJson.startsWith("["))
            assertTrue(openAIJson.endsWith("]"))
            assertTrue(openAIJson.contains("\"type\":\"function\""))
            assertTrue(openAIJson.contains("\"name\":\"tap\""))
        }

        @Test
        fun should_useCorrectPropertyTypes() {
            val openAIJson = schema.toOpenAIToolsJson()
            assertTrue(openAIJson.contains("\"type\":\"string\""))
            assertTrue(openAIJson.contains("\"type\":\"object\""))
        }

        @Test
        fun should_includeEnumValues_forNavigate() {
            val openAIJson = schema.toOpenAIToolsJson()
            assertTrue(openAIJson.contains("\"enum\":[\"home\""))
        }
    }

    @Nested
    @DisplayName("OpenAI tool_calls response parsing")
    inner class ToolCallsParsing {
        @Test
        fun should_parseTapToolCall() {
            val json = """{"name": "tap", "arguments": "{\"target_id\": \"com.app:id/btn\", \"reasoning\": \"Tapping\"}"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.TAP, action.actionType)
            assertEquals("com.app:id/btn", action.targetId)
        }

        @Test
        fun should_parseLaunchToolCall() {
            val json = """{"name": "launch", "arguments": "{\"value\": \"Chrome\", \"reasoning\": \"Opening Chrome\"}"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.LAUNCH, action.actionType)
            assertEquals("Chrome", action.value)
        }

        @Test
        fun should_parseNavigateToolCall() {
            val json = """{"name": "navigate", "arguments": "{\"value\": \"back\", \"reasoning\": \"Going back\"}"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.NAVIGATE, action.actionType)
            assertEquals("back", action.value)
        }

        @Test
        fun should_parseDoneToolCall() {
            val json = """{"name": "done", "arguments": "{\"reasoning\": \"Task complete\"}"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Success)
            assertEquals(ActionType.DONE, (result as NeuronResult.Success).data.actionType)
        }

        @Test
        fun should_returnError_when_invalidArguments() {
            val json = """{"name": "tap", "arguments": "not json at all"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Error)
        }

        @Test
        fun should_returnError_when_unknownFunction() {
            val json = """{"name": "teleport", "arguments": "{}"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Error)
        }
    }
}
