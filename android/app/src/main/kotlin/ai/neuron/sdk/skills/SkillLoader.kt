package ai.neuron.sdk.skills

import ai.neuron.sdk.NeuronTool
import ai.neuron.sdk.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads SKILL.md files from skill directories and registers their tools
 * with the central ToolRegistry.
 *
 * Skill scopes (searched in order):
 * 1. Bundled — shipped with the app (assets/skills/)
 * 2. Global — user-installed (~/.neuron/skills/)
 * 3. Workspace — per-project (./neuron-skills/)
 */
@Singleton
class SkillLoader
    @Inject
    constructor(
        private val toolRegistry: ToolRegistry,
        private val validator: SkillValidator,
    ) {
        private val _loadedSkills = mutableMapOf<String, LoadedSkill>()
        val loadedSkills: Map<String, LoadedSkill> get() = _loadedSkills.toMap()

        data class LoadedSkill(
            val manifest: SkillManifest,
            val tools: List<NeuronTool>,
            val source: String,
        )

        data class LoadResult(
            val loaded: List<String>,
            val errors: Map<String, List<String>>,
        )

        /**
         * Load a skill from its SKILL.md content.
         * @param content Raw SKILL.md file content
         * @param source Descriptive source path (e.g., "bundled/open-app")
         * @return skill name on success, null on failure
         */
        fun loadFromContent(
            content: String,
            source: String = "inline",
        ): String? {
            val manifest = SkillManifest.parse(content) ?: return null

            val validation = validator.validate(manifest)
            if (!validation.valid) return null

            // Already loaded?
            if (_loadedSkills.containsKey(manifest.name)) return null

            // Convert skill tools to NeuronTools and register
            val neuronTools =
                manifest.tools.map { toolDef ->
                    NeuronTool(
                        name = "${manifest.name}:${toolDef.name}",
                        description = "[${manifest.name}] ${toolDef.description}",
                        parameters = toolDef.parameters,
                        execute = { params ->
                            // Default execution: return parameters as confirmation
                            "Skill '${manifest.name}' tool '${toolDef.name}' called with: $params"
                        },
                    )
                }

            for (tool in neuronTools) {
                try {
                    toolRegistry.register(tool)
                } catch (_: IllegalArgumentException) {
                    // Tool already registered — skip
                }
            }

            _loadedSkills[manifest.name] = LoadedSkill(manifest, neuronTools, source)
            return manifest.name
        }

        /**
         * Load multiple skills from a list of (content, source) pairs.
         */
        fun loadAll(skills: List<Pair<String, String>>): LoadResult {
            val loaded = mutableListOf<String>()
            val errors = mutableMapOf<String, List<String>>()

            for ((content, source) in skills) {
                val manifest = SkillManifest.parse(content)
                if (manifest == null) {
                    errors[source] = listOf("Failed to parse SKILL.md")
                    continue
                }

                val validation = validator.validate(manifest)
                if (!validation.valid) {
                    errors[manifest.name] = validation.errors
                    continue
                }

                val name = loadFromContent(content, source)
                if (name != null) {
                    loaded.add(name)
                }
            }

            return LoadResult(loaded, errors)
        }

        /**
         * Unload a skill by name. Unregisters its tools.
         */
        fun unload(skillName: String): Boolean {
            val loaded = _loadedSkills.remove(skillName) ?: return false
            for (tool in loaded.tools) {
                toolRegistry.unregister(tool.name)
            }
            return true
        }

        /**
         * Get all tool names from loaded skills, for prompt injection.
         */
        fun getLoadedToolNames(): List<String> = _loadedSkills.values.flatMap { it.tools.map { t -> t.name } }
    }
