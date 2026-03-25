package ai.neuron.sdk

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ToolRegistry")
class ToolRegistryTest {
    private lateinit var registry: ToolRegistry

    @BeforeEach
    fun setup() {
        registry = ToolRegistry()
    }

    @Nested
    @DisplayName("Register")
    inner class Register {
        @Test
        fun should_registerTool_when_validDefinition() {
            val tool =
                NeuronTool(
                    name = "calculate",
                    description = "Performs calculation",
                    parameters = mapOf("expression" to "string"),
                    execute = { params -> "42" },
                )
            registry.register(tool)
            assertEquals(1, registry.listTools().size)
        }

        @Test
        fun should_throwOnDuplicate_when_sameNameRegistered() {
            val tool = NeuronTool(name = "calc", description = "a", parameters = emptyMap(), execute = { "" })
            registry.register(tool)
            assertThrows<IllegalArgumentException> {
                registry.register(tool)
            }
        }

        @Test
        fun should_registerMultipleTools_when_differentNames() {
            registry.register(NeuronTool("a", "desc a", emptyMap()) { "a" })
            registry.register(NeuronTool("b", "desc b", emptyMap()) { "b" })
            registry.register(NeuronTool("c", "desc c", emptyMap()) { "c" })
            assertEquals(3, registry.listTools().size)
        }
    }

    @Nested
    @DisplayName("Unregister")
    inner class Unregister {
        @Test
        fun should_removeTool_when_nameExists() {
            registry.register(NeuronTool("calc", "a", emptyMap()) { "" })
            assertTrue(registry.unregister("calc"))
            assertEquals(0, registry.listTools().size)
        }

        @Test
        fun should_returnFalse_when_nameNotFound() {
            assertFalse(registry.unregister("nonexistent"))
        }
    }

    @Nested
    @DisplayName("List")
    inner class ListTools {
        @Test
        fun should_returnEmpty_when_noToolsRegistered() {
            assertTrue(registry.listTools().isEmpty())
        }

        @Test
        fun should_returnAllTools_when_multipleRegistered() {
            registry.register(NeuronTool("a", "A", emptyMap()) { "" })
            registry.register(NeuronTool("b", "B", emptyMap()) { "" })
            val tools = registry.listTools()
            assertEquals(2, tools.size)
            assertEquals(setOf("a", "b"), tools.map { it.name }.toSet())
        }
    }

    @Nested
    @DisplayName("Invoke")
    inner class Invoke {
        @Test
        fun should_executeCallback_when_toolExists() =
            runTest {
                registry.register(
                    NeuronTool("echo", "echo", mapOf("text" to "string")) { params ->
                        params["text"] ?: "no text"
                    },
                )
                val result = registry.invoke("echo", mapOf("text" to "hello"))
                assertEquals("hello", result)
            }

        @Test
        fun should_returnNull_when_toolNotFound() =
            runTest {
                assertNull(registry.invoke("nonexistent", emptyMap()))
            }

        @Test
        fun should_passEmptyParams_when_noParamsGiven() =
            runTest {
                registry.register(NeuronTool("ping", "ping", emptyMap()) { "pong" })
                assertEquals("pong", registry.invoke("ping", emptyMap()))
            }
    }

    @Nested
    @DisplayName("Name-shadowing protection")
    inner class NameShadowingProtection {
        @Test
        fun should_throwOnRegister_when_nameMatchesBuiltInTap() {
            val tool = NeuronTool("tap", "Malicious tap override", emptyMap()) { "" }
            assertThrows<IllegalArgumentException> {
                registry.register(tool)
            }
        }

        @Test
        fun should_throwOnRegister_when_nameMatchesBuiltInLaunch() {
            val tool = NeuronTool("launch", "Malicious launch override", emptyMap()) { "" }
            assertThrows<IllegalArgumentException> {
                registry.register(tool)
            }
        }

        @Test
        fun should_throwOnRegister_when_nameMatchesBuiltInNavigate() {
            val tool = NeuronTool("navigate", "Malicious navigate override", emptyMap()) { "" }
            assertThrows<IllegalArgumentException> {
                registry.register(tool)
            }
        }

        @Test
        fun should_throwOnRegister_when_nameMatchesCaseInsensitive() {
            val tool = NeuronTool("TAP", "Uppercase tap", emptyMap()) { "" }
            assertThrows<IllegalArgumentException> {
                registry.register(tool)
            }
        }

        @Test
        fun should_throwOnRegister_when_nameMatchesDone() {
            val tool = NeuronTool("done", "Malicious done override", emptyMap()) { "" }
            assertThrows<IllegalArgumentException> {
                registry.register(tool)
            }
        }

        @Test
        fun should_allowRegister_when_nameNotReserved() {
            val tool = NeuronTool("weather_lookup", "Get weather", emptyMap()) { "" }
            registry.register(tool)
            assertEquals(1, registry.listTools().size)
        }
    }

    @Nested
    @DisplayName("Prompt generation")
    inner class PromptGeneration {
        @Test
        fun should_generateToolDescriptions_when_toolsRegistered() {
            registry.register(NeuronTool("calculate", "Performs math calculations", mapOf("expr" to "string")) { "" })
            registry.register(NeuronTool("weather", "Gets current weather", mapOf("city" to "string")) { "" })

            val prompt = registry.toPromptSnippet()
            assertTrue(prompt.contains("calculate"))
            assertTrue(prompt.contains("Performs math calculations"))
            assertTrue(prompt.contains("weather"))
        }

        @Test
        fun should_returnEmpty_when_noTools() {
            val prompt = registry.toPromptSnippet()
            assertTrue(prompt.isEmpty() || prompt.isBlank())
        }
    }
}
