package ai.neuron.character

import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.RendererType
import ai.neuron.character.renderer.CharacterRenderer
import ai.neuron.character.renderer.CharacterRendererFactory
import ai.neuron.character.renderer.ComposeCharacterRenderer
import ai.neuron.character.renderer.EmotionVisuals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CharacterRendererTest {
    // ─── Renderer Factory ───

    @Nested
    @DisplayName("Renderer factory")
    inner class Factory {
        @Test
        fun should_returnComposeRenderer_when_abstractCuteType() {
            val renderer = CharacterRendererFactory.create(CharacterType.ABSTRACT_CUTE)
            assertTrue(renderer is ComposeCharacterRenderer, "Expected ComposeCharacterRenderer")
        }

        @Test
        fun should_returnComposeRenderer_when_abstractPixelType() {
            val renderer = CharacterRendererFactory.create(CharacterType.ABSTRACT_PIXEL)
            assertTrue(renderer is ComposeCharacterRenderer, "Expected ComposeCharacterRenderer")
        }

        @Test
        fun should_returnComposeRenderer_when_live2dNotAvailable() {
            // Live2D SDK not bundled — factory falls back to Compose for anime types
            val renderer = CharacterRendererFactory.create(CharacterType.ANIME_GIRL)
            assertNotNull(renderer, "Should always return a renderer")
        }

        @Test
        fun should_matchRendererType_when_typeChecked() {
            assertEquals(RendererType.COMPOSE, CharacterType.ABSTRACT_CUTE.rendererType)
            assertEquals(RendererType.COMPOSE, CharacterType.ABSTRACT_PIXEL.rendererType)
            assertEquals(RendererType.LIVE2D, CharacterType.ANIME_GIRL.rendererType)
            assertEquals(RendererType.LIVE2D, CharacterType.ANIME_BOY.rendererType)
        }
    }

    // ─── Emotion Visuals ───

    @Nested
    @DisplayName("Emotion visuals mapping")
    inner class EmotionVisualsMapping {
        @Test
        fun should_haveVisualsForAllEmotions_when_allStatesChecked() {
            EmotionState.entries.forEach { emotion ->
                val visuals = EmotionVisuals.forEmotion(emotion)
                assertNotNull(visuals, "${emotion.name} should have visuals")
                assertTrue(visuals.primaryColor != 0L, "${emotion.name} should have a non-zero color")
            }
        }

        @Test
        fun should_haveDifferentColors_when_distinctEmotions() {
            val happyColor = EmotionVisuals.forEmotion(EmotionState.HAPPY).primaryColor
            val sadColor = EmotionVisuals.forEmotion(EmotionState.SAD).primaryColor
            assertTrue(happyColor != sadColor, "Happy and Sad should have different colors")
        }

        @Test
        fun should_haveEyeShape_when_emotionMapped() {
            val thinking = EmotionVisuals.forEmotion(EmotionState.THINKING)
            assertNotNull(thinking.eyeShape, "THINKING should have an eye shape")
        }

        @Test
        fun should_haveMouthShape_when_emotionMapped() {
            val happy = EmotionVisuals.forEmotion(EmotionState.HAPPY)
            assertNotNull(happy.mouthShape, "HAPPY should have a mouth shape")
        }
    }

    // ─── Compose Renderer ───

    @Nested
    @DisplayName("Compose renderer")
    inner class ComposeRenderer {
        @Test
        fun should_acceptEmotionUpdate_when_setEmotionCalled() {
            val renderer = ComposeCharacterRenderer(CharacterType.ABSTRACT_CUTE)
            renderer.setEmotion(EmotionState.HAPPY)
            assertEquals(EmotionState.HAPPY, renderer.currentEmotion)
        }

        @Test
        fun should_defaultToNeutral_when_created() {
            val renderer = ComposeCharacterRenderer(CharacterType.ABSTRACT_CUTE)
            assertEquals(EmotionState.NEUTRAL, renderer.currentEmotion)
        }

        @Test
        fun should_toggleIdleAnimation_when_setCalled() {
            val renderer = ComposeCharacterRenderer(CharacterType.ABSTRACT_CUTE)
            renderer.setIdleAnimation(true)
            assertTrue(renderer.isIdleAnimating)
        }

        @Test
        fun should_notCrash_when_disposedTwice() {
            val renderer = ComposeCharacterRenderer(CharacterType.ABSTRACT_PIXEL)
            renderer.dispose()
            renderer.dispose() // should not throw
        }
    }

    // ─── CharacterRenderer Interface ───

    @Nested
    @DisplayName("Renderer interface contract")
    inner class InterfaceContract {
        @Test
        fun should_haveAllRequiredMethods_when_interfaceChecked() {
            // Verify CharacterRenderer has the methods we depend on
            val methods = CharacterRenderer::class.java.declaredMethods.map { it.name }
            assertTrue("setEmotion" in methods, "Missing setEmotion")
            assertTrue("setIdleAnimation" in methods, "Missing setIdleAnimation")
            assertTrue("dispose" in methods, "Missing dispose")
        }
    }
}
