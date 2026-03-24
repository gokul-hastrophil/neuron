package ai.neuron.accessibility

import ai.neuron.accessibility.model.UINode
import ai.neuron.accessibility.model.UITree
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UITreeReaderTest {
    private lateinit var mockService: NeuronAccessibilityService
    private lateinit var uiTreeReader: UITreeReader

    @BeforeEach
    fun setup() {
        mockService = mockk(relaxed = true)
        uiTreeReader = UITreeReader(mockService)
    }

    // --- Null / Empty cases ---

    @Test
    fun should_returnEmptyTree_when_rootIsNull() {
        every { mockService.rootInActiveWindow } returns null

        val result = uiTreeReader.getUITree()

        assertTrue(result.nodes.isEmpty())
    }

    @Test
    fun should_returnSingleNode_when_rootHasNoChildren() {
        val mockRoot =
            createMockNode(
                resourceId = "android:id/content",
                text = "Hello",
                visible = true,
                clickable = true,
                childCount = 0,
            )
        every { mockService.rootInActiveWindow } returns mockRoot

        val result = uiTreeReader.getUITree()

        assertEquals(1, result.nodes.size)
        assertEquals("android:id/content", result.nodes[0].id)
        assertEquals("Hello", result.nodes[0].text)
    }

    // --- Pruning: invisible nodes ---

    @Test
    fun should_pruneInvisibleNodes_when_treeContainsInvisibleElements() {
        val invisibleChild =
            createMockNode(
                resourceId = "hidden_node",
                visible = false,
                clickable = false,
                childCount = 0,
            )
        val visibleChild =
            createMockNode(
                resourceId = "visible_node",
                text = "Visible",
                visible = true,
                clickable = true,
                childCount = 0,
            )
        val root =
            createMockNode(
                resourceId = "root",
                visible = true,
                clickable = false,
                childCount = 2,
                children = listOf(invisibleChild, visibleChild),
            )
        every { mockService.rootInActiveWindow } returns root

        val result = uiTreeReader.getUITree()

        val allIds = flattenIds(result.nodes)
        assertFalse(allIds.contains("hidden_node"), "Invisible node should be pruned")
        assertTrue(allIds.contains("visible_node"), "Visible node should be kept")
    }

    // --- Pruning: non-interactive leaves ---

    @Test
    fun should_pruneNonInteractiveLeaves_when_leafIsNotClickableOrEditable() {
        val nonInteractiveLeaf =
            createMockNode(
                resourceId = "decoration",
                visible = true,
                clickable = false,
                scrollable = false,
                editable = false,
                text = null,
                contentDescription = null,
                childCount = 0,
            )
        val interactiveLeaf =
            createMockNode(
                resourceId = "button",
                visible = true,
                clickable = true,
                text = "Click me",
                childCount = 0,
            )
        val root =
            createMockNode(
                resourceId = "root",
                visible = true,
                clickable = false,
                childCount = 2,
                children = listOf(nonInteractiveLeaf, interactiveLeaf),
            )
        every { mockService.rootInActiveWindow } returns root

        val result = uiTreeReader.getUITree()

        val allIds = flattenIds(result.nodes)
        assertFalse(allIds.contains("decoration"), "Non-interactive leaf should be pruned")
        assertTrue(allIds.contains("button"), "Interactive leaf should be kept")
    }

    @Test
    fun should_keepNonInteractiveLeaf_when_itHasText() {
        val textNode =
            createMockNode(
                resourceId = "label",
                visible = true,
                clickable = false,
                text = "Important label",
                childCount = 0,
            )
        val root =
            createMockNode(
                resourceId = "root",
                visible = true,
                clickable = false,
                childCount = 1,
                children = listOf(textNode),
            )
        every { mockService.rootInActiveWindow } returns root

        val result = uiTreeReader.getUITree()

        val allIds = flattenIds(result.nodes)
        assertTrue(allIds.contains("label"), "Node with text should be kept even if not interactive")
    }

    // --- Depth limit ---

    @Test
    fun should_stopTraversing_when_depthLimitReached() {
        // Create a chain 20 levels deep; with maxDepth=15, bottom nodes should be pruned
        val deepReader = UITreeReader(mockService, maxDepth = 3)
        val deepChild = createMockNode(resourceId = "level3", visible = true, clickable = true, childCount = 0)
        val midChild =
            createMockNode(
                resourceId = "level2",
                visible = true,
                clickable = true,
                childCount = 1,
                children = listOf(deepChild),
            )
        val topChild =
            createMockNode(
                resourceId = "level1",
                visible = true,
                clickable = false,
                childCount = 1,
                children = listOf(midChild),
            )
        val root =
            createMockNode(
                resourceId = "level0",
                visible = true,
                clickable = false,
                childCount = 1,
                children = listOf(topChild),
            )
        every { mockService.rootInActiveWindow } returns root

        val result = deepReader.getUITree()

        val allIds = flattenIds(result.nodes)
        // depth 0=level0, 1=level1, 2=level2, 3=level3 — level3 is at maxDepth, should be excluded
        assertFalse(allIds.contains("level3"), "Nodes at maxDepth should be pruned")
        assertTrue(allIds.contains("level2"), "Nodes within maxDepth should be kept")
    }

    // --- JSON serialization ---

    @Test
    fun should_serializeTreeAsValidJson_when_complexTree() {
        val child =
            createMockNode(
                resourceId = "com.whatsapp:id/send_btn",
                text = "Send",
                visible = true,
                clickable = true,
                childCount = 0,
            )
        val root =
            createMockNode(
                resourceId = "android:id/content",
                visible = true,
                clickable = false,
                childCount = 1,
                children = listOf(child),
            )
        every { mockService.rootInActiveWindow } returns root
        every { root.packageName } returns "com.whatsapp"

        val result = uiTreeReader.getUITree()
        val json = result.toJson()

        // Should be parseable JSON
        val parsed = Json.decodeFromString<UITree>(json)
        assertNotNull(parsed)
        assertTrue(json.contains("com.whatsapp:id/send_btn"))
        assertTrue(json.contains("Send"))
    }

    // --- Password field detection ---

    @Test
    fun should_markPasswordField_when_nodeIsPassword() {
        val passwordNode =
            createMockNode(
                resourceId = "password_input",
                visible = true,
                clickable = true,
                editable = true,
                isPassword = true,
                childCount = 0,
            )
        val root =
            createMockNode(
                resourceId = "root",
                visible = true,
                clickable = false,
                childCount = 1,
                children = listOf(passwordNode),
            )
        every { mockService.rootInActiveWindow } returns root

        val result = uiTreeReader.getUITree()

        val passwordUINode = flattenNodes(result.nodes).find { it.id == "password_input" }
        assertNotNull(passwordUINode, "Password node should be in tree")
        assertTrue(passwordUINode!!.password, "Password field should be marked")
    }

    // --- Package name capture ---

    @Test
    fun should_capturePackageName_when_rootAvailable() {
        val root =
            createMockNode(
                resourceId = "root",
                visible = true,
                clickable = false,
                childCount = 0,
            )
        every { mockService.rootInActiveWindow } returns root
        every { root.packageName } returns "com.google.android.apps.maps"

        val result = uiTreeReader.getUITree()

        assertEquals("com.google.android.apps.maps", result.packageName)
    }

    // --- Helpers ---

    private fun createMockNode(
        resourceId: String? = null,
        text: CharSequence? = null,
        contentDescription: CharSequence? = null,
        visible: Boolean = true,
        clickable: Boolean = false,
        scrollable: Boolean = false,
        editable: Boolean = false,
        isPassword: Boolean = false,
        childCount: Int = 0,
        children: List<AccessibilityNodeInfo> = emptyList(),
        className: CharSequence? = "android.widget.FrameLayout",
    ): AccessibilityNodeInfo {
        val node = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { node.viewIdResourceName } returns resourceId
        every { node.text } returns text
        every { node.contentDescription } returns contentDescription
        every { node.isVisibleToUser } returns visible
        every { node.isClickable } returns clickable
        every { node.isScrollable } returns scrollable
        every { node.isEditable } returns editable
        every { node.isPassword } returns isPassword
        every { node.childCount } returns childCount
        every { node.className } returns className
        every { node.packageName } returns "com.test.app"

        val rect = Rect(0, 0, 100, 50)
        every { node.getBoundsInScreen(any()) } answers {
            val r = firstArg<Rect>()
            r.set(rect)
        }

        children.forEachIndexed { index, child ->
            every { node.getChild(index) } returns child
        }
        // Return null for out-of-bounds children
        for (i in children.size until childCount) {
            every { node.getChild(i) } returns null
        }

        return node
    }

    private fun flattenNodes(nodes: List<UINode>): List<UINode> {
        return nodes.flatMap { listOf(it) + flattenNodes(it.children) }
    }

    private fun flattenIds(nodes: List<UINode>): List<String> {
        return flattenNodes(nodes).map { it.id }
    }
}
