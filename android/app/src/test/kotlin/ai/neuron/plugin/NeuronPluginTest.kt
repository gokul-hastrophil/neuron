package ai.neuron.plugin

import ai.neuron.sdk.NeuronTool
import ai.neuron.sdk.ToolRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NeuronPluginTest {
    private lateinit var toolRegistry: ToolRegistry

    @BeforeEach
    fun setup() {
        toolRegistry = ToolRegistry()
    }

    @Nested
    @DisplayName("NeuronPlugin interface contract")
    inner class PluginContract {
        @Test
        fun should_haveIdAndVersion() {
            val plugin = CharacterPlugin()
            assertTrue(plugin.id.isNotBlank())
            assertTrue(plugin.version.isNotBlank())
            assertTrue(plugin.displayName.isNotBlank())
        }

        @Test
        fun should_loadAndProvideTools() {
            val plugin = CharacterPlugin()
            val context = NeuronContext(toolRegistry)
            plugin.onLoad(context)
            val tools = plugin.getTools()
            assertTrue(tools.isNotEmpty())
        }

        @Test
        fun should_cleanUpOnUnload() {
            val plugin = CharacterPlugin()
            val context = NeuronContext(toolRegistry)
            plugin.onLoad(context)
            assertTrue(plugin.isLoaded())
            plugin.onUnload()
            assertFalse(plugin.isLoaded())
        }

        @Test
        fun should_returnSkillIds() {
            val plugin = CharacterPlugin()
            val context = NeuronContext(toolRegistry)
            plugin.onLoad(context)
            assertTrue(plugin.getSkillIds().isNotEmpty())
        }
    }

    @Nested
    @DisplayName("PluginManager lifecycle")
    inner class PluginManagerLifecycle {
        private lateinit var manager: PluginManager

        @BeforeEach
        fun setup() {
            manager = PluginManager(toolRegistry)
        }

        @Test
        fun should_registerPlugin() {
            val plugin = CharacterPlugin()
            assertTrue(manager.register(plugin))
            assertEquals(1, manager.plugins.size)
        }

        @Test
        fun should_rejectDuplicateRegistration() {
            val plugin1 = CharacterPlugin()
            val plugin2 = CharacterPlugin()
            assertTrue(manager.register(plugin1))
            assertFalse(manager.register(plugin2))
        }

        @Test
        fun should_loadPlugin() {
            val plugin = CharacterPlugin()
            manager.register(plugin)
            assertTrue(manager.load(plugin.id))
            assertTrue(manager.isLoaded(plugin.id))
        }

        @Test
        fun should_loadAllPlugins() {
            val plugin = CharacterPlugin()
            manager.register(plugin)
            val loaded = manager.loadAll()
            assertEquals(1, loaded.size)
            assertEquals(plugin.id, loaded[0])
        }

        @Test
        fun should_skipAlreadyLoadedPlugins_when_loadAll() {
            val plugin = CharacterPlugin()
            manager.register(plugin)
            manager.loadAll()
            val secondLoad = manager.loadAll()
            assertEquals(0, secondLoad.size)
        }

        @Test
        fun should_unloadPlugin() {
            val plugin = CharacterPlugin()
            manager.register(plugin)
            manager.load(plugin.id)
            assertTrue(manager.isLoaded(plugin.id))
            assertTrue(manager.unload(plugin.id))
            assertFalse(manager.isLoaded(plugin.id))
        }

        @Test
        fun should_unregisterPlugin() {
            val plugin = CharacterPlugin()
            manager.register(plugin)
            manager.load(plugin.id)
            assertTrue(manager.unregister(plugin.id))
            assertFalse(manager.plugins.containsKey(plugin.id))
        }

        @Test
        fun should_registerToolsWithRegistry_when_pluginLoaded() {
            val plugin = CharacterPlugin()
            manager.register(plugin)
            manager.load(plugin.id)
            val tools = toolRegistry.listTools()
            assertTrue(tools.any { it.name == "set_emotion" })
            assertTrue(tools.any { it.name == "get_character_info" })
        }

        @Test
        fun should_unregisterToolsFromRegistry_when_pluginUnloaded() {
            val plugin = CharacterPlugin()
            manager.register(plugin)
            manager.load(plugin.id)
            manager.unload(plugin.id)
            val tools = toolRegistry.listTools()
            assertFalse(tools.any { it.name == "set_emotion" })
            assertFalse(tools.any { it.name == "get_character_info" })
        }

        @Test
        fun should_getAllToolsFromLoadedPlugins() {
            val plugin = CharacterPlugin()
            manager.register(plugin)
            manager.load(plugin.id)
            val allTools = manager.getAllTools()
            assertEquals(2, allTools.size)
        }

        @Test
        fun should_returnFalse_when_loadingUnregisteredPlugin() {
            assertFalse(manager.load("non.existent"))
        }

        @Test
        fun should_returnFalse_when_unregisteringUnknownPlugin() {
            assertFalse(manager.unregister("non.existent"))
        }

        @Test
        fun should_isolateErrors_when_pluginLoadFails() {
            val failingPlugin =
                object : NeuronPlugin {
                    override val id = "failing.plugin"
                    override val version = "1.0.0"
                    override val displayName = "Failing Plugin"

                    override fun onLoad(context: NeuronContext) = throw RuntimeException("Load failed!")

                    override fun onUnload() {}

                    override fun getTools() = emptyList<NeuronTool>()
                }
            val goodPlugin = CharacterPlugin()
            manager.register(failingPlugin)
            manager.register(goodPlugin)
            val loaded = manager.loadAll()
            // Only the good plugin should load
            assertEquals(1, loaded.size)
            assertEquals(goodPlugin.id, loaded[0])
            assertFalse(manager.isLoaded(failingPlugin.id))
            assertTrue(manager.isLoaded(goodPlugin.id))
        }
    }

    @Nested
    @DisplayName("CharacterPlugin specifics")
    inner class CharacterPluginSpecifics {
        @Test
        fun should_exposeSetEmotionTool() {
            val plugin = CharacterPlugin()
            plugin.onLoad(NeuronContext(toolRegistry))
            val tools = plugin.getTools()
            val emotionTool = tools.find { it.name == "set_emotion" }
            assertTrue(emotionTool != null)
            assertTrue(emotionTool!!.parameters.containsKey("emotion"))
        }

        @Test
        fun should_exposeGetCharacterInfoTool() {
            val plugin = CharacterPlugin()
            plugin.onLoad(NeuronContext(toolRegistry))
            val tools = plugin.getTools()
            val infoTool = tools.find { it.name == "get_character_info" }
            assertTrue(infoTool != null)
        }

        @Test
        fun should_haveCorrectPluginId() {
            val plugin = CharacterPlugin()
            assertEquals("neuron.character", plugin.id)
        }
    }
}
