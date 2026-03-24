package ai.neuron.character.renderer

import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.RendererType

/**
 * Creates the appropriate [CharacterRenderer] for a given [CharacterType].
 *
 * Live2D types fall back to [ComposeCharacterRenderer] if the Cubism SDK
 * is not available (native .so not bundled).
 */
object CharacterRendererFactory {
    fun create(type: CharacterType): CharacterRenderer =
        when (type.rendererType) {
            RendererType.COMPOSE -> ComposeCharacterRenderer(type)
            RendererType.LIVE2D -> createLive2DOrFallback(type)
        }

    private fun createLive2DOrFallback(type: CharacterType): CharacterRenderer {
        // Live2D Cubism SDK requires native .so files bundled in jniLibs.
        // When not available, fall back to Compose renderer.
        return if (isLive2DAvailable()) {
            // TODO: Replace with Live2DRenderer(type) when Cubism SDK is integrated
            ComposeCharacterRenderer(type)
        } else {
            ComposeCharacterRenderer(type)
        }
    }

    /** Check if Live2D native libraries are available at runtime. */
    private fun isLive2DAvailable(): Boolean =
        try {
            Class.forName("com.live2d.sdk.cubism.core.CubismFramework")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
}
