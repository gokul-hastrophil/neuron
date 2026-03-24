package ai.neuron.accessibility.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UITree(
    val nodes: List<UINode> = emptyList(),
    val packageName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toJson(): String = jsonFormat.encodeToString(this)

    companion object {
        private val jsonFormat =
            Json {
                encodeDefaults = false
                explicitNulls = false
            }

        fun empty(): UITree = UITree()
    }
}
