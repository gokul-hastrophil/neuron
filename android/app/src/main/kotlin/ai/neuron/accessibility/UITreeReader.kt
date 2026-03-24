package ai.neuron.accessibility

import ai.neuron.accessibility.model.Bounds
import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class UITreeReader(
    private val service: NeuronAccessibilityService,
    private val maxDepth: Int = MAX_DEPTH,
) {
    fun getUITree(): UITree {
        val root = service.rootInActiveWindow ?: return UITree.empty()
        val packageName = root.packageName?.toString().orEmpty()

        return try {
            val nodes = traverseAndPrune(root, depth = 0)
            val tree =
                UITree(
                    nodes = nodes,
                    packageName = packageName,
                )
            Log.d(TAG, "UITree captured: ${flatCount(nodes)} nodes from $packageName")
            tree
        } finally {
            root.recycle()
        }
    }

    private fun traverseAndPrune(
        node: AccessibilityNodeInfo,
        depth: Int,
    ): List<UINode> {
        if (!node.isVisibleToUser) return emptyList()
        if (depth >= maxDepth) return emptyList()

        val children = mutableListOf<UINode>()
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                children.addAll(traverseAndPrune(child, depth + 1))
            } finally {
                child.recycle()
            }
        }

        val uiNode = nodeToUINode(node, children)

        // Prune non-interactive leaf nodes with no text and no content description
        if (shouldPrune(uiNode)) return children // promote children, drop this node

        return listOf(uiNode)
    }

    private fun nodeToUINode(
        node: AccessibilityNodeInfo,
        children: List<UINode>,
    ): UINode {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        return UINode(
            id = node.viewIdResourceName.orEmpty(),
            text = node.text?.toString(),
            desc = node.contentDescription?.toString(),
            className = node.className?.toString(),
            bounds = Bounds(left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom),
            clickable = node.isClickable,
            scrollable = node.isScrollable,
            editable = node.isEditable,
            password = node.isPassword,
            visible = node.isVisibleToUser,
            children = children,
        )
    }

    private fun shouldPrune(node: UINode): Boolean {
        // Keep interactive nodes
        if (node.clickable || node.scrollable || node.editable) return false
        // Keep nodes with meaningful text
        if (!node.text.isNullOrBlank()) return false
        // Keep nodes with content description
        if (!node.desc.isNullOrBlank()) return false
        // Keep password fields
        if (node.password) return false
        // Keep nodes with children (they're structural)
        if (node.children.isNotEmpty()) return false
        // Prune everything else (decorative, empty leaves)
        return true
    }

    private fun flatCount(nodes: List<UINode>): Int {
        return nodes.sumOf { 1 + flatCount(it.children) }
    }

    companion object {
        private const val TAG = "NeuronUITree"
        const val MAX_DEPTH = 15
    }
}
