package ai.neuron.character.renderer

import ai.neuron.character.model.EmotionState

/**
 * Maps [EmotionState] to visual rendering parameters for abstract characters.
 * Used by [ComposeCharacterRenderer] to drive shape/color changes.
 */
data class EmotionVisuals(
    val primaryColor: Long,
    val eyeShape: EyeShape,
    val mouthShape: MouthShape,
    val bodyScale: Float = 1.0f,
    val bounceAmplitude: Float = 0.02f,
) {
    companion object {
        fun forEmotion(emotion: EmotionState): EmotionVisuals =
            when (emotion) {
                EmotionState.HAPPY ->
                    EmotionVisuals(
                        primaryColor = 0xFFFFD54F,
                        eyeShape = EyeShape.SQUINT_HAPPY,
                        mouthShape = MouthShape.SMILE_WIDE,
                        bodyScale = 1.05f,
                        bounceAmplitude = 0.04f,
                    )
                EmotionState.SAD ->
                    EmotionVisuals(
                        primaryColor = 0xFF64B5F6,
                        eyeShape = EyeShape.DROOPY,
                        mouthShape = MouthShape.FROWN,
                        bodyScale = 0.95f,
                        bounceAmplitude = 0.01f,
                    )
                EmotionState.THINKING ->
                    EmotionVisuals(
                        primaryColor = 0xFFCE93D8,
                        eyeShape = EyeShape.LOOK_UP,
                        mouthShape = MouthShape.FLAT,
                        bodyScale = 1.0f,
                        bounceAmplitude = 0.015f,
                    )
                EmotionState.EXCITED ->
                    EmotionVisuals(
                        primaryColor = 0xFFFF8A65,
                        eyeShape = EyeShape.WIDE_OPEN,
                        mouthShape = MouthShape.SMILE_WIDE,
                        bodyScale = 1.1f,
                        bounceAmplitude = 0.06f,
                    )
                EmotionState.CONCERNED ->
                    EmotionVisuals(
                        primaryColor = 0xFF90A4AE,
                        eyeShape = EyeShape.WORRIED,
                        mouthShape = MouthShape.SLIGHT_FROWN,
                        bodyScale = 0.98f,
                        bounceAmplitude = 0.015f,
                    )
                EmotionState.PLAYFUL ->
                    EmotionVisuals(
                        primaryColor = 0xFF81C784,
                        eyeShape = EyeShape.WINK,
                        mouthShape = MouthShape.SMILE_SIDE,
                        bodyScale = 1.05f,
                        bounceAmplitude = 0.05f,
                    )
                EmotionState.FOCUSED ->
                    EmotionVisuals(
                        primaryColor = 0xFF7986CB,
                        eyeShape = EyeShape.NARROW,
                        mouthShape = MouthShape.FLAT,
                        bodyScale = 1.0f,
                        bounceAmplitude = 0.01f,
                    )
                EmotionState.NEUTRAL ->
                    EmotionVisuals(
                        primaryColor = 0xFFB0BEC5,
                        eyeShape = EyeShape.NORMAL,
                        mouthShape = MouthShape.SLIGHT_SMILE,
                        bodyScale = 1.0f,
                        bounceAmplitude = 0.02f,
                    )
            }
    }
}

enum class EyeShape {
    NORMAL,
    SQUINT_HAPPY,
    DROOPY,
    LOOK_UP,
    WIDE_OPEN,
    WORRIED,
    WINK,
    NARROW,
}

enum class MouthShape {
    SLIGHT_SMILE,
    SMILE_WIDE,
    SMILE_SIDE,
    FROWN,
    SLIGHT_FROWN,
    FLAT,
    OPEN,
}
