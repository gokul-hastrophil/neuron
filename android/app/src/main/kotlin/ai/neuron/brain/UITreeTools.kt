package ai.neuron.brain

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RLM-inspired tool-based UITree access.
 *
 * Instead of dumping the entire UI tree into LLM context (often 2000+ tokens),
 * the LLM calls these tools on-demand to query specific parts of the tree (~200 tokens
 * per call). This reduces context usage by 60-80% for multi-step tasks.
 *
 * Each method returns a compact list of [UINodeSummary] that includes only the
 * fields relevant for action selection.
 */
@Singleton
class UITreeTools
    @Inject
    constructor() {
        /**
         * Compact representation of a UI node for LLM consumption.
         * Only includes fields needed for action selection — no deep children.
         */
        data class UINodeSummary(
            val id: String,
            val text: String?,
            val desc: String?,
            val clickable: Boolean,
            val editable: Boolean,
            val scrollable: Boolean,
            val bounds: String?,
        ) {
            fun toPromptLine(): String {
                val label = text ?: desc ?: "(no label)"
                val flags =
                    buildList {
                        if (clickable) add("tap")
                        if (editable) add("edit")
                        if (scrollable) add("scroll")
                    }.joinToString(",")
                val boundsStr = bounds ?: ""
                return "[${id.ifEmpty { "?" }}] $label [$flags] $boundsStr"
            }
        }

        /**
         * Get all clickable (interactive) nodes in the tree.
         * This is the primary tool — gives the LLM the list of things it can tap.
         */
        fun getClickableNodes(tree: UITree): List<UINodeSummary> {
            return flattenTree(tree.nodes).filter { it.clickable || it.editable }
                .map { it.toSummary() }
        }

        /**
         * Search nodes by text content (case-insensitive substring match).
         */
        fun getNodeByText(
            tree: UITree,
            query: String,
        ): List<UINodeSummary> {
            val lowerQuery = query.lowercase()
            return flattenTree(tree.nodes).filter { node ->
                node.text?.lowercase()?.contains(lowerQuery) == true ||
                    node.desc?.lowercase()?.contains(lowerQuery) == true
            }.map { it.toSummary() }
        }

        /**
         * Get direct children of a node identified by resource ID.
         * Returns empty list if node not found.
         */
        fun getNodeChildren(
            tree: UITree,
            parentId: String,
        ): List<UINodeSummary> {
            val parent = findNodeById(tree.nodes, parentId) ?: return emptyList()
            return parent.children.map { it.toSummary() }
        }

        /**
         * Search nodes by regex pattern against text, desc, or id.
         */
        fun searchNodes(
            tree: UITree,
            pattern: String,
        ): List<UINodeSummary> {
            val regex =
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE)
                } catch (_: Exception) {
                    return emptyList()
                }
            return flattenTree(tree.nodes).filter { node ->
                regex.containsMatchIn(node.text.orEmpty()) ||
                    regex.containsMatchIn(node.desc.orEmpty()) ||
                    regex.containsMatchIn(node.id)
            }.map { it.toSummary() }
        }

        /**
         * Get a compact summary of the screen: package name, node count,
         * count of clickable/editable/scrollable nodes, and top-level node labels.
         */
        fun getScreenSummary(tree: UITree): String {
            val allNodes = flattenTree(tree.nodes)
            val clickable = allNodes.count { it.clickable }
            val editable = allNodes.count { it.editable }
            val scrollable = allNodes.count { it.scrollable }

            val topLabels =
                tree.nodes.take(10).mapNotNull { node ->
                    node.text ?: node.desc
                }.take(5)

            return buildString {
                appendLine("Package: ${tree.packageName}")
                appendLine("Nodes: ${allNodes.size} (clickable=$clickable, editable=$editable, scrollable=$scrollable)")
                if (topLabels.isNotEmpty()) {
                    appendLine("Top labels: ${topLabels.joinToString(", ")}")
                }
            }.trim()
        }

        /**
         * Format tool definitions for injection into LLM system prompt.
         * These replace the raw UI tree dump.
         */
        fun toPromptSnippet(): String =
            """
            Available UI tools (call these instead of reading raw UI tree):
            - get_clickable_nodes(): Returns all tappable/editable elements with IDs and labels
            - get_node_by_text(query): Find nodes containing text (case-insensitive)
            - get_node_children(parent_id): Get children of a specific node
            - search_nodes(pattern): Regex search across text, desc, and id
            - get_screen_summary(): Package name, node counts, top-level labels
            """.trimIndent()

        private fun flattenTree(nodes: List<UINode>): List<UINode> {
            return nodes.flatMap { node ->
                listOf(node) + flattenTree(node.children)
            }
        }

        private fun findNodeById(
            nodes: List<UINode>,
            id: String,
        ): UINode? {
            for (node in nodes) {
                if (node.id == id) return node
                val found = findNodeById(node.children, id)
                if (found != null) return found
            }
            return null
        }

        private fun UINode.toSummary(): UINodeSummary =
            UINodeSummary(
                id = id,
                text = text,
                desc = desc,
                clickable = clickable,
                editable = editable,
                scrollable = scrollable,
                bounds = bounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" },
            )
    }
