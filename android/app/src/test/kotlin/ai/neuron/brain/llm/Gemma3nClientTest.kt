package ai.neuron.brain.llm

import ai.neuron.brain.NeuronToolSchema
import ai.neuron.brain.StructuredToolCallParser
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.NeuronResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class Gemma3nClientTest {
    private lateinit var client: Gemma3nClient
    private lateinit var schema: NeuronToolSchema
    private lateinit var parser: StructuredToolCallParser

    @BeforeEach
    fun setup() {
        schema = NeuronToolSchema()
        parser = StructuredToolCallParser(schema)
        client = Gemma3nClient(schema, parser)
    }

    @Nested
    @DisplayName("Availability")
    inner class Availability {
        @Test
        fun should_notBeAvailable_when_noEngineSet() {
            assertFalse(client.isAvailable())
        }

        @Test
        fun should_beAvailable_when_engineReady() {
            client.initialize(mockEngine(ready = true))
            assertTrue(client.isAvailable())
        }

        @Test
        fun should_notBeAvailable_when_engineNotReady() {
            client.initialize(mockEngine(ready = false))
            assertFalse(client.isAvailable())
        }

        @Test
        fun should_reportMultimodalSupport() {
            client.initialize(mockEngine(ready = true, multimodal = true))
            assertTrue(client.supportsMultimodal())
        }

        @Test
        fun should_reportNoMultimodal_when_textOnly() {
            client.initialize(mockEngine(ready = true, multimodal = false))
            assertFalse(client.supportsMultimodal())
        }
    }

    @Nested
    @DisplayName("Text-only generation (T1.5)")
    inner class TextGeneration {
        @Test
        fun should_returnError_when_noEngine() =
            runTest {
                val result = client.generateAction("open calculator")
                assertTrue(result is NeuronResult.Error)
            }

        @Test
        fun should_parseTapAction_when_engineReturnsFuncCall() =
            runTest {
                client.initialize(
                    mockEngine(
                        ready = true,
                        output = """tap(target_id="com.app:id/btn", reasoning="Tapping button")""",
                    ),
                )
                val result = client.generateAction("tap the button")
                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.TAP, response.action?.actionType)
                assertEquals("T1.5", response.tier)
                assertEquals("gemma-3n-e2b", response.modelId)
            }

        @Test
        fun should_parseLaunchAction() =
            runTest {
                client.initialize(
                    mockEngine(
                        ready = true,
                        output = """launch(value="YouTube", reasoning="Opening YouTube")""",
                    ),
                )
                val result = client.generateAction("open YouTube")
                assertTrue(result is NeuronResult.Success)
                assertEquals(ActionType.LAUNCH, (result as NeuronResult.Success).data.action?.actionType)
            }

        @Test
        fun should_handleScreenContext() =
            runTest {
                client.initialize(
                    mockEngine(
                        ready = true,
                        output = """done(reasoning="Already on home screen")""",
                    ),
                )
                val result = client.generateAction("go home", screenContext = "Home screen with 5 apps")
                assertTrue(result is NeuronResult.Success)
                assertEquals(ActionType.DONE, (result as NeuronResult.Success).data.action?.actionType)
            }

        @Test
        fun should_returnError_when_unparseable() =
            runTest {
                client.initialize(mockEngine(ready = true, output = "garbage output"))
                val result = client.generateAction("do something")
                assertTrue(result is NeuronResult.Error)
            }
    }

    @Nested
    @DisplayName("Multimodal generation (T4)")
    inner class MultimodalGeneration {
        @Test
        fun should_returnError_when_noMultimodalSupport() =
            runTest {
                client.initialize(mockEngine(ready = true, multimodal = false))
                val result = client.generateFromScreenshot("enter PIN", ByteArray(100))
                assertTrue(result is NeuronResult.Error)
                assertTrue((result as NeuronResult.Error).message.contains("multimodal"))
            }

        @Test
        fun should_parseTapAction_when_multimodalEnabled() =
            runTest {
                client.initialize(
                    mockEngine(
                        ready = true,
                        multimodal = true,
                        imageOutput = """tap(target_id="com.bank:id/pin_field", reasoning="Tapping PIN field")""",
                    ),
                )
                val result = client.generateFromScreenshot("enter my PIN", ByteArray(100))
                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals(ActionType.TAP, response.action?.actionType)
                assertEquals("T4", response.tier)
            }

        @Test
        fun should_returnError_when_noEngine_multimodal() =
            runTest {
                val result = client.generateFromScreenshot("enter PIN", ByteArray(100))
                assertTrue(result is NeuronResult.Error)
            }

        @Test
        fun should_handleTypeAction_onSensitiveScreen() =
            runTest {
                client.initialize(
                    mockEngine(
                        ready = true,
                        multimodal = true,
                        imageOutput = """type(value="1234", target_id="com.bank:id/pin_input", reasoning="Entering PIN")""",
                    ),
                )
                val result = client.generateFromScreenshot("type my PIN 1234", ByteArray(100))
                assertTrue(result is NeuronResult.Success)
                assertEquals(ActionType.TYPE, (result as NeuronResult.Success).data.action?.actionType)
            }
    }

    // Helper to create mock engines
    private fun mockEngine(
        ready: Boolean = true,
        multimodal: Boolean = false,
        output: String = """done(reasoning="Done")""",
        imageOutput: String = output,
    ): Gemma3nClient.InferenceEngine =
        object : Gemma3nClient.InferenceEngine {
            override suspend fun generate(prompt: String) = output

            override suspend fun generateWithImage(
                prompt: String,
                imageBytes: ByteArray,
            ) = imageOutput

            override fun isReady() = ready

            override fun supportsMultimodal() = multimodal
        }
}
