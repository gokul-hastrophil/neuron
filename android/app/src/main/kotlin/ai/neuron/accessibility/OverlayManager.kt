@file:Suppress("ktlint:standard:function-naming")

package ai.neuron.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

class OverlayManager(
    private val service: NeuronAccessibilityService,
) {
    enum class OverlayState {
        HIDDEN,
        IDLE,
        LISTENING,
        THINKING,
        EXECUTING,
        CONFIRMING,
        DONE,
        ERROR,
    }

    val state = mutableStateOf(OverlayState.HIDDEN)
    val partialTranscript = mutableStateOf("")
    val errorMessage = mutableStateOf("")
    val statusText = mutableStateOf("")
    val confirmationPrompt = mutableStateOf("")

    var onHoldStart: (() -> Unit)? = null
    var onHoldRelease: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onConfirmAction: (() -> Unit)? = null
    var onRejectAction: (() -> Unit)? = null

    private var overlayView: ComposeView? = null
    private val windowManager: WindowManager
        get() = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun show() {
        if (overlayView != null) return
        Log.d(TAG, "Showing overlay")

        state.value = OverlayState.IDLE

        val view =
            ComposeView(service).apply {
                val lifecycleOwner = OverlayLifecycleOwner()
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
                lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }

        view.setContent {
            NeuronOverlay(
                overlayState = state.value,
                partialText = partialTranscript.value,
                errorText = errorMessage.value,
                statusLabel = statusText.value,
                confirmationText = confirmationPrompt.value,
                onHoldStart = { onHoldStart?.invoke() },
                onHoldRelease = { onHoldRelease?.invoke() },
                onClose = { onClose?.invoke() },
                onConfirm = { onConfirmAction?.invoke() },
                onReject = { onRejectAction?.invoke() },
            )
        }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    fun hide() {
        Log.d(TAG, "Hiding overlay")
        state.value = OverlayState.HIDDEN
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay", e)
            }
        }
        overlayView = null
    }

    fun updateState(newState: OverlayState) {
        state.value = newState
        Log.d(TAG, "Overlay state: $newState")
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            savedStateRegistryController.performRestore(null)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }

    companion object {
        private const val TAG = "NeuronOverlay"
    }
}

// ─── Colors ───

private val Purple = Color(0xFF6750A4)
private val PurpleDark = Color(0xFF4A3780)
private val Pink = Color(0xFFE91E63)
private val Orange = Color(0xFFFF9800)
private val Blue = Color(0xFF2196F3)
private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFF44336)
private val Amber = Color(0xFFFFC107)
private val CardBg = Color(0xF0202020)
private val CardBgError = Color(0xF0401010)

// ─── Main Overlay Composable ───

@Composable
private fun NeuronOverlay(
    overlayState: OverlayManager.OverlayState,
    partialText: String,
    errorText: String,
    statusLabel: String,
    confirmationText: String = "",
    onHoldStart: () -> Unit,
    onHoldRelease: () -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit = {},
    onReject: () -> Unit = {},
) {
    if (overlayState == OverlayManager.OverlayState.HIDDEN) return

    val isActive = overlayState != OverlayManager.OverlayState.IDLE

    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        // Close button (X) — always visible at top-right
        CloseButton(onClick = onClose)

        Spacer(modifier = Modifier.height(4.dp))

        // Status card — visible when not IDLE
        AnimatedVisibility(
            visible = isActive,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(150)),
        ) {
            StatusCard(
                overlayState = overlayState,
                partialText = partialText,
                errorText = errorText,
                statusLabel = statusLabel,
                confirmationText = confirmationText,
                onConfirm = onConfirm,
                onReject = onReject,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // The bubble
        AnimatedBubble(
            overlayState = overlayState,
            onHoldStart = onHoldStart,
            onHoldRelease = onHoldRelease,
        )
    }
}

// ─── Close Button ───

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(24.dp)
                .shadow(2.dp, CircleShape)
                .background(Color(0xCC333333), CircleShape)
                .clip(CircleShape)
                .clickable { onClick() },
    ) {
        Text(
            text = "\u2715",
            color = Color(0xCCFFFFFF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─── Status Card ───

@Composable
private fun StatusCard(
    overlayState: OverlayManager.OverlayState,
    partialText: String,
    errorText: String,
    statusLabel: String,
    confirmationText: String = "",
    onConfirm: () -> Unit = {},
    onReject: () -> Unit = {},
) {
    val isError = overlayState == OverlayManager.OverlayState.ERROR
    val isConfirming = overlayState == OverlayManager.OverlayState.CONFIRMING
    val bgColor = if (isError) CardBgError else CardBg

    val (icon, title, accentColor) =
        when (overlayState) {
            OverlayManager.OverlayState.LISTENING -> Triple("\uD83C\uDF99\uFE0F", "Listening", Pink)
            OverlayManager.OverlayState.THINKING -> Triple("\uD83E\uDDE0", "Thinking", Orange)
            OverlayManager.OverlayState.EXECUTING -> Triple("\u26A1", "Executing", Blue)
            OverlayManager.OverlayState.CONFIRMING -> Triple("\u2753", "Confirm?", Amber)
            OverlayManager.OverlayState.DONE -> Triple("\u2713", "Done", Green)
            OverlayManager.OverlayState.ERROR -> Triple("\u26A0\uFE0F", "Error", Red)
            else -> Triple("", "", Purple)
        }

    // Animated dots for THINKING
    val dotsText =
        if (overlayState == OverlayManager.OverlayState.THINKING) {
            val transition = rememberInfiniteTransition(label = "dots")
            val dotCount by transition.animateFloat(
                initialValue = 0f,
                targetValue = 4f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "dotCount",
            )
            ".".repeat(dotCount.toInt().coerceIn(0, 3))
        } else {
            ""
        }

    Column(
        modifier =
            Modifier
                .widthIn(max = 220.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .background(bgColor, RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        // Title row: icon + state name
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title + dotsText,
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Description text (custom status or partial transcript or error)
        val description =
            when {
                isError && errorText.isNotBlank() -> errorText
                isConfirming && confirmationText.isNotBlank() -> confirmationText
                overlayState == OverlayManager.OverlayState.LISTENING && partialText.isNotBlank() -> "\"$partialText\""
                statusLabel.isNotBlank() -> statusLabel
                overlayState == OverlayManager.OverlayState.LISTENING -> "Hold and speak..."
                overlayState == OverlayManager.OverlayState.THINKING -> "Processing your command"
                overlayState == OverlayManager.OverlayState.EXECUTING -> "Running action"
                overlayState == OverlayManager.OverlayState.CONFIRMING -> "Approve this action?"
                overlayState == OverlayManager.OverlayState.DONE -> "Task complete"
                else -> ""
            }

        if (description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = if (isError) Color(0xFFFF8A80) else Color(0xCCFFFFFF),
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                lineHeight = 16.sp,
            )
        }

        // Confirm / Reject buttons for CONFIRMING state
        if (isConfirming) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(Green.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onConfirm() },
                ) {
                    Text(
                        text = "Yes",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(32.dp)
                            .background(Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReject() },
                ) {
                    Text(
                        text = "No",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ─── Animated Bubble ───

@Composable
private fun AnimatedBubble(
    overlayState: OverlayManager.OverlayState,
    onHoldStart: () -> Unit,
    onHoldRelease: () -> Unit,
) {
    val bubbleColor by animateColorAsState(
        targetValue =
            when (overlayState) {
                OverlayManager.OverlayState.HIDDEN -> Color.Transparent
                OverlayManager.OverlayState.IDLE -> Purple
                OverlayManager.OverlayState.LISTENING -> Pink
                OverlayManager.OverlayState.THINKING -> Orange
                OverlayManager.OverlayState.EXECUTING -> Blue
                OverlayManager.OverlayState.CONFIRMING -> Amber
                OverlayManager.OverlayState.DONE -> Green
                OverlayManager.OverlayState.ERROR -> Red
            },
        animationSpec = tween(300),
        label = "bubbleColor",
    )

    val label =
        when (overlayState) {
            OverlayManager.OverlayState.HIDDEN -> ""
            OverlayManager.OverlayState.IDLE -> "N"
            OverlayManager.OverlayState.LISTENING -> "\uD83C\uDF99"
            OverlayManager.OverlayState.THINKING -> ""
            OverlayManager.OverlayState.EXECUTING -> "\u26A1"
            OverlayManager.OverlayState.CONFIRMING -> "?"
            OverlayManager.OverlayState.DONE -> "\u2713"
            OverlayManager.OverlayState.ERROR -> "!"
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(64.dp),
    ) {
        // Ripple rings for LISTENING
        if (overlayState == OverlayManager.OverlayState.LISTENING) {
            ListeningRipples()
        }

        // Spinning ring for THINKING
        if (overlayState == OverlayManager.OverlayState.THINKING) {
            SpinningRing(color = Orange)
        }

        // Progress ring for EXECUTING
        if (overlayState == OverlayManager.OverlayState.EXECUTING) {
            SpinningRing(color = Blue)
        }

        // Shake for ERROR
        val shakeOffset =
            if (overlayState == OverlayManager.OverlayState.ERROR) {
                val shakeAnim = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    repeat(3) {
                        shakeAnim.animateTo(6f, tween(50))
                        shakeAnim.animateTo(-6f, tween(50))
                    }
                    shakeAnim.animateTo(0f, tween(50))
                }
                shakeAnim.value
            } else {
                0f
            }

        // Pulse scale for LISTENING
        val pulseScale =
            if (overlayState == OverlayManager.OverlayState.LISTENING) {
                val transition = rememberInfiniteTransition(label = "pulse")
                val scale by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.12f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "pulseScale",
                )
                scale
            } else {
                1f
            }

        // Pop scale for DONE
        val doneScale by animateFloatAsState(
            targetValue = if (overlayState == OverlayManager.OverlayState.DONE) 1.2f else 1f,
            animationSpec = tween(300),
            label = "doneScale",
        )

        val finalScale = pulseScale * doneScale

        // The main bubble circle
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(52.dp)
                    .offset { IntOffset(shakeOffset.roundToInt(), 0) }
                    .scale(finalScale)
                    .shadow(8.dp, CircleShape)
                    .background(bubbleColor, CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            onHoldStart()
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { !it.changedToUp() })
                            onHoldRelease()
                        }
                    },
        ) {
            if (overlayState == OverlayManager.OverlayState.THINKING) {
                // Animated dots inside bubble for THINKING
                ThinkingDots()
            } else {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ─── Listening Ripple Rings ───

@Composable
private fun ListeningRipples() {
    val transition = rememberInfiniteTransition(label = "ripple")

    repeat(2) { index ->
        val delay = index * 600
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.8f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1200, delayMillis = delay),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "rippleScale$index",
        )
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1200, delayMillis = delay),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "rippleAlpha$index",
        )
        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .background(Pink.copy(alpha = 0.5f), CircleShape),
        )
    }
}

// ─── Spinning Ring ───

@Composable
private fun SpinningRing(color: Color) {
    val transition = rememberInfiniteTransition(label = "spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
            ),
        label = "rotation",
    )

    Box(
        modifier =
            Modifier
                .size(60.dp)
                .drawBehind {
                    rotate(rotation) {
                        drawArc(
                            brush =
                                Brush.sweepGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            color.copy(alpha = 0.3f),
                                            color.copy(alpha = 0.8f),
                                            color,
                                        ),
                                ),
                            startAngle = 0f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                },
    )
}

// ─── Thinking Dots ───

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinkDots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val delay = index * 200
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(600, delayMillis = delay),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "dot$index",
            )
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .alpha(alpha)
                        .background(Color.White, CircleShape),
            )
        }
    }
}
