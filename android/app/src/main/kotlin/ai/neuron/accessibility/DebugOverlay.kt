package ai.neuron.accessibility

import ai.neuron.accessibility.model.UINode
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class DebugOverlay(
    private val service: NeuronAccessibilityService,
) {
    private var debugView: DebugBoundsView? = null
    var isEnabled: Boolean = false
        private set

    private val windowManager: WindowManager
        get() = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun toggle() {
        if (isEnabled) hide() else show()
    }

    fun show() {
        if (debugView != null) return
        isEnabled = true
        Log.d(TAG, "Debug overlay enabled")

        val view = DebugBoundsView(service)
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

        try {
            windowManager.addView(view, params)
            debugView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show debug overlay", e)
        }
    }

    fun hide() {
        isEnabled = false
        Log.d(TAG, "Debug overlay disabled")
        debugView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove debug overlay", e)
            }
        }
        debugView = null
    }

    fun updateNodes(nodes: List<UINode>) {
        debugView?.setNodes(nodes)
    }

    class DebugBoundsView(context: Context) : View(context) {
        private var nodes: List<UINode> = emptyList()

        private val clickablePaint =
            Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 3f
                alpha = 180
            }

        private val scrollablePaint =
            Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = 3f
                alpha = 180
            }

        private val editablePaint =
            Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 3f
                alpha = 180
            }

        private val defaultPaint =
            Paint().apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
                alpha = 120
            }

        fun setNodes(newNodes: List<UINode>) {
            nodes = newNodes
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawNodes(canvas, nodes)
        }

        private fun drawNodes(
            canvas: Canvas,
            nodes: List<UINode>,
        ) {
            for (node in nodes) {
                val bounds = node.bounds ?: continue
                val paint =
                    when {
                        node.editable -> editablePaint
                        node.clickable -> clickablePaint
                        node.scrollable -> scrollablePaint
                        else -> defaultPaint
                    }
                canvas.drawRect(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat(),
                    paint,
                )
                drawNodes(canvas, node.children)
            }
        }
    }

    companion object {
        private const val TAG = "NeuronDebugOverlay"
    }
}
