package ai.neuron.character.renderer

import ai.neuron.character.model.EmotionState

/**
 * Abstraction for rendering a character on screen.
 * Implementations: [ComposeCharacterRenderer] (Canvas-based abstract characters),
 * and Live2DRenderer (anime characters, requires Cubism SDK).
 */
interface CharacterRenderer {
    /** Current emotion being displayed. */
    val currentEmotion: EmotionState

    /** Whether the idle animation (breathing, bouncing) is active. */
    val isIdleAnimating: Boolean

    /** Update the rendered emotion expression. */
    fun setEmotion(emotion: EmotionState)

    /** Start lip-sync animation driven by audio amplitude. */
    fun startLipSync(amplitudeProvider: () -> Float)

    /** Stop lip-sync animation. */
    fun stopLipSync()

    /** Enable or disable idle animation (blinking, breathing, bouncing). */
    fun setIdleAnimation(enabled: Boolean)

    /** Release all resources. Safe to call multiple times. */
    fun dispose()
}
