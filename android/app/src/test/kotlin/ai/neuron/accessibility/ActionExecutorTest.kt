package ai.neuron.accessibility

import ai.neuron.accessibility.model.ActionResult
import ai.neuron.accessibility.model.GlobalActionType
import ai.neuron.accessibility.model.NeuronAction
import ai.neuron.accessibility.model.ScrollDirection
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ActionExecutorTest {
    private lateinit var mockService: NeuronAccessibilityService
    private lateinit var mockGestureFactory: ActionExecutor.GestureFactory
    private lateinit var executor: ActionExecutor

    @BeforeEach
    fun setup() {
        mockService = mockk(relaxed = true)
        mockGestureFactory = mockk(relaxed = true)
        executor = ActionExecutor(mockService, mockGestureFactory)
    }

    // --- Tap by Node ID ---

    @Nested
    inner class TapByNodeId {
        @Test
        fun should_returnSuccess_when_nodeFoundAndClicked() {
            val targetNode = createMockNode(resourceId = "com.app:id/button", clickable = true)
            every { targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true
            stubFindNode(targetNode)

            val result = executor.execute(NeuronAction.Tap(nodeId = "com.app:id/button"))

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        }

        @Test
        fun should_returnError_when_nodeNotFound() {
            stubFindNodeReturnsNull()

            val result = executor.execute(NeuronAction.Tap(nodeId = "nonexistent"))

            assertInstanceOf(ActionResult.Error::class.java, result)
            val error = result as ActionResult.Error
            assertTrue(error.message.contains("not found", ignoreCase = true))
        }

        @Test
        fun should_clickNearestClickableAncestor_when_nodeNotClickable() {
            val nonClickableNode =
                createMockNode(
                    resourceId = "com.app:id/label",
                    clickable = false,
                )
            val clickableParent =
                createMockNode(
                    resourceId = "com.app:id/row",
                    clickable = true,
                )
            every { nonClickableNode.parent } returns clickableParent
            every { clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true
            stubFindNode(nonClickableNode)

            val result = executor.execute(NeuronAction.Tap(nodeId = "com.app:id/label"))

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        }

        @Test
        fun should_returnError_when_nodeAndAncestorsNotClickable() {
            val nonClickableNode =
                createMockNode(
                    resourceId = "com.app:id/decoration",
                    clickable = false,
                )
            every { nonClickableNode.parent } returns null
            stubFindNode(nonClickableNode)

            val result = executor.execute(NeuronAction.Tap(nodeId = "com.app:id/decoration"))

            assertInstanceOf(ActionResult.Error::class.java, result)
            val error = result as ActionResult.Error
            assertTrue(error.message.contains("not clickable", ignoreCase = true))
        }
    }

    // --- Tap by Coordinate ---

    @Nested
    inner class TapByCoordinate {
        @Test
        fun should_returnSuccess_when_gestureDispatched() {
            val mockGesture = mockk<GestureDescription>()
            every { mockGestureFactory.createTap(500f, 800f, any()) } returns mockGesture
            every { mockService.dispatchGesture(mockGesture, null, null) } returns true

            val result = executor.execute(NeuronAction.TapCoordinate(x = 500, y = 800))

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { mockService.dispatchGesture(mockGesture, null, null) }
        }

        @Test
        fun should_returnError_when_gestureDispatchFails() {
            val mockGesture = mockk<GestureDescription>()
            every { mockGestureFactory.createTap(500f, 800f, any()) } returns mockGesture
            every { mockService.dispatchGesture(mockGesture, null, null) } returns false

            val result = executor.execute(NeuronAction.TapCoordinate(x = 500, y = 800))

            assertInstanceOf(ActionResult.Error::class.java, result)
        }
    }

    // --- Type Text ---

    @Nested
    inner class TypeText {
        @Test
        fun should_returnSuccess_when_textSetOnEditableNode() {
            val editableNode =
                createMockNode(
                    resourceId = "com.app:id/input",
                    editable = true,
                )
            stubFindNode(editableNode)
            every { editableNode.performAction(any(), any()) } returns true

            val result =
                executor.execute(
                    NeuronAction.TypeText(nodeId = "com.app:id/input", text = "Hello World"),
                )

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify {
                editableNode.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    any(),
                )
            }
        }

        @Test
        fun should_returnError_when_nodeNotEditable() {
            val nonEditableNode =
                createMockNode(
                    resourceId = "com.app:id/label",
                    editable = false,
                )
            stubFindNode(nonEditableNode)

            val result =
                executor.execute(
                    NeuronAction.TypeText(nodeId = "com.app:id/label", text = "test"),
                )

            assertInstanceOf(ActionResult.Error::class.java, result)
            val error = result as ActionResult.Error
            assertTrue(error.message.contains("not editable", ignoreCase = true))
        }

        @Test
        fun should_returnError_when_targetNodeNotFound() {
            stubFindNodeReturnsNull()

            val result =
                executor.execute(
                    NeuronAction.TypeText(nodeId = "missing", text = "test"),
                )

            assertInstanceOf(ActionResult.Error::class.java, result)
        }
    }

    // --- Swipe ---

    @Nested
    inner class SwipeAction {
        @Test
        fun should_returnSuccess_when_swipeGestureDispatched() {
            val mockGesture = mockk<GestureDescription>()
            every {
                mockGestureFactory.createSwipe(500f, 1500f, 500f, 500f, 300L)
            } returns mockGesture
            every { mockService.dispatchGesture(mockGesture, null, null) } returns true

            val result =
                executor.execute(
                    NeuronAction.Swipe(startX = 500, startY = 1500, endX = 500, endY = 500),
                )

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { mockService.dispatchGesture(mockGesture, null, null) }
        }

        @Test
        fun should_returnError_when_swipeDispatchFails() {
            val mockGesture = mockk<GestureDescription>()
            every {
                mockGestureFactory.createSwipe(500f, 1500f, 500f, 500f, 300L)
            } returns mockGesture
            every { mockService.dispatchGesture(mockGesture, null, null) } returns false

            val result =
                executor.execute(
                    NeuronAction.Swipe(startX = 500, startY = 1500, endX = 500, endY = 500),
                )

            assertInstanceOf(ActionResult.Error::class.java, result)
        }
    }

    // --- Scroll ---

    @Nested
    inner class ScrollAction {
        @Test
        fun should_returnSuccess_when_scrollForwardPerformed() {
            val scrollableNode =
                createMockNode(
                    resourceId = "com.app:id/list",
                    scrollable = true,
                )
            stubFindNode(scrollableNode)
            every {
                scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            } returns true

            val result =
                executor.execute(
                    NeuronAction.Scroll(nodeId = "com.app:id/list", direction = ScrollDirection.DOWN),
                )

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
        }

        @Test
        fun should_scrollBackward_when_directionIsUp() {
            val scrollableNode =
                createMockNode(
                    resourceId = "com.app:id/list",
                    scrollable = true,
                )
            stubFindNode(scrollableNode)
            every {
                scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            } returns true

            val result =
                executor.execute(
                    NeuronAction.Scroll(nodeId = "com.app:id/list", direction = ScrollDirection.UP),
                )

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        }

        @Test
        fun should_returnError_when_nodeNotScrollable() {
            val nonScrollableNode =
                createMockNode(
                    resourceId = "com.app:id/text",
                    scrollable = false,
                )
            stubFindNode(nonScrollableNode)

            val result =
                executor.execute(
                    NeuronAction.Scroll(nodeId = "com.app:id/text", direction = ScrollDirection.DOWN),
                )

            assertInstanceOf(ActionResult.Error::class.java, result)
        }

        @Test
        fun should_returnError_when_scrollNodeNotFound() {
            stubFindNodeReturnsNull()

            val result =
                executor.execute(
                    NeuronAction.Scroll(nodeId = "missing", direction = ScrollDirection.DOWN),
                )

            assertInstanceOf(ActionResult.Error::class.java, result)
        }
    }

    // --- Long Press ---

    @Nested
    inner class LongPressAction {
        @Test
        fun should_returnSuccess_when_longClickPerformed() {
            val node = createMockNode(resourceId = "com.app:id/item", clickable = true)
            stubFindNode(node)
            every { node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) } returns true

            val result = executor.execute(NeuronAction.LongPress(nodeId = "com.app:id/item"))

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) }
        }

        @Test
        fun should_returnError_when_nodeNotFound() {
            stubFindNodeReturnsNull()

            val result = executor.execute(NeuronAction.LongPress(nodeId = "missing"))

            assertInstanceOf(ActionResult.Error::class.java, result)
        }
    }

    // --- Global Actions ---

    @Nested
    inner class GlobalActions {
        @Test
        fun should_performHome_when_homeActionRequested() {
            every {
                mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            } returns true

            val result =
                executor.execute(
                    NeuronAction.GlobalAction(action = GlobalActionType.HOME),
                )

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
        }

        @Test
        fun should_performBack_when_backActionRequested() {
            every {
                mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } returns true

            val result =
                executor.execute(
                    NeuronAction.GlobalAction(action = GlobalActionType.BACK),
                )

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        }

        @Test
        fun should_performRecents_when_recentsActionRequested() {
            every {
                mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            } returns true

            val result =
                executor.execute(
                    NeuronAction.GlobalAction(action = GlobalActionType.RECENTS),
                )

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify { mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }
        }

        @Test
        fun should_performNotifications_when_notificationsActionRequested() {
            every {
                mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            } returns true

            val result =
                executor.execute(
                    NeuronAction.GlobalAction(action = GlobalActionType.NOTIFICATIONS),
                )

            assertInstanceOf(ActionResult.Success::class.java, result)
            verify {
                mockService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }
        }

        @Test
        fun should_returnError_when_globalActionFails() {
            every { mockService.performGlobalAction(any()) } returns false

            val result =
                executor.execute(
                    NeuronAction.GlobalAction(action = GlobalActionType.HOME),
                )

            assertInstanceOf(ActionResult.Error::class.java, result)
        }
    }

    // --- Helpers ---

    private fun stubFindNode(node: AccessibilityNodeInfo) {
        val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockService.rootInActiveWindow } returns rootNode
        val nodeId = node.viewIdResourceName
        every { rootNode.findAccessibilityNodeInfosByViewId(nodeId!!) } returns listOf(node)
    }

    private fun stubFindNodeReturnsNull() {
        val rootNode = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { mockService.rootInActiveWindow } returns rootNode
        every { rootNode.findAccessibilityNodeInfosByViewId(any()) } returns emptyList()
    }

    private fun createMockNode(
        resourceId: String? = null,
        clickable: Boolean = false,
        scrollable: Boolean = false,
        editable: Boolean = false,
    ): AccessibilityNodeInfo {
        val node = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { node.viewIdResourceName } returns resourceId
        every { node.isClickable } returns clickable
        every { node.isScrollable } returns scrollable
        every { node.isEditable } returns editable
        every { node.parent } returns null

        val rect = Rect(0, 0, 100, 50)
        every { node.getBoundsInScreen(any()) } answers {
            val r = firstArg<Rect>()
            r.set(rect)
        }

        return node
    }
}
