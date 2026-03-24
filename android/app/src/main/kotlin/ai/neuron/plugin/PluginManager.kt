package ai.neuron.plugin

import ai.neuron.sdk.NeuronTool
import ai.neuron.sdk.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages NeuronPlugin lifecycle: registration, loading, unloading.
 * Plugins are loaded at startup and can be added/removed at runtime.
 */
@Singleton
class PluginManager
    @Inject
    constructor(
        private val toolRegistry: ToolRegistry,
    ) {
        private val _plugins = mutableMapOf<String, PluginEntry>()
        val plugins: Map<String, PluginEntry> get() = _plugins.toMap()

        data class PluginEntry(
            val plugin: NeuronPlugin,
            var loaded: Boolean = false,
            val tools: MutableList<NeuronTool> = mutableListOf(),
        )

        /**
         * Register a plugin. Does not load it yet.
         * @return true if registered (false if duplicate ID)
         */
        fun register(plugin: NeuronPlugin): Boolean {
            if (_plugins.containsKey(plugin.id)) return false
            _plugins[plugin.id] = PluginEntry(plugin)
            return true
        }

        /**
         * Unregister and unload a plugin by ID.
         * @return true if found and removed
         */
        fun unregister(pluginId: String): Boolean {
            val entry = _plugins.remove(pluginId) ?: return false
            if (entry.loaded) {
                unloadPlugin(entry)
            }
            return true
        }

        /**
         * Load all registered plugins. Safe to call multiple times —
         * already-loaded plugins are skipped.
         */
        fun loadAll(): List<String> {
            val loaded = mutableListOf<String>()
            for ((id, entry) in _plugins) {
                if (!entry.loaded) {
                    if (loadPlugin(entry)) {
                        loaded.add(id)
                    }
                }
            }
            return loaded
        }

        /**
         * Load a single plugin by ID.
         * @return true if loaded successfully
         */
        fun load(pluginId: String): Boolean {
            val entry = _plugins[pluginId] ?: return false
            if (entry.loaded) return true
            return loadPlugin(entry)
        }

        /**
         * Unload a single plugin by ID.
         * @return true if unloaded successfully
         */
        fun unload(pluginId: String): Boolean {
            val entry = _plugins[pluginId] ?: return false
            if (!entry.loaded) return true
            unloadPlugin(entry)
            return true
        }

        /**
         * Get all tools from all loaded plugins.
         */
        fun getAllTools(): List<NeuronTool> = _plugins.values.filter { it.loaded }.flatMap { it.tools }

        /**
         * Check if a plugin is loaded.
         */
        fun isLoaded(pluginId: String): Boolean = _plugins[pluginId]?.loaded == true

        private fun loadPlugin(entry: PluginEntry): Boolean {
            return try {
                val context = NeuronContext(toolRegistry)
                entry.plugin.onLoad(context)
                val tools = entry.plugin.getTools()
                entry.tools.clear()
                entry.tools.addAll(tools)
                // Register tools with central ToolRegistry
                for (tool in tools) {
                    toolRegistry.register(tool)
                }
                entry.loaded = true
                true
            } catch (e: Exception) {
                // Error isolation: one plugin failure doesn't crash the system
                entry.loaded = false
                false
            }
        }

        private fun unloadPlugin(entry: PluginEntry) {
            try {
                // Unregister tools
                for (tool in entry.tools) {
                    toolRegistry.unregister(tool.name)
                }
                entry.plugin.onUnload()
            } catch (_: Exception) {
                // Best-effort cleanup
            }
            entry.tools.clear()
            entry.loaded = false
        }
    }
