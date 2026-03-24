package ai.neuron.brain

import ai.neuron.brain.model.LLMAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkingMemory
    @Inject
    constructor() {
        companion object {
            private const val MAX_HISTORY_SIZE = 10

            private val json =
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                }

            fun fromJson(jsonString: String): WorkingMemory {
                val state = json.decodeFromString<WorkingMemoryState>(jsonString)
                return WorkingMemory().apply {
                    currentTask = state.currentTask
                    actionHistory.addAll(state.actionHistory)
                    screenStateHash = state.screenStateHash
                }
            }
        }

        private var currentTask: String? = null
        private val actionHistory = mutableListOf<LLMAction>()
        private var screenStateHash: Int = 0

        fun setCurrentTask(task: String) {
            currentTask = task
        }

        fun getCurrentTask(): String? = currentTask

        fun addAction(action: LLMAction) {
            actionHistory.add(action)
            if (actionHistory.size > MAX_HISTORY_SIZE) {
                actionHistory.removeAt(0)
            }
        }

        fun getActionHistory(): List<LLMAction> = actionHistory.toList()

        fun setScreenStateHash(hash: Int) {
            screenStateHash = hash
        }

        fun getScreenStateHash(): Int = screenStateHash

        fun clear() {
            currentTask = null
            actionHistory.clear()
            screenStateHash = 0
        }

        fun toJson(): String {
            val state =
                WorkingMemoryState(
                    currentTask = currentTask,
                    actionHistory = actionHistory.toList(),
                    screenStateHash = screenStateHash,
                )
            return json.encodeToString(state)
        }

        @Serializable
        private data class WorkingMemoryState(
            val currentTask: String? = null,
            val actionHistory: List<LLMAction> = emptyList(),
            val screenStateHash: Int = 0,
        )
    }
