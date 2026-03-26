package ai.neuron.brain.client

import ai.neuron.brain.StructuredToolCallParser
import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMTier
import ai.neuron.brain.model.NeuronResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LlmProxyClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: LlmProxyClient
    private val okHttpClient = OkHttpClient.Builder().build()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        client =
            LlmProxyClient(
                okHttpClient = okHttpClient,
                serverUrl = server.url("/").toString(),
                deviceTokenProvider = { "test-device-token" },
            )
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Nested
    @DisplayName("Successful proxy responses")
    inner class SuccessResponses {
        @Test
        fun should_parseValidActionResponse_when_proxyReturnsSuccess() =
            runTest {
                val responseBody =
                    """
                    {
                        "text": "{\"action_type\": \"tap\", \"target_id\": \"com.app:id/btn\", \"confidence\": 0.95, \"reasoning\": \"Tapping\"}",
                        "model": "gemini-2.5-flash",
                        "tier": "T2",
                        "tokens_used": 150,
                        "latency_ms": 500
                    }
                    """.trimIndent()

                server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

                val result = client.generate(LLMTier.T2, "system prompt", "tap the button")

                assertTrue(result is NeuronResult.Success)
                val response = (result as NeuronResult.Success).data
                assertEquals("T2", response.tier)
                assertEquals("gemini-2.5-flash", response.modelId)
                assertEquals(ActionType.TAP, response.action?.actionType)
                assertEquals("com.app:id/btn", response.action?.targetId)
            }

        @Test
        fun should_sendCorrectRequestFormat_when_generating() =
            runTest {
                val responseBody =
                    """
                    {
                        "text": "{\"action_type\": \"done\", \"reasoning\": \"done\", \"confidence\": 1.0}",
                        "model": "proxy",
                        "tier": "T3"
                    }
                    """.trimIndent()

                server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

                client.generate(LLMTier.T3, "system", "user message")

                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/v1/llm/chat", request.path)
                assertEquals("Bearer test-device-token", request.getHeader("Authorization"))

                val body = request.body.readUtf8()
                assertTrue(body.contains("\"tier\":\"T3\""))
                assertTrue(body.contains("\"role\":\"system\""))
                assertTrue(body.contains("\"role\":\"user\""))
            }

        @Test
        fun should_parseDoneAction_when_proxyReturnsDone() =
            runTest {
                val responseBody =
                    """
                    {
                        "text": "{\"action_type\": \"done\", \"reasoning\": \"Task complete\", \"confidence\": 1.0}",
                        "model": "gemini-2.0-flash",
                        "tier": "T2"
                    }
                    """.trimIndent()

                server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

                val result = client.generate(LLMTier.T2, "system", "check done")

                assertTrue(result is NeuronResult.Success)
                assertEquals(ActionType.DONE, (result as NeuronResult.Success).data.action?.actionType)
            }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {
        @Test
        fun should_returnError_when_deviceTokenBlank() =
            runTest {
                val noTokenClient =
                    LlmProxyClient(
                        okHttpClient = okHttpClient,
                        serverUrl = server.url("/").toString(),
                        deviceTokenProvider = { "" },
                    )

                val result = noTokenClient.generate(LLMTier.T2, "system", "test")

                assertTrue(result is NeuronResult.Error)
                assertTrue((result as NeuronResult.Error).message.contains("Device token"))
            }

        @Test
        fun should_returnError_when_proxyReturnsErrorField() =
            runTest {
                val responseBody =
                    """
                    {
                        "error": "rate limit exceeded"
                    }
                    """.trimIndent()

                server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

                val result = client.generate(LLMTier.T2, "system", "test")

                assertTrue(result is NeuronResult.Error)
                assertTrue((result as NeuronResult.Error).message.contains("rate limit"))
            }

        @Test
        fun should_returnError_when_proxyReturnsEmptyText() =
            runTest {
                val responseBody =
                    """
                    {
                        "model": "test"
                    }
                    """.trimIndent()

                server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

                val result = client.generate(LLMTier.T2, "system", "test")

                assertTrue(result is NeuronResult.Error)
                assertTrue((result as NeuronResult.Error).message.contains("Empty response"))
            }

        @Test
        fun should_returnError_when_proxyReturnsUnparsableText() =
            runTest {
                val responseBody =
                    """
                    {
                        "text": "This is not JSON at all",
                        "model": "test"
                    }
                    """.trimIndent()

                server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

                val result = client.generate(LLMTier.T2, "system", "test")

                assertTrue(result is NeuronResult.Error)
                assertTrue((result as NeuronResult.Error).message.contains("Failed to parse"))
            }

        @Test
        fun should_returnError_when_proxyReturnsHttp500() =
            runTest {
                server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))

                val result = client.generate(LLMTier.T2, "system", "test")

                assertTrue(result is NeuronResult.Error)
            }
    }

    @Nested
    @DisplayName("Server URL configuration")
    inner class ServerUrlConfig {
        @Test
        fun should_updateEndpoint_when_updateServerUrlCalled() =
            runTest {
                val secondServer = MockWebServer()
                secondServer.start()

                val responseBody =
                    """
                    {
                        "text": "{\"action_type\": \"done\", \"reasoning\": \"ok\", \"confidence\": 1.0}",
                        "model": "test"
                    }
                    """.trimIndent()

                secondServer.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

                client.updateServerUrl(secondServer.url("/").toString())
                val result = client.generate(LLMTier.T2, "system", "test")

                assertTrue(result is NeuronResult.Success)
                assertEquals(1, secondServer.requestCount)
                assertEquals(0, server.requestCount)

                secondServer.shutdown()
            }
    }

    @Nested
    @DisplayName("Structured tool call parsing")
    inner class ToolCallParsing {
        @Test
        fun should_useToolCallParser_when_provided() =
            runTest {
                val parser = mockk<StructuredToolCallParser>()
                val clientWithParser =
                    LlmProxyClient(
                        okHttpClient = okHttpClient,
                        toolCallParser = parser,
                        serverUrl = server.url("/").toString(),
                        deviceTokenProvider = { "token" },
                    )

                every { parser.parse(any()) } returns
                    NeuronResult.Success(
                        ai.neuron.brain.model.LLMAction(
                            actionType = ActionType.NAVIGATE,
                            value = "home",
                            reasoning = "Going home",
                            confidence = 1.0,
                        ),
                    )

                val responseBody =
                    """
                    {
                        "text": "{\"name\": \"navigate\", \"args\": {\"value\": \"home\"}}",
                        "model": "test"
                    }
                    """.trimIndent()

                server.enqueue(MockResponse().setBody(responseBody).setResponseCode(200))

                val result = clientWithParser.generate(LLMTier.T2, "system", "go home")

                assertTrue(result is NeuronResult.Success)
                assertEquals(ActionType.NAVIGATE, (result as NeuronResult.Success).data.action?.actionType)
            }
    }
}
