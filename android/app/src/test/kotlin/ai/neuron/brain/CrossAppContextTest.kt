package ai.neuron.brain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CrossAppContextTest {
    private lateinit var context: CrossAppContext

    @BeforeEach
    fun setup() {
        context = CrossAppContext()
    }

    @Nested
    @DisplayName("Extracted values")
    inner class ExtractedValues {
        @Test
        fun should_storeAndRetrieveValue_when_putAndGet() {
            context.putValue("com.whatsapp:contact", "Mom")
            assertEquals("Mom", context.getValue("com.whatsapp:contact"))
        }

        @Test
        fun should_returnNull_when_keyNotPresent() {
            assertNull(context.getValue("nonexistent"))
        }

        @Test
        fun should_returnAllValues_when_getAllValuesCalled() {
            context.putValue("key1", "val1")
            context.putValue("key2", "val2")
            val all = context.getAllValues()
            assertEquals(2, all.size)
            assertEquals("val1", all["key1"])
            assertEquals("val2", all["key2"])
        }

        @Test
        fun should_truncateValue_when_exceedsMaxLength() {
            val longValue = "a".repeat(CrossAppContext.MAX_VALUE_LENGTH + 500)
            context.putValue("key", longValue)
            assertEquals(CrossAppContext.MAX_VALUE_LENGTH, context.getValue("key")!!.length)
        }

        @Test
        fun should_rejectNewEntries_when_maxEntriesReached() {
            repeat(CrossAppContext.MAX_ENTRIES) { i ->
                context.putValue("key_$i", "val_$i")
            }
            context.putValue("overflow", "should_not_store")
            assertNull(context.getValue("overflow"))
            assertEquals(CrossAppContext.MAX_ENTRIES, context.getAllValues().size)
        }
    }

    @Nested
    @DisplayName("App sequence")
    inner class AppSequence {
        @Test
        fun should_recordAppSwitch_when_differentApp() {
            context.recordAppSwitch("com.whatsapp")
            context.recordAppSwitch("com.google.android.apps.maps")
            assertEquals(listOf("com.whatsapp", "com.google.android.apps.maps"), context.getAppSequence())
        }

        @Test
        fun should_deduplicateConsecutive_when_sameApp() {
            context.recordAppSwitch("com.whatsapp")
            context.recordAppSwitch("com.whatsapp")
            context.recordAppSwitch("com.whatsapp")
            assertEquals(listOf("com.whatsapp"), context.getAppSequence())
        }

        @Test
        fun should_returnEmptyList_when_noSwitches() {
            assertTrue(context.getAppSequence().isEmpty())
        }
    }

    @Nested
    @DisplayName("Prompt context")
    inner class PromptContext {
        @Test
        fun should_returnNull_when_empty() {
            assertNull(context.buildPromptContext())
        }

        @Test
        fun should_buildContextString_when_dataPresent() {
            context.recordAppSwitch("com.google.android.gm")
            context.putValue("com.google.android.gm:address", "123 Main St")
            val prompt = context.buildPromptContext()!!
            assertTrue(prompt.contains("[Cross-App Context]"))
            assertTrue(prompt.contains("com.google.android.gm"))
            assertTrue(prompt.contains("123 Main St"))
        }
    }

    @Nested
    @DisplayName("Clear")
    inner class Clear {
        @Test
        fun should_clearAllState_when_clearCalled() {
            context.putValue("key", "value")
            context.recordAppSwitch("com.whatsapp")
            context.clear()
            assertTrue(context.isEmpty())
            assertTrue(context.getAllValues().isEmpty())
            assertTrue(context.getAppSequence().isEmpty())
        }
    }

    @Nested
    @DisplayName("isEmpty")
    inner class IsEmpty {
        @Test
        fun should_returnTrue_when_newContext() {
            assertTrue(context.isEmpty())
        }

        @Test
        fun should_returnFalse_when_valuesPresent() {
            context.putValue("key", "val")
            assertFalse(context.isEmpty())
        }

        @Test
        fun should_returnFalse_when_appSequencePresent() {
            context.recordAppSwitch("com.app")
            assertFalse(context.isEmpty())
        }
    }
}
