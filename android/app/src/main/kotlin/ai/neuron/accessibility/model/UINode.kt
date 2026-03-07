package ai.neuron.accessibility.model

import kotlinx.serialization.Serializable

@Serializable
data class UINode(
    val id: String = "",
    val text: String? = null,
    val desc: String? = null,
    val className: String? = null,
    val bounds: Bounds? = null,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val password: Boolean = false,
    val visible: Boolean = true,
    val children: List<UINode> = emptyList(),
)

@Serializable
data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
