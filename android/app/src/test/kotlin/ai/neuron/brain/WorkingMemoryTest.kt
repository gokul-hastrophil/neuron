package ai.neuron.brain

import ai.neuron.brain.model.ActionType
import ai.neuron.brain.model.LLMAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WorkingMemoryTest {
    private lateinit var memory: WorkingMemory

    @BeforeEach
    fun setup() {
        memory = WorkingMemory()
    }

    @Nested
    @DisplayName("Current task")
    inner class CurrentTask {
        @Test
        fun should_storeAndRetrieveTask_when_taskSet() {
            memory.setCurrentTask("open WhatsApp and message Mom")
            assertEquals("open WhatsApp and message Mom", memory.getCurrentTask())
        }

        @Test
        fun should_returnNull_when_noTaskSet() {
            assertNull(memory.getCurrentTask())
        }

        @Test
        fun should_clearTask_when_clearCalled() {
            memory.setCurrentTask("some task")
            memory.clear()
            assertNull(memory.getCurrentTask())
        }
    }

    @Nested
    @DisplayName("Action history")
    inner class ActionHistory {
        @Test
        fun should_recordActions_when_actionsAdded() {
            val action = LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9)
            memory.addAction(action)
            assertEquals(1, memory.getActionHistory().size)
            assertEquals(action, memory.getActionHistory().first())
        }

        @Test
        fun should_limitToLast10_when_moreThan10Actions() {
            repeat(15) { i ->
                memory.addAction(
                    LLMAction(actionType = ActionType.TAP, targetId = "btn_$i", confidence = 0.9),
                )
            }
            val history = memory.getActionHistory()
            assertEquals(10, history.size)
            assertEquals("btn_5", history.first().targetId)
            assertEquals("btn_14", history.last().targetId)
        }

        @Test
        fun should_clearHistory_when_clearCalled() {
            memory.addAction(LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9))
            memory.clear()
            assertTrue(memory.getActionHistory().isEmpty())
        }
    }

    @Nested
    @DisplayName("Screen state hash")
    inner class ScreenStateHash {
        @Test
        fun should_storeScreenHash_when_set() {
            memory.setScreenStateHash(12345)
            assertEquals(12345, memory.getScreenStateHash())
        }

        @Test
        fun should_returnZero_when_noHashSet() {
            assertEquals(0, memory.getScreenStateHash())
        }

        @Test
        fun should_clearHash_when_clearCalled() {
            memory.setScreenStateHash(12345)
            memory.clear()
            assertEquals(0, memory.getScreenStateHash())
        }
    }

    @Nested
    @DisplayName("Serialization")
    inner class Serialization {
        @Test
        fun should_serializeAndDeserialize_when_roundtrip() {
            memory.setCurrentTask("test task")
            memory.addAction(LLMAction(actionType = ActionType.TAP, targetId = "btn", confidence = 0.9))
            memory.setScreenStateHash(42)

            val json = memory.toJson()
            val restored = WorkingMemory.fromJson(json)

            assertEquals("test task", restored.getCurrentTask())
            assertEquals(1, restored.getActionHistory().size)
            assertEquals(42, restored.getScreenStateHash())
        }

        @Test
        fun should_handleEmptyMemory_when_serialized() {
            val json = memory.toJson()
            val restored = WorkingMemory.fromJson(json)

            assertNull(restored.getCurrentTask())
            assertTrue(restored.getActionHistory().isEmpty())
            assertEquals(0, restored.getScreenStateHash())
        }
    }
}
