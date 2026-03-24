package ai.neuron.sdk.skills

/**
 * Data model for a SKILL.md manifest. Parsed from YAML frontmatter.
 *
 * SKILL.md format:
 * ```
 * ---
 * name: open-app
 * version: 1.0.0
 * description: Launch any app by name
 * author: Neuron Team
 * triggers:
 *   - "open *"
 *   - "launch *"
 * permissions:
 *   - LAUNCH_APP
 * tools:
 *   - name: launch_app
 *     description: Launch an app by name
 *     parameters:
 *       app_name: The name of the app to launch
 * ---
 * # Open App Skill
 * This skill launches any installed app by name.
 * ```
 */
data class SkillManifest(
    val name: String,
    val version: String,
    val description: String,
    val author: String = "",
    val triggers: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val tools: List<SkillToolDefinition> = emptyList(),
) {
    data class SkillToolDefinition(
        val name: String,
        val description: String,
        val parameters: Map<String, String> = emptyMap(),
    )

    companion object {
        /**
         * Parse a SKILL.md file content into a SkillManifest.
         * Expects YAML frontmatter between --- delimiters.
         */
        fun parse(content: String): SkillManifest? {
            val trimmed = content.trim()
            if (!trimmed.startsWith("---")) return null

            val endIndex = trimmed.indexOf("---", 3)
            if (endIndex == -1) return null

            val yaml = trimmed.substring(3, endIndex).trim()
            return parseYamlFrontmatter(yaml)
        }

        private fun parseYamlFrontmatter(yaml: String): SkillManifest? {
            val fields = mutableMapOf<String, String>()
            val triggers = mutableListOf<String>()
            val permissions = mutableListOf<String>()
            val tools = mutableListOf<SkillToolDefinition>()

            var currentList: MutableList<String>? = null
            var currentToolName = ""
            var currentToolDesc = ""
            var currentToolParams = mutableMapOf<String, String>()
            var inToolParams = false
            var inTools = false

            for (line in yaml.lines()) {
                val trimmedLine = line.trim()

                // Top-level key: value
                if (!line.startsWith(" ") && !line.startsWith("\t") && trimmedLine.contains(":") && !trimmedLine.startsWith("-")) {
                    // Flush any current tool
                    if (inTools && currentToolName.isNotBlank()) {
                        tools.add(SkillToolDefinition(currentToolName, currentToolDesc, currentToolParams.toMap()))
                        currentToolName = ""
                        currentToolDesc = ""
                        currentToolParams = mutableMapOf()
                        inToolParams = false
                    }

                    val colonIdx = trimmedLine.indexOf(':')
                    val key = trimmedLine.substring(0, colonIdx).trim()
                    val value = trimmedLine.substring(colonIdx + 1).trim()

                    inTools = key == "tools"
                    currentList =
                        when (key) {
                            "triggers" -> triggers
                            "permissions" -> permissions
                            else -> null
                        }
                    if (value.isNotBlank() && currentList == null && !inTools) {
                        fields[key] = value
                    }
                    continue
                }

                // List item: - value
                if (trimmedLine.startsWith("- ") && !inTools) {
                    val value =
                        trimmedLine.removePrefix("- ").trim()
                            .removeSurrounding("\"").removeSurrounding("'")
                    currentList?.add(value)
                    continue
                }

                // Tools section
                if (inTools) {
                    if (trimmedLine.startsWith("- name:")) {
                        // Flush previous tool
                        if (currentToolName.isNotBlank()) {
                            tools.add(SkillToolDefinition(currentToolName, currentToolDesc, currentToolParams.toMap()))
                            currentToolParams = mutableMapOf()
                            inToolParams = false
                        }
                        currentToolName = trimmedLine.removePrefix("- name:").trim()
                        currentToolDesc = ""
                        continue
                    }
                    if (trimmedLine.startsWith("description:")) {
                        currentToolDesc = trimmedLine.removePrefix("description:").trim()
                        continue
                    }
                    if (trimmedLine == "parameters:") {
                        inToolParams = true
                        continue
                    }
                    if (inToolParams && trimmedLine.contains(":")) {
                        val colonIdx = trimmedLine.indexOf(':')
                        val paramName = trimmedLine.substring(0, colonIdx).trim()
                        val paramDesc = trimmedLine.substring(colonIdx + 1).trim()
                        currentToolParams[paramName] = paramDesc
                        continue
                    }
                }
            }

            // Flush last tool
            if (inTools && currentToolName.isNotBlank()) {
                tools.add(SkillToolDefinition(currentToolName, currentToolDesc, currentToolParams.toMap()))
            }

            val name = fields["name"] ?: return null
            val version = fields["version"] ?: return null
            val description = fields["description"] ?: return null

            return SkillManifest(
                name = name,
                version = version,
                description = description,
                author = fields["author"] ?: "",
                triggers = triggers,
                permissions = permissions,
                tools = tools,
            )
        }
    }
}
