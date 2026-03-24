package ai.neuron.sdk.skills

import ai.neuron.sdk.ToolRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SkillSystemTest {
    companion object {
        val VALID_SKILL =
            """
            ---
            name: open-app
            version: 1.0.0
            description: Launch any app by name
            author: Neuron Team
            triggers:
              - "open *"
              - "launch *"
            permissions:
              - LAUNCH_APP
            tools:
              - name: launch_app
                description: Launch an app by name
                parameters:
                  app_name: The name of the app to launch
            ---
            # Open App Skill
            This skill launches any installed app by name.
            """.trimIndent()

        val MULTI_TOOL_SKILL =
            """
            ---
            name: browser-helper
            version: 2.0.0
            description: Browser automation helpers
            triggers:
              - "search for *"
            permissions:
              - LAUNCH_APP
              - TAP_ELEMENT
              - TYPE_TEXT
            tools:
              - name: search_web
                description: Search the web for a query
                parameters:
                  query: The search query
              - name: open_url
                description: Open a URL in browser
                parameters:
                  url: The URL to open
            ---
            """.trimIndent()

        val INVALID_SKILL_NO_NAME =
            """
            ---
            version: 1.0.0
            description: Missing name
            tools:
              - name: foo
                description: A tool
            ---
            """.trimIndent()

        val INVALID_SKILL_BAD_VERSION =
            """
            ---
            name: bad-version
            version: one.two
            description: Bad version format
            tools:
              - name: foo
                description: A tool
            ---
            """.trimIndent()

        val INVALID_SKILL_BAD_PERMISSION =
            """
            ---
            name: bad-perm
            version: 1.0.0
            description: Unknown permission
            permissions:
              - HACK_PHONE
            tools:
              - name: foo
                description: A tool
            ---
            """.trimIndent()
    }

    @Nested
    @DisplayName("SkillManifest parsing")
    inner class ManifestParsing {
        @Test
        fun should_parseValidSkillManifest() {
            val manifest = SkillManifest.parse(VALID_SKILL)
            assertNotNull(manifest)
            assertEquals("open-app", manifest!!.name)
            assertEquals("1.0.0", manifest.version)
            assertEquals("Launch any app by name", manifest.description)
            assertEquals("Neuron Team", manifest.author)
        }

        @Test
        fun should_parseTriggers() {
            val manifest = SkillManifest.parse(VALID_SKILL)!!
            assertEquals(2, manifest.triggers.size)
            assertTrue(manifest.triggers.contains("open *"))
            assertTrue(manifest.triggers.contains("launch *"))
        }

        @Test
        fun should_parsePermissions() {
            val manifest = SkillManifest.parse(VALID_SKILL)!!
            assertEquals(1, manifest.permissions.size)
            assertEquals("LAUNCH_APP", manifest.permissions[0])
        }

        @Test
        fun should_parseTools() {
            val manifest = SkillManifest.parse(VALID_SKILL)!!
            assertEquals(1, manifest.tools.size)
            assertEquals("launch_app", manifest.tools[0].name)
            assertEquals("Launch an app by name", manifest.tools[0].description)
            assertTrue(manifest.tools[0].parameters.containsKey("app_name"))
        }

        @Test
        fun should_parseMultipleTools() {
            val manifest = SkillManifest.parse(MULTI_TOOL_SKILL)!!
            assertEquals(2, manifest.tools.size)
            assertEquals("search_web", manifest.tools[0].name)
            assertEquals("open_url", manifest.tools[1].name)
        }

        @Test
        fun should_returnNull_when_noFrontmatter() {
            assertNull(SkillManifest.parse("No frontmatter here"))
        }

        @Test
        fun should_returnNull_when_missingRequiredFields() {
            assertNull(SkillManifest.parse(INVALID_SKILL_NO_NAME))
        }

        @Test
        fun should_returnNull_when_noClosingDelimiter() {
            assertNull(SkillManifest.parse("---\nname: test\nversion: 1.0.0"))
        }
    }

    @Nested
    @DisplayName("SkillValidator")
    inner class ValidatorTests {
        private lateinit var validator: SkillValidator

        @BeforeEach
        fun setup() {
            validator = SkillValidator()
        }

        @Test
        fun should_acceptValidManifest() {
            val manifest = SkillManifest.parse(VALID_SKILL)!!
            val result = validator.validate(manifest)
            assertTrue(result.valid)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        fun should_rejectBadVersionFormat() {
            val manifest = SkillManifest.parse(INVALID_SKILL_BAD_VERSION)!!
            val result = validator.validate(manifest)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("semver") })
        }

        @Test
        fun should_rejectUnknownPermission() {
            val manifest = SkillManifest.parse(INVALID_SKILL_BAD_PERMISSION)!!
            val result = validator.validate(manifest)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("Unknown permission") })
        }

        @Test
        fun should_rejectSkillWithNoToolsOrTriggers() {
            val manifest =
                SkillManifest(
                    name = "empty-skill",
                    version = "1.0.0",
                    description = "No tools or triggers",
                )
            val result = validator.validate(manifest)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("at least one tool or trigger") })
        }

        @Test
        fun should_rejectUppercaseName() {
            val manifest =
                SkillManifest(
                    name = "OpenApp",
                    version = "1.0.0",
                    description = "Bad name",
                    triggers = listOf("open *"),
                )
            val result = validator.validate(manifest)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("lowercase") })
        }

        @Test
        fun should_acceptMultiplePermissions() {
            val manifest = SkillManifest.parse(MULTI_TOOL_SKILL)!!
            val result = validator.validate(manifest)
            assertTrue(result.valid)
        }
    }

    @Nested
    @DisplayName("SkillLoader")
    inner class LoaderTests {
        private lateinit var toolRegistry: ToolRegistry
        private lateinit var loader: SkillLoader

        @BeforeEach
        fun setup() {
            toolRegistry = ToolRegistry()
            loader = SkillLoader(toolRegistry, SkillValidator())
        }

        @Test
        fun should_loadValidSkill() {
            val name = loader.loadFromContent(VALID_SKILL, "bundled/open-app")
            assertEquals("open-app", name)
            assertEquals(1, loader.loadedSkills.size)
        }

        @Test
        fun should_registerToolsWithRegistry() {
            loader.loadFromContent(VALID_SKILL, "bundled/open-app")
            val tools = toolRegistry.listTools()
            assertTrue(tools.any { it.name == "open-app:launch_app" })
        }

        @Test
        fun should_returnNull_when_invalidSkill() {
            val name = loader.loadFromContent("not a skill", "invalid")
            assertNull(name)
        }

        @Test
        fun should_returnNull_when_duplicateSkill() {
            loader.loadFromContent(VALID_SKILL, "first")
            val second = loader.loadFromContent(VALID_SKILL, "second")
            assertNull(second)
        }

        @Test
        fun should_loadMultipleSkills() {
            val result =
                loader.loadAll(
                    listOf(
                        VALID_SKILL to "bundled/open-app",
                        MULTI_TOOL_SKILL to "bundled/browser-helper",
                    ),
                )
            assertEquals(2, result.loaded.size)
            assertEquals(0, result.errors.size)
        }

        @Test
        fun should_reportErrors_when_invalidSkillsInBatch() {
            val result =
                loader.loadAll(
                    listOf(
                        VALID_SKILL to "valid",
                        INVALID_SKILL_BAD_PERMISSION to "invalid",
                    ),
                )
            assertEquals(1, result.loaded.size)
            assertEquals(1, result.errors.size)
        }

        @Test
        fun should_unloadSkill() {
            loader.loadFromContent(VALID_SKILL, "test")
            assertTrue(loader.unload("open-app"))
            assertEquals(0, loader.loadedSkills.size)
            // Tools should be unregistered
            assertFalse(toolRegistry.listTools().any { it.name.startsWith("open-app:") })
        }

        @Test
        fun should_returnFalse_when_unloadingUnknownSkill() {
            assertFalse(loader.unload("nonexistent"))
        }

        @Test
        fun should_getLoadedToolNames() {
            loader.loadFromContent(VALID_SKILL, "test")
            val names = loader.getLoadedToolNames()
            assertEquals(1, names.size)
            assertEquals("open-app:launch_app", names[0])
        }

        @Test
        fun should_namespaceToolsWithSkillName() {
            loader.loadFromContent(MULTI_TOOL_SKILL, "test")
            val names = loader.getLoadedToolNames()
            assertTrue(names.all { it.startsWith("browser-helper:") })
        }

        @Test
        fun should_showToolsInPromptSnippet() {
            loader.loadFromContent(VALID_SKILL, "test")
            val snippet = toolRegistry.toPromptSnippet()
            assertTrue(snippet.contains("open-app:launch_app"))
        }
    }
}
