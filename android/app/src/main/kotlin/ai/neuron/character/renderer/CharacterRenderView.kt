@file:Suppress("ktlint:standard:function-naming")

package ai.neuron.character.renderer

import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Compose view that renders a character with emotion-based visuals.
 * Selects the correct renderer based on [CharacterType.rendererType] and
 * observes emotion changes.
 */
@Composable
fun CharacterRenderView(
    characterType: CharacterType,
    emotion: EmotionState,
    modifier: Modifier = Modifier,
) {
    val renderer =
        remember(characterType) {
            CharacterRendererFactory.create(characterType) as? ComposeCharacterRenderer
                ?: ComposeCharacterRenderer(characterType)
        }

    DisposableEffect(characterType) {
        renderer.setIdleAnimation(true)
        onDispose { renderer.dispose() }
    }

    renderer.setEmotion(emotion)
    val visuals = renderer.visuals

    val animatedColor by animateColorAsState(
        targetValue = Color(visuals.primaryColor),
        animationSpec = tween(durationMillis = 400),
        label = "emotionColor",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "idle")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = visuals.bounceAmplitude,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "bounce",
    )

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f),
    ) {
        when (characterType) {
            CharacterType.ABSTRACT_CUTE -> drawBlobCharacter(animatedColor, visuals, bounceOffset)
            CharacterType.ABSTRACT_PIXEL -> drawPixelCharacter(animatedColor, visuals, bounceOffset)
            else -> drawBlobCharacter(animatedColor, visuals, bounceOffset) // fallback
        }
    }
}

private fun DrawScope.drawBlobCharacter(
    color: Color,
    visuals: EmotionVisuals,
    bounceOffset: Float,
) {
    val centerX = size.width / 2
    val centerY = size.height / 2 - (bounceOffset * size.height)
    val bodyRadius = size.minDimension * 0.3f * visuals.bodyScale

    // Body
    drawCircle(color = color, radius = bodyRadius, center = Offset(centerX, centerY))

    // Eyes
    val eyeY = centerY - bodyRadius * 0.15f
    val eyeSpacing = bodyRadius * 0.35f
    val eyeRadius = bodyRadius * 0.08f
    drawEyes(visuals.eyeShape, eyeY, centerX, eyeSpacing, eyeRadius)

    // Mouth
    val mouthY = centerY + bodyRadius * 0.2f
    drawMouth(visuals.mouthShape, mouthY, centerX, bodyRadius * 0.15f)
}

private fun DrawScope.drawPixelCharacter(
    color: Color,
    visuals: EmotionVisuals,
    bounceOffset: Float,
) {
    val centerX = size.width / 2
    val centerY = size.height / 2 - (bounceOffset * size.height)
    val blockSize = size.minDimension * 0.08f
    val halfBody = (3 * blockSize * visuals.bodyScale)

    // Pixel body (grid of blocks)
    val startX = centerX - halfBody
    val startY = centerY - halfBody
    val cols = 6
    val rows = 6
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            drawRect(
                color = color,
                topLeft = Offset(startX + c * blockSize + 1, startY + r * blockSize + 1),
                size = Size(blockSize - 2, blockSize - 2),
            )
        }
    }

    // Eyes (pixel-style)
    val eyeY = centerY - blockSize
    val eyeSpacing = blockSize * 1.5f
    drawRect(Color.Black, Offset(centerX - eyeSpacing - blockSize / 2, eyeY), Size(blockSize, blockSize))
    drawRect(Color.Black, Offset(centerX + eyeSpacing - blockSize / 2, eyeY), Size(blockSize, blockSize))

    // Mouth
    val mouthY = centerY + blockSize * 0.5f
    val mouthWidth = if (visuals.mouthShape == MouthShape.SMILE_WIDE) blockSize * 2 else blockSize
    drawRect(Color.Black, Offset(centerX - mouthWidth / 2, mouthY), Size(mouthWidth, blockSize * 0.5f))
}

private fun DrawScope.drawEyes(
    shape: EyeShape,
    eyeY: Float,
    centerX: Float,
    spacing: Float,
    radius: Float,
) {
    val eyeColor = Color.Black
    when (shape) {
        EyeShape.SQUINT_HAPPY -> {
            // Happy squint — horizontal lines
            drawRect(eyeColor, Offset(centerX - spacing - radius, eyeY), Size(radius * 2, radius * 0.5f))
            drawRect(eyeColor, Offset(centerX + spacing - radius, eyeY), Size(radius * 2, radius * 0.5f))
        }
        EyeShape.WIDE_OPEN -> {
            drawCircle(eyeColor, radius * 1.3f, Offset(centerX - spacing, eyeY))
            drawCircle(eyeColor, radius * 1.3f, Offset(centerX + spacing, eyeY))
        }
        EyeShape.WINK -> {
            drawCircle(eyeColor, radius, Offset(centerX - spacing, eyeY))
            drawRect(eyeColor, Offset(centerX + spacing - radius, eyeY), Size(radius * 2, radius * 0.5f))
        }
        EyeShape.NARROW -> {
            drawOval(eyeColor, Offset(centerX - spacing - radius, eyeY - radius * 0.3f), Size(radius * 2, radius * 0.6f))
            drawOval(eyeColor, Offset(centerX + spacing - radius, eyeY - radius * 0.3f), Size(radius * 2, radius * 0.6f))
        }
        else -> {
            drawCircle(eyeColor, radius, Offset(centerX - spacing, eyeY))
            drawCircle(eyeColor, radius, Offset(centerX + spacing, eyeY))
        }
    }
}

private fun DrawScope.drawMouth(
    shape: MouthShape,
    mouthY: Float,
    centerX: Float,
    width: Float,
) {
    val mouthColor = Color.Black
    when (shape) {
        MouthShape.SMILE_WIDE -> {
            drawArc(mouthColor, 0f, 180f, true, Offset(centerX - width, mouthY - width * 0.3f), Size(width * 2, width))
        }
        MouthShape.FROWN -> {
            drawArc(mouthColor, 180f, 180f, true, Offset(centerX - width * 0.7f, mouthY), Size(width * 1.4f, width * 0.7f))
        }
        MouthShape.SLIGHT_FROWN -> {
            drawArc(mouthColor, 180f, 180f, true, Offset(centerX - width * 0.5f, mouthY), Size(width, width * 0.4f))
        }
        MouthShape.FLAT -> {
            drawRect(mouthColor, Offset(centerX - width * 0.5f, mouthY), Size(width, width * 0.15f))
        }
        MouthShape.OPEN -> {
            drawOval(mouthColor, Offset(centerX - width * 0.4f, mouthY - width * 0.2f), Size(width * 0.8f, width * 0.6f))
        }
        MouthShape.SMILE_SIDE -> {
            drawArc(mouthColor, 10f, 160f, true, Offset(centerX - width * 0.8f, mouthY - width * 0.2f), Size(width * 1.6f, width * 0.7f))
        }
        MouthShape.SLIGHT_SMILE -> {
            drawArc(mouthColor, 10f, 160f, true, Offset(centerX - width * 0.5f, mouthY - width * 0.1f), Size(width, width * 0.4f))
        }
    }
}

private fun DrawScope.drawOval(
    color: Color,
    topLeft: Offset,
    size: Size,
) {
    drawOval(color = color, topLeft = topLeft, size = size)
}
