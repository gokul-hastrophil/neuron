# Skill: Android Accessibility Control

## Overview
Complete reference for implementing Android AccessibilityService-based UI control in the Neuron project.

## Service Registration

### AndroidManifest.xml
```xml
<service
    android:name=".accessibility.NeuronAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService"/>
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config"/>
</service>
```

### res/xml/accessibility_service_config.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindowsContent|flagRequestFilterKeyEvents"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:canRequestFilterKeyEvents="true"
    android:notificationTimeout="50"
    android:description="@string/accessibility_service_description"
    android:settingsActivity=".ui.SettingsActivity"/>
```

## UI Tree Reading

### Full Implementation
```kotlin
class UITreeReader(private val service: AccessibilityService) {
    
    data class UINode(
        val id: String?,
        val text: String?,
        val contentDesc: String?,
        val className: String?,
        val bounds: Rect,
        val isClickable: Boolean,
        val isScrollable: Boolean,
        val isPassword: Boolean,
        val isEditable: Boolean,
        val isChecked: Boolean,
        val children: List<UINode> = emptyList()
    )
    
    fun getUITree(): UINode? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            parseNode(root)
        } finally {
            root.recycle()
        }
    }
    
    private fun parseNode(node: AccessibilityNodeInfo): UINode {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        val children = (0 until node.childCount)
            .mapNotNull { node.getChild(it) }
            .filter { shouldInclude(it) }
            .map { child ->
                try { parseNode(child) }
                finally { child.recycle() }
            }
        
        return UINode(
            id = node.viewIdResourceName,
            text = node.text?.toString(),
            contentDesc = node.contentDescription?.toString(),
            className = node.className?.toString()?.substringAfterLast('.'),
            bounds = bounds,
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isPassword = node.isPassword,
            isEditable = node.isEditable,
            isChecked = node.isChecked,
            children = children
        )
    }
    
    private fun shouldInclude(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0) return false
        // Keep if: has text, content description, is interactive, or has relevant children
        return node.text != null || 
               node.contentDescription != null ||
               node.isClickable || 
               node.isScrollable || 
               node.isEditable ||
               node.childCount > 0
    }
    
    fun toCompactJson(root: UINode): String {
        // Serialize to compact JSON for LLM consumption
        // Target: < 3000 tokens
        val sb = StringBuilder()
        serializeNode(root, sb, 0)
        return sb.toString()
    }
    
    private fun serializeNode(node: UINode, sb: StringBuilder, depth: Int) {
        if (depth > 8) return // Max depth to prevent explosion
        sb.append("{")
        node.id?.let { sb.append("\"id\":\"${it.substringAfterLast('/')}\",") }
        node.text?.let { sb.append("\"t\":\"${it.take(50)}\",") }
        node.contentDesc?.let { sb.append("\"d\":\"${it.take(50)}\",") }
        if (node.isClickable) sb.append("\"c\":1,")
        if (node.isScrollable) sb.append("\"s\":1,")
        if (node.isPassword) sb.append("\"pw\":1,")
        if (node.isEditable) sb.append("\"e\":1,")
        sb.append("\"b\":[${node.bounds.left},${node.bounds.top},${node.bounds.right},${node.bounds.bottom}]")
        if (node.children.isNotEmpty()) {
            sb.append(",\"ch\":[")
            node.children.forEachIndexed { i, child ->
                if (i > 0) sb.append(",")
                serializeNode(child, sb, depth + 1)
            }
            sb.append("]")
        }
        sb.append("}")
    }
}
```

## Action Execution

### All Action Types
```kotlin
class ActionExecutor(private val service: AccessibilityService) {
    
    // Tap by node resource ID
    suspend fun tapById(resourceId: String): Boolean = withContext(Dispatchers.Main) {
        val node = findNodeById(resourceId) ?: return@withContext false
        val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        node.recycle()
        delay(300)
        result
    }
    
    // Tap by coordinate
    suspend fun tapAt(x: Float, y: Float): Boolean = withContext(Dispatchers.Main) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        
        var result = false
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) { result = true }
            override fun onCancelled(gestureDescription: GestureDescription) { result = false }
        }, null)
        delay(400)
        result
    }
    
    // Type text into focused element
    suspend fun typeText(nodeId: String, text: String): Boolean = withContext(Dispatchers.Main) {
        val node = findNodeById(nodeId) ?: return@withContext false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        val result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        delay(200)
        result
    }
    
    // Swipe gesture
    suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 300): Boolean = withContext(Dispatchers.Main) {
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        var result = false
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription) { result = true }
        }, null)
        delay(durationMs + 200)
        result
    }
    
    // Global navigation
    fun goHome() = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    fun goBack() = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    fun goRecents() = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    fun pullNotifications() = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    
    private fun findNodeById(resourceId: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByViewId(resourceId).firstOrNull()
    }
}
```

## Screenshot Capture (API 30+)
```kotlin
class ScreenCapture(private val service: AccessibilityService) {
    
    suspend fun captureScreen(): ByteArray? = suspendCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        screenshot.hardwareBuffer.close()
                        val softBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        bitmap?.recycle()
                        
                        val baos = ByteArrayOutputStream()
                        softBitmap?.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        softBitmap?.recycle()
                        continuation.resume(baos.toByteArray())
                    }
                    override fun onFailure(errorCode: Int) {
                        continuation.resume(null)
                    }
                }
            )
        } else {
            continuation.resume(null) // Fallback: use MediaProjection (requires permission dialog)
        }
    }
}
```

## Sensitivity Detection
```kotlin
object SensitivityGate {
    
    private val BANKING_PACKAGES = setOf(
        "com.google.android.apps.walletnfcrel",
        "net.one97.paytm",
        "com.phonepe.app",
        "in.amazon.mShop.android.shopping",
        "com.mobikwik_new",
        "com.freecharge.android",
        "com.dreamplug.androidapp",  // CRED
        // Add more as needed
    )
    
    fun isSensitive(uiTree: UITreeReader.UINode, currentPackage: String): Boolean {
        if (currentPackage in BANKING_PACKAGES) return true
        return containsPasswordField(uiTree)
    }
    
    private fun containsPasswordField(node: UITreeReader.UINode): Boolean {
        if (node.isPassword) return true
        return node.children.any { containsPasswordField(it) }
    }
}
```

## Common Pitfalls
- **Stale nodes:** Always recycle AccessibilityNodeInfo after use
- **Main thread required:** All accessibility actions must run on main thread
- **OEM issues:** Samsung One UI wraps some views; always try both resource ID and text matching
- **Timing:** Add delay(300) after most actions for UI to settle
- **API 30+:** takeScreenshot() only works API 30+; handle gracefully on lower versions
