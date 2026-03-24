package ai.neuron.character.renderer

import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState

/**
 * Renders abstract/cute characters using Compose Canvas.
 * Supports emotion-based shape/color changes, idle bounce animation,
 * and simple lip-sync (mouth open/close).
 */
class ComposeCharacterRenderer(
    val characterType: CharacterType,
) : CharacterRenderer {
    override var currentEmotion: EmotionState = EmotionState.NEUTRAL
        private set

    override var isIdleAnimating: Boolean = false
        private set

    private var lipSyncProvider: (() -> Float)? = null
    private var disposed = false

    /** Current visual parameters derived from emotion. */
    val visuals: EmotionVisuals
        get() = EmotionVisuals.forEmotion(currentEmotion)

    override fun setEmotion(emotion: EmotionState) {
        if (disposed) return
        currentEmotion = emotion
    }

    override fun startLipSync(amplitudeProvider: () -> Float) {
        if (disposed) return
        lipSyncProvider = amplitudeProvider
    }

    override fun stopLipSync() {
        lipSyncProvider = null
    }

    /** Get current lip-sync mouth openness (0.0 = closed, 1.0 = fully open). */
    fun getLipSyncAmount(): Float {
        return lipSyncProvider?.invoke()?.coerceIn(0f, 1f) ?: 0f
    }

    override fun setIdleAnimation(enabled: Boolean) {
        if (disposed) return
        isIdleAnimating = enabled
    }

    override fun dispose() {
        disposed = true
        lipSyncProvider = null
        isIdleAnimating = false
    }
}
