package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ScreenExtractorTest {
    private lateinit var extractor: ScreenExtractor
    private lateinit var sensitivityGate: SensitivityGate
    private lateinit var context: CrossAppContext

    private lateinit var promptSanitizer: PromptSanitizer

    @BeforeEach
    fun setup() {
        sensitivityGate = SensitivityGate()
        promptSanitizer = PromptSanitizer()
        extractor = ScreenExtractor(sensitivityGate)
        context = CrossAppContext(promptSanitizer, sensitivityGate)
    }

    @Nested
    @DisplayName("Normal extraction")
    inner class NormalExtraction {
        @Test
        fun should_extractTextFromNodes_when_normalApp() {
            val tree =
                UITree(
                    packageName = "com.whatsapp",
                    nodes =
                        listOf(
                            UINode(id = "title", text = "John Doe"),
                            UINode(id = "message", text = "Hello, how are you?"),
                        ),
                )

            val count = extractor.extractAndStore(tree, context)
            assertEquals(2, count)
            assertTrue(context.getAllValues().values.any { it == "John Doe" })
            assertTrue(context.getAllValues().values.any { it == "Hello, how are you?" })
        }

        @Test
        fun should_extractDescriptions_when_present() {
            val tree =
                UITree(
                    packageName = "com.google.android.gm",
                    nodes =
                        listOf(
                            UINode(id = "btn_reply", desc = "Reply to email"),
                        ),
                )

            val count = extractor.extractAndStore(tree, context)
            assertEquals(1, count)
            assertTrue(context.getAllValues().values.any { it == "Reply to email" })
        }

        @Test
        fun should_recordAppSwitch_when_extracted() {
            val tree = UITree(packageName = "com.chrome", nodes = listOf(UINode(id = "url", text = "https://example.com")))
            extractor.extractAndStore(tree, context)
            assertEquals(listOf("com.chrome"), context.getAppSequence())
        }

        @Test
        fun should_extractFromNestedChildren_when_present() {
            val tree =
                UITree(
                    packageName = "com.app",
                    nodes =
                        listOf(
                            UINode(
                                id = "parent",
                                text = "Parent text",
                                children =
                                    listOf(
                                        UINode(id = "child", text = "Child text"),
                                    ),
                            ),
                        ),
                )

            val count = extractor.extractAndStore(tree, context)
            assertEquals(2, count)
        }
    }

    @Nested
    @DisplayName("Sensitive screen protection")
    inner class SensitiveScreens {
        @Test
        fun should_extractZero_when_bankingApp() {
            val tree =
                UITree(
                    packageName = "net.one97.paytm",
                    nodes = listOf(UINode(id = "balance", text = "₹50,000")),
                )

            val count = extractor.extractAndStore(tree, context)
            assertEquals(0, count)
            assertTrue(context.getAllValues().isEmpty())
        }

        @Test
        fun should_extractZero_when_passwordFieldPresent() {
            val tree =
                UITree(
                    packageName = "com.someapp",
                    nodes =
                        listOf(
                            UINode(id = "user", text = "user@email.com"),
                            UINode(id = "pass", text = "secret", password = true),
                        ),
                )

            val count = extractor.extractAndStore(tree, context)
            assertEquals(0, count)
        }

        @Test
        fun should_extractZero_when_passwordManagerApp() {
            val tree =
                UITree(
                    packageName = "com.agilebits.onepassword",
                    nodes = listOf(UINode(id = "entry", text = "My Login")),
                )

            val count = extractor.extractAndStore(tree, context)
            assertEquals(0, count)
        }
    }

    @Nested
    @DisplayName("Password field skipping")
    inner class PasswordSkipping {
        @Test
        fun should_skipPasswordNodes_when_passwordFlagSet() {
            // Non-sensitive app, but individual password nodes should be skipped
            val tree =
                UITree(
                    packageName = "com.normalapp",
                    nodes =
                        listOf(
                            UINode(id = "username", text = "john_doe"),
                            UINode(id = "password", text = "hidden", password = true),
                            UINode(id = "submit", text = "Login"),
                        ),
                )

            // This will be blocked by SensitivityGate because password field is present
            // The extractor itself also skips password nodes as a defense-in-depth measure
            val count = extractor.extractAndStore(tree, context)
            assertEquals(0, count) // SensitivityGate blocks the entire screen
        }
    }

    @Nested
    @DisplayName("Limits")
    inner class Limits {
        @Test
        fun should_limitExtraction_when_tooManyNodes() {
            val nodes =
                (0 until 50).map { i ->
                    UINode(id = "node_$i", text = "Text content $i")
                }
            val tree = UITree(packageName = "com.bigapp", nodes = nodes)

            val count = extractor.extractAndStore(tree, context)
            assertEquals(ScreenExtractor.MAX_EXTRACTED_TEXTS, count)
        }

        @Test
        fun should_skipShortText_when_lessThanMinLength() {
            val tree =
                UITree(
                    packageName = "com.app",
                    nodes =
                        listOf(
                            // "x" is below MIN_TEXT_LENGTH, should be skipped
                            UINode(id = "a", text = "x"),
                            UINode(id = "b", text = "Hello World"),
                        ),
                )

            val count = extractor.extractAndStore(tree, context)
            assertEquals(1, count)
        }

        @Test
        fun should_returnZero_when_emptyTree() {
            val tree = UITree(packageName = "com.app", nodes = emptyList())
            val count = extractor.extractAndStore(tree, context)
            assertEquals(0, count)
        }
    }
}
