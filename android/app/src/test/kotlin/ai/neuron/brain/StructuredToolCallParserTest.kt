package ai.neuron.brain

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.NeuronResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StructuredToolCallParserTest {
    private lateinit var parser: StructuredToolCallParser
    private lateinit var schema: NeuronToolSchema

    @BeforeEach
    fun setup() {
        schema = NeuronToolSchema()
        parser = StructuredToolCallParser(schema)
    }

    @Nested
    @DisplayName("Gemini functionCall format")
    inner class GeminiFunctionCall {
        @Test
        fun should_parseTapAction_when_geminiFunctionCallFormat() {
            val json = """{"name": "tap", "args": {"target_id": "com.app:id/btn_ok", "reasoning": "Tapping OK button"}}"""
            val result = parser.parseGeminiFunctionCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.TAP, action.actionType)
            assertEquals("com.app:id/btn_ok", action.targetId)
            assertEquals("Tapping OK button", action.reasoning)
        }

        @Test
        fun should_parseLaunchAction_when_geminiFunctionCallFormat() {
            val json = """{"name": "launch", "args": {"value": "Calculator", "reasoning": "Opening calculator"}}"""
            val result = parser.parseGeminiFunctionCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.LAUNCH, action.actionType)
            assertEquals("Calculator", action.value)
        }

        @Test
        fun should_parseNavigateAction_when_geminiFunctionCallFormat() {
            val json = """{"name": "navigate", "args": {"value": "home", "reasoning": "Going home"}}"""
            val result = parser.parseGeminiFunctionCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.NAVIGATE, action.actionType)
            assertEquals("home", action.value)
        }

        @Test
        fun should_returnError_when_unknownFunctionName() {
            val json = """{"name": "fly", "args": {"value": "moon"}}"""
            val result = parser.parseGeminiFunctionCall(json)
            assertTrue(result is NeuronResult.Error)
        }

        @Test
        fun should_handleMissingArgs_when_doneAction() {
            val json = """{"name": "done", "args": {"reasoning": "Task complete"}}"""
            val result = parser.parseGeminiFunctionCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.DONE, action.actionType)
        }
    }

    @Nested
    @DisplayName("OpenAI tool_calls format")
    inner class OpenAIToolCalls {
        @Test
        fun should_parseTapAction_when_openAIToolCallFormat() {
            val json = """{"name": "tap", "arguments": "{\"target_id\": \"com.app:id/btn\", \"reasoning\": \"Tapping\"}"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.TAP, action.actionType)
            assertEquals("com.app:id/btn", action.targetId)
        }

        @Test
        fun should_parseLaunchAction_when_openAIToolCallFormat() {
            val json = """{"name": "launch", "arguments": "{\"value\": \"Chrome\", \"reasoning\": \"Opening Chrome\"}"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.LAUNCH, action.actionType)
            assertEquals("Chrome", action.value)
        }

        @Test
        fun should_returnError_when_malformedArguments() {
            val json = """{"name": "tap", "arguments": "not valid json"}"""
            val result = parser.parseOpenAIToolCall(json)
            assertTrue(result is NeuronResult.Error)
        }
    }

    @Nested
    @DisplayName("FunctionGemma func() format")
    inner class FunctionGemmaFormat {
        @Test
        fun should_parseTapAction_when_funcFormat() {
            val raw = """tap(target_id="com.app:id/btn", reasoning="Tapping button")"""
            val result = parser.parseFunctionGemmaCall(raw)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.TAP, action.actionType)
            assertEquals("com.app:id/btn", action.targetId)
        }

        @Test
        fun should_parseLaunchAction_when_funcFormat() {
            val raw = """launch(value="YouTube", reasoning="Opening YouTube")"""
            val result = parser.parseFunctionGemmaCall(raw)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.LAUNCH, action.actionType)
            assertEquals("YouTube", action.value)
        }

        @Test
        fun should_handleSingleQuotes() {
            val raw = """navigate(value='back', reasoning='Going back')"""
            val result = parser.parseFunctionGemmaCall(raw)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.NAVIGATE, action.actionType)
            assertEquals("back", action.value)
        }
    }

    @Nested
    @DisplayName("Legacy JSON format")
    inner class LegacyJson {
        @Test
        fun should_parseBareAction_when_legacyJsonFormat() {
            val json = """{"action_type": "tap", "target_id": "com.app:id/btn", "confidence": 0.9, "reasoning": "Tapping"}"""
            val result = parser.parseLegacyJson(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.TAP, action.actionType)
            assertEquals("com.app:id/btn", action.targetId)
            assertEquals(0.9, action.confidence)
        }

        @Test
        fun should_parseWrappedResponse_when_legacyJsonFormat() {
            val json = """{"action": {"action_type": "launch", "value": "Chrome", "reasoning": "Opening"}}"""
            val result = parser.parseLegacyJson(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.LAUNCH, action.actionType)
        }

        @Test
        fun should_stripMarkdownFences_when_present() {
            val json = "```json\n{\"action_type\": \"done\", \"reasoning\": \"Done\"}\n```"
            val result = parser.parseLegacyJson(json)
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data
            assertEquals(ActionType.DONE, action.actionType)
        }
    }

    @Nested
    @DisplayName("Auto-detect format")
    inner class AutoDetect {
        @Test
        fun should_autoDetectGeminiFunctionCall() {
            val json = """{"name": "tap", "args": {"target_id": "btn_1", "reasoning": "tap"}}"""
            val result = parser.parse(json)
            assertTrue(result is NeuronResult.Success)
            assertEquals(ActionType.TAP, (result as NeuronResult.Success).data.actionType)
        }

        @Test
        fun should_autoDetectFunctionGemmaFormat() {
            val raw = """launch(value="Calculator", reasoning="Opening calc")"""
            val result = parser.parse(raw)
            assertTrue(result is NeuronResult.Success)
            assertEquals(ActionType.LAUNCH, (result as NeuronResult.Success).data.actionType)
        }

        @Test
        fun should_autoDetectLegacyJson() {
            val json = """{"action_type": "navigate", "value": "home", "reasoning": "Going home"}"""
            val result = parser.parse(json)
            assertTrue(result is NeuronResult.Success)
            assertEquals(ActionType.NAVIGATE, (result as NeuronResult.Success).data.actionType)
        }

        @Test
        fun should_returnError_when_completelyUnparseable() {
            val garbage = "hello world this is not valid"
            val result = parser.parse(garbage)
            assertTrue(result is NeuronResult.Error)
        }
    }

    @Nested
    @DisplayName("NeuronToolSchema provider formats")
    inner class ProviderFormats {
        @Test
        fun should_generateGeminiToolDeclarations() {
            val geminiJson = schema.toGeminiToolsJson()
            assertTrue(geminiJson.contains("\"name\":\"tap\""))
            assertTrue(geminiJson.contains("\"type\":\"STRING\""))
            assertTrue(geminiJson.contains("\"description\""))
        }

        @Test
        fun should_generateOpenAIToolDefinitions() {
            val openAIJson = schema.toOpenAIToolsJson()
            assertTrue(openAIJson.contains("\"type\":\"function\""))
            assertTrue(openAIJson.contains("\"name\":\"tap\""))
            assertTrue(openAIJson.contains("\"type\":\"string\""))
        }
    }
}
