package ai.neuron.accessibility

import ai.neuron.accessibility.model.ActionResult
import ai.neuron.accessibility.model.GlobalActionType
import ai.neuron.accessibility.model.NeuronAction
import ai.neuron.accessibility.model.ScrollDirection
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class ActionExecutor(
    private val service: NeuronAccessibilityService,
    private val gestureFactory: GestureFactory = DefaultGestureFactory(),
) {

    interface GestureFactory {
        fun createTap(x: Float, y: Float, durationMs: Long): GestureDescription
        fun createSwipe(
            startX: Float, startY: Float,
            endX: Float, endY: Float,
            durationMs: Long,
        ): GestureDescription
    }

    class DefaultGestureFactory : GestureFactory {
        override fun createTap(x: Float, y: Float, durationMs: Long): GestureDescription {
            val path = Path().apply { moveTo(x, y) }
            return GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
        }

        override fun createSwipe(
            startX: Float, startY: Float,
            endX: Float, endY: Float,
            durationMs: Long,
        ): GestureDescription {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            return GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
        }
    }

    fun execute(action: NeuronAction): ActionResult {
        Log.d(TAG, "Executing action: $action")
        return when (action) {
            is NeuronAction.Tap -> executeTap(action)
            is NeuronAction.TapCoordinate -> executeTapCoordinate(action)
            is NeuronAction.TypeText -> executeTypeText(action)
            is NeuronAction.Swipe -> executeSwipe(action)
            is NeuronAction.Scroll -> executeScroll(action)
            is NeuronAction.LongPress -> executeLongPress(action)
            is NeuronAction.LaunchApp -> executeLaunchApp(action)
            is NeuronAction.GlobalAction -> executeGlobalAction(action)
        }
    }

    private fun executeTap(action: NeuronAction.Tap): ActionResult {
        val node = findNodeById(action.nodeId)
            ?: return ActionResult.Error(action, "Node '${action.nodeId}' not found")

        val clickTarget = findClickableNode(node)
            ?: return ActionResult.Error(action, "Node '${action.nodeId}' is not clickable and has no clickable ancestor")

        return try {
            clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ActionResult.Success(action)
        } finally {
            recycleNodes(node, clickTarget)
        }
    }

    private fun executeTapCoordinate(action: NeuronAction.TapCoordinate): ActionResult {
        val gesture = gestureFactory.createTap(
            action.x.toFloat(), action.y.toFloat(), TAP_DURATION_MS,
        )
        val dispatched = service.dispatchGesture(gesture, null, null)
        return if (dispatched) {
            ActionResult.Success(action)
        } else {
            ActionResult.Error(action, "Failed to dispatch tap gesture at (${action.x}, ${action.y})")
        }
    }

    private fun executeTypeText(action: NeuronAction.TypeText): ActionResult {
        val node = findNodeById(action.nodeId)
            ?: return ActionResult.Error(action, "Node '${action.nodeId}' not found")

        return try {
            if (!node.isEditable) {
                return ActionResult.Error(action, "Node '${action.nodeId}' is not editable")
            }

            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    action.text,
                )
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            ActionResult.Success(action)
        } finally {
            node.recycle()
        }
    }

    private fun executeSwipe(action: NeuronAction.Swipe): ActionResult {
        val gesture = gestureFactory.createSwipe(
            action.startX.toFloat(), action.startY.toFloat(),
            action.endX.toFloat(), action.endY.toFloat(),
            action.durationMs,
        )
        val dispatched = service.dispatchGesture(gesture, null, null)
        return if (dispatched) {
            ActionResult.Success(action)
        } else {
            ActionResult.Error(action, "Failed to dispatch swipe gesture")
        }
    }

    private fun executeScroll(action: NeuronAction.Scroll): ActionResult {
        val node = findNodeById(action.nodeId)
            ?: return ActionResult.Error(action, "Node '${action.nodeId}' not found")

        return try {
            if (!node.isScrollable) {
                return ActionResult.Error(action, "Node '${action.nodeId}' is not scrollable")
            }

            val scrollAction = when (action.direction) {
                ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            node.performAction(scrollAction)
            ActionResult.Success(action)
        } finally {
            node.recycle()
        }
    }

    private fun executeLongPress(action: NeuronAction.LongPress): ActionResult {
        val node = findNodeById(action.nodeId)
            ?: return ActionResult.Error(action, "Node '${action.nodeId}' not found")

        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            ActionResult.Success(action)
        } finally {
            node.recycle()
        }
    }

    private fun executeLaunchApp(action: NeuronAction.LaunchApp): ActionResult {
        val context = service.applicationContext
        val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return ActionResult.Error(action, "No launch intent for package '${action.packageName}'")

        return try {
            context.startActivity(intent)
            ActionResult.Success(action)
        } catch (e: Exception) {
            ActionResult.Error(action, "Failed to launch '${action.packageName}': ${e.message}", e)
        }
    }

    private fun executeGlobalAction(action: NeuronAction.GlobalAction): ActionResult {
        val globalActionId = when (action.action) {
            GlobalActionType.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalActionType.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalActionType.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalActionType.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            GlobalActionType.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        }

        val success = service.performGlobalAction(globalActionId)
        return if (success) {
            ActionResult.Success(action)
        } else {
            ActionResult.Error(action, "Global action ${action.action} failed")
        }
    }

    private fun findNodeById(nodeId: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            val nodes = root.findAccessibilityNodeInfosByViewId(nodeId)
            nodes.firstOrNull()
        } finally {
            root.recycle()
        }
    }

    private fun findClickableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var current = node.parent
        while (current != null) {
            if (current.isClickable) return current
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return null
    }

    private fun recycleNodes(vararg nodes: AccessibilityNodeInfo?) {
        nodes.forEach { it?.recycle() }
    }

    companion object {
        private const val TAG = "NeuronAction"
        private const val TAP_DURATION_MS = 50L
    }
}
