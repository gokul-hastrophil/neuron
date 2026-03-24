package ai.neuron.brain

import ai.neuron.accessibility.model.Bounds
import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UITreeToolsTest {
    private lateinit var tools: UITreeTools

    private val sampleTree =
        UITree(
            packageName = "com.example.app",
            nodes =
                listOf(
                    UINode(
                        id = "toolbar",
                        text = "My App",
                        clickable = false,
                        children =
                            listOf(
                                UINode(id = "btn_back", text = "Back", clickable = true, bounds = Bounds(0, 0, 100, 50)),
                                UINode(id = "btn_menu", desc = "Menu", clickable = true, bounds = Bounds(300, 0, 400, 50)),
                            ),
                    ),
                    UINode(
                        id = "content",
                        clickable = false,
                        children =
                            listOf(
                                UINode(id = "search_input", text = "Search...", editable = true, clickable = true),
                                UINode(id = "item_1", text = "Calculator", clickable = true),
                                UINode(id = "item_2", text = "Calendar", clickable = true),
                                UINode(id = "item_3", text = "Camera", clickable = true),
                                UINode(
                                    id = "scroll_view",
                                    scrollable = true,
                                    children =
                                        listOf(
                                            UINode(id = "item_4", text = "Settings", clickable = true),
                                            UINode(id = "label_info", text = "Version 1.0"),
                                        ),
                                ),
                            ),
                    ),
                ),
        )

    @BeforeEach
    fun setup() {
        tools = UITreeTools()
    }

    @Nested
    @DisplayName("getClickableNodes")
    inner class GetClickableNodes {
        @Test
        fun should_returnOnlyClickableAndEditableNodes() {
            val clickable = tools.getClickableNodes(sampleTree)

            // btn_back, btn_menu, search_input, item_1, item_2, item_3, item_4
            assertEquals(7, clickable.size)
            assertTrue(clickable.all { it.clickable || it.editable })
        }

        @Test
        fun should_includeEditableNodes() {
            val clickable = tools.getClickableNodes(sampleTree)
            val editable = clickable.filter { it.editable }
            assertEquals(1, editable.size)
            assertEquals("search_input", editable.first().id)
        }

        @Test
        fun should_returnEmpty_when_noClickableNodes() {
            val staticTree =
                UITree(
                    packageName = "com.static",
                    nodes = listOf(UINode(id = "label", text = "Hello")),
                )
            val result = tools.getClickableNodes(staticTree)
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("getNodeByText")
    inner class GetNodeByText {
        @Test
        fun should_findByTextCaseInsensitive() {
            val results = tools.getNodeByText(sampleTree, "camera")
            assertEquals(1, results.size)
            assertEquals("item_3", results.first().id)
        }

        @Test
        fun should_findByContentDescription() {
            val results = tools.getNodeByText(sampleTree, "menu")
            assertEquals(1, results.size)
            assertEquals("btn_menu", results.first().id)
        }

        @Test
        fun should_findMultipleMatches() {
            val results = tools.getNodeByText(sampleTree, "cal")
            // Calculator and Calendar both contain "cal"
            assertEquals(2, results.size)
        }

        @Test
        fun should_returnEmpty_when_noMatch() {
            val results = tools.getNodeByText(sampleTree, "nonexistent")
            assertTrue(results.isEmpty())
        }
    }

    @Nested
    @DisplayName("getNodeChildren")
    inner class GetNodeChildren {
        @Test
        fun should_returnDirectChildren() {
            val children = tools.getNodeChildren(sampleTree, "toolbar")
            assertEquals(2, children.size)
            assertEquals("btn_back", children[0].id)
            assertEquals("btn_menu", children[1].id)
        }

        @Test
        fun should_returnEmpty_when_nodeNotFound() {
            val children = tools.getNodeChildren(sampleTree, "nonexistent_id")
            assertTrue(children.isEmpty())
        }

        @Test
        fun should_returnEmpty_when_nodeHasNoChildren() {
            val children = tools.getNodeChildren(sampleTree, "item_1")
            assertTrue(children.isEmpty())
        }
    }

    @Nested
    @DisplayName("searchNodes")
    inner class SearchNodes {
        @Test
        fun should_findByRegexPattern() {
            val results = tools.searchNodes(sampleTree, "Cal.*")
            // Calculator, Calendar
            assertEquals(2, results.size)
        }

        @Test
        fun should_matchAgainstId() {
            val results = tools.searchNodes(sampleTree, "btn_.*")
            assertEquals(2, results.size)
        }

        @Test
        fun should_returnEmpty_when_invalidRegex() {
            val results = tools.searchNodes(sampleTree, "[invalid")
            assertTrue(results.isEmpty())
        }
    }

    @Nested
    @DisplayName("getScreenSummary")
    inner class GetScreenSummary {
        @Test
        fun should_includePackageName() {
            val summary = tools.getScreenSummary(sampleTree)
            assertTrue(summary.contains("com.example.app"))
        }

        @Test
        fun should_includeNodeCounts() {
            val summary = tools.getScreenSummary(sampleTree)
            assertTrue(summary.contains("clickable="))
            assertTrue(summary.contains("editable="))
        }

        @Test
        fun should_includeTopLabels() {
            val summary = tools.getScreenSummary(sampleTree)
            assertTrue(summary.contains("My App"))
        }
    }

    @Nested
    @DisplayName("toPromptSnippet")
    inner class PromptSnippet {
        @Test
        fun should_listAllTools() {
            val snippet = tools.toPromptSnippet()
            assertTrue(snippet.contains("get_clickable_nodes"))
            assertTrue(snippet.contains("get_node_by_text"))
            assertTrue(snippet.contains("get_node_children"))
            assertTrue(snippet.contains("search_nodes"))
            assertTrue(snippet.contains("get_screen_summary"))
        }
    }

    @Nested
    @DisplayName("UINodeSummary.toPromptLine")
    inner class PromptLine {
        @Test
        fun should_formatClickableNode() {
            val summary =
                UITreeTools.UINodeSummary(
                    id = "btn_ok",
                    text = "OK",
                    desc = null,
                    clickable = true,
                    editable = false,
                    scrollable = false,
                    bounds = "100,200,200,250",
                )
            val line = summary.toPromptLine()
            assertEquals("[btn_ok] OK [tap] 100,200,200,250", line)
        }

        @Test
        fun should_useDescWhenNoText() {
            val summary =
                UITreeTools.UINodeSummary(
                    id = "btn",
                    text = null,
                    desc = "Close",
                    clickable = true,
                    editable = false,
                    scrollable = false,
                    bounds = null,
                )
            val line = summary.toPromptLine()
            assertEquals("[btn] Close [tap] ", line)
        }

        @Test
        fun should_showMultipleFlags() {
            val summary =
                UITreeTools.UINodeSummary(
                    id = "field",
                    text = "Name",
                    desc = null,
                    clickable = true,
                    editable = true,
                    scrollable = false,
                    bounds = null,
                )
            val line = summary.toPromptLine()
            assertTrue(line.contains("tap,edit"))
        }
    }
}
