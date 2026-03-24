package ai.neuron.brain.llm

import ai.neuron.brain.NeuronToolSchema
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.NeuronResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FunctionGemmaClientTest {
    private lateinit var client: FunctionGemmaClient
    private lateinit var schema: NeuronToolSchema

    @BeforeEach
    fun setup() {
        schema = NeuronToolSchema()
        client = FunctionGemmaClient(schema)
    }

    @Nested
    @DisplayName("Availability")
    inner class Availability {
        @Test
        fun should_notBeAvailable_when_noEngine() {
            assertFalse(client.isAvailable())
        }

        @Test
        fun should_beAvailable_when_engineReady() {
            client.initialize(MockEngine(ready = true))
            assertTrue(client.isAvailable())
        }

        @Test
        fun should_notBeAvailable_when_engineNotReady() {
            client.initialize(MockEngine(ready = false))
            assertFalse(client.isAvailable())
        }
    }

    @Nested
    @DisplayName("generateAction")
    inner class GenerateAction {
        @Test
        fun should_returnError_when_noEngine() =
            runTest {
                val result = client.generateAction("open calculator")
                assertTrue(result is NeuronResult.Error)
            }

        @Test
        fun should_parseLaunchAction() =
            runTest {
                client.initialize(
                    MockEngine(
                        ready = true,
                        output = """launch(value="Calculator", reasoning="User wants to open calculator")""",
                    ),
                )

                val result = client.generateAction("open the calculator")
                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.LAUNCH, response.action?.actionType)
                assertEquals("Calculator", response.action?.value)
                assertEquals("function-gemma", response.modelId)
                assertEquals("T1", response.tier)
            }

        @Test
        fun should_parseTapAction() =
            runTest {
                client.initialize(
                    MockEngine(
                        ready = true,
                        output = """tap(target_id="btn_send", reasoning="Tapping send button")""",
                    ),
                )

                val result = client.generateAction("tap the send button")
                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.TAP, response.action?.actionType)
                assertEquals("btn_send", response.action?.targetId)
            }

        @Test
        fun should_parseNavigateAction() =
            runTest {
                client.initialize(
                    MockEngine(
                        ready = true,
                        output = """navigate(value="home", reasoning="Going to home screen")""",
                    ),
                )

                val result = client.generateAction("go home")
                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.NAVIGATE, response.action?.actionType)
                assertEquals("home", response.action?.value)
            }

        @Test
        fun should_parseDoneAction() =
            runTest {
                client.initialize(
                    MockEngine(
                        ready = true,
                        output = """done(reasoning="Task completed successfully")""",
                    ),
                )

                val result = client.generateAction("done")
                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.DONE, response.action?.actionType)
            }

        @Test
        fun should_setBaselineConfidence() =
            runTest {
                client.initialize(
                    MockEngine(
                        ready = true,
                        output = """launch(value="YouTube", reasoning="Opening YouTube")""",
                    ),
                )

                val result = client.generateAction("open youtube")
                assertTrue(result is NeuronResult.Success)
                assertEquals(0.85, (result as NeuronResult.Success).data.action?.confidence)
            }
    }

    @Nested
    @DisplayName("parseToolCall")
    inner class ParseToolCall {
        @Test
        fun should_returnError_when_invalidFormat() {
            val result = client.parseToolCall("just some text without function call")
            assertTrue(result is NeuronResult.Error)
        }

        @Test
        fun should_returnError_when_unknownFunction() {
            val result = client.parseToolCall("""unknown_func(value="test")""")
            assertTrue(result is NeuronResult.Error)
        }

        @Test
        fun should_handleSingleQuotes() {
            val result = client.parseToolCall("""launch(value='Calculator', reasoning='Open calc')""")
            assertTrue(result is NeuronResult.Success)
            assertEquals("Calculator", (result as NeuronResult.Success).data.action?.value)
        }

        @Test
        fun should_handleWhitespaceInArgs() {
            val result = client.parseToolCall("""launch( value = "YouTube" , reasoning = "Open it" )""")
            assertTrue(result is NeuronResult.Success)
            assertEquals("YouTube", (result as NeuronResult.Success).data.action?.value)
        }

        @Test
        fun should_handleEmptyArgs() {
            val result = client.parseToolCall("""done(reasoning="All done")""")
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data.action
            assertNotNull(action)
            assertEquals(ActionType.DONE, action?.actionType)
        }

        @Test
        fun should_parseTapWithTargetText() {
            val result = client.parseToolCall("""tap(target_text="Send", reasoning="Tap send")""")
            assertTrue(result is NeuronResult.Success)
            val action = (result as NeuronResult.Success).data.action
            assertEquals("Send", action?.targetText)
        }
    }

    private class MockEngine(
        private val ready: Boolean = true,
        private val output: String = "",
    ) : FunctionGemmaClient.InferenceEngine {
        override suspend fun generate(prompt: String): String = output

        override fun isReady(): Boolean = ready
    }
}
