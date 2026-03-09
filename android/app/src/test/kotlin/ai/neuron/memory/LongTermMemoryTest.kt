package ai.neuron.memory

import ai.neuron.memory.dao.AppWorkflowDao
import ai.neuron.memory.dao.ContactAssociationDao
import ai.neuron.memory.dao.UserPreferenceDao
import ai.neuron.memory.entity.AppWorkflow
import ai.neuron.memory.entity.ContactAssociation
import ai.neuron.memory.entity.UserPreference
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("LongTermMemory")
class LongTermMemoryTest {

    private lateinit var prefDao: UserPreferenceDao
    private lateinit var workflowDao: AppWorkflowDao
    private lateinit var contactDao: ContactAssociationDao
    private lateinit var memory: LongTermMemory

    @BeforeEach
    fun setup() {
        prefDao = mockk(relaxed = true)
        workflowDao = mockk(relaxed = true)
        contactDao = mockk(relaxed = true)
        memory = LongTermMemory(prefDao, workflowDao, contactDao)
    }

    @Nested
    @DisplayName("Preferences")
    inner class Preferences {

        @Test
        fun should_insertPreference_when_keyNotExists() = runTest {
            coEvery { prefDao.findByKey("app", "default_browser") } returns null

            memory.savePreference("app", "default_browser", "chrome")

            coVerify { prefDao.insert(match { it.category == "app" && it.key == "default_browser" && it.value == "chrome" }) }
        }

        @Test
        fun should_updatePreference_when_keyExists() = runTest {
            val existing = UserPreference(id = 1, category = "app", key = "default_browser", value = "firefox")
            coEvery { prefDao.findByKey("app", "default_browser") } returns existing

            memory.savePreference("app", "default_browser", "chrome")

            coVerify { prefDao.update(match { it.id == 1L && it.value == "chrome" }) }
        }

        @Test
        fun should_returnValue_when_preferenceExists() = runTest {
            coEvery { prefDao.findByKey("app", "default_browser") } returns
                UserPreference(category = "app", key = "default_browser", value = "chrome")

            val result = memory.getPreference("app", "default_browser")
            assertEquals("chrome", result)
        }

        @Test
        fun should_returnNull_when_preferenceNotExists() = runTest {
            coEvery { prefDao.findByKey("app", "unknown") } returns null

            assertNull(memory.getPreference("app", "unknown"))
        }

        @Test
        fun should_deletePreference_when_exists() = runTest {
            val existing = UserPreference(id = 1, category = "app", key = "x", value = "y")
            coEvery { prefDao.findByKey("app", "x") } returns existing

            memory.deletePreference("app", "x")

            coVerify { prefDao.delete(existing) }
        }

        @Test
        fun should_notThrow_when_deletingNonexistent() = runTest {
            coEvery { prefDao.findByKey("app", "x") } returns null

            memory.deletePreference("app", "x")

            coVerify(exactly = 0) { prefDao.delete(any()) }
        }
    }

    @Nested
    @DisplayName("Workflows")
    inner class Workflows {

        @Test
        fun should_insertWorkflow_when_newTask() = runTest {
            coEvery { workflowDao.findByPackageAndTask("com.google.android.calculator", "open") } returns null

            memory.saveWorkflow("com.google.android.calculator", "open", "[{\"action\":\"launch\"}]", 500L, true)

            coVerify { workflowDao.insert(match {
                it.packageName == "com.google.android.calculator" && it.successCount == 1 && it.failCount == 0
            }) }
        }

        @Test
        fun should_incrementSuccessCount_when_existingTaskSucceeds() = runTest {
            val existing = AppWorkflow(
                id = 1, packageName = "com.calc", taskType = "open",
                actionSequenceJson = "[old]", successCount = 3, failCount = 1, avgLatencyMs = 400,
            )
            coEvery { workflowDao.findByPackageAndTask("com.calc", "open") } returns existing

            memory.saveWorkflow("com.calc", "open", "[new]", 600L, true)

            val captured = slot<AppWorkflow>()
            coVerify { workflowDao.update(capture(captured)) }
            assertEquals(4, captured.captured.successCount)
            assertEquals(1, captured.captured.failCount)
            assertEquals("[new]", captured.captured.actionSequenceJson)
        }

        @Test
        fun should_incrementFailCount_when_existingTaskFails() = runTest {
            val existing = AppWorkflow(
                id = 1, packageName = "com.calc", taskType = "open",
                actionSequenceJson = "[old]", successCount = 3, failCount = 1, avgLatencyMs = 400,
            )
            coEvery { workflowDao.findByPackageAndTask("com.calc", "open") } returns existing

            memory.saveWorkflow("com.calc", "open", "[new]", 600L, false)

            val captured = slot<AppWorkflow>()
            coVerify { workflowDao.update(capture(captured)) }
            assertEquals(3, captured.captured.successCount)
            assertEquals(2, captured.captured.failCount)
            assertEquals("[old]", captured.captured.actionSequenceJson) // keep old on failure
        }

        @Test
        fun should_returnCachedWorkflow_when_exists() = runTest {
            val wf = AppWorkflow(packageName = "com.calc", taskType = "open", actionSequenceJson = "[steps]")
            coEvery { workflowDao.findByPackageAndTask("com.calc", "open") } returns wf

            val result = memory.getCachedWorkflow("com.calc", "open")
            assertEquals("[steps]", result?.actionSequenceJson)
        }

        @Test
        fun should_returnNull_when_noCachedWorkflow() = runTest {
            coEvery { workflowDao.findByPackageAndTask("com.calc", "open") } returns null

            assertNull(memory.getCachedWorkflow("com.calc", "open"))
        }
    }

    @Nested
    @DisplayName("Contacts")
    inner class Contacts {

        @Test
        fun should_insertContact_when_new() = runTest {
            coEvery { contactDao.findByCanonicalKey("john_doe") } returns null

            memory.saveContact("John Doe", "john_doe", "com.whatsapp")

            coVerify { contactDao.insert(match { it.displayName == "John Doe" && it.canonicalKey == "john_doe" }) }
        }

        @Test
        fun should_updateContact_when_existing() = runTest {
            val existing = ContactAssociation(id = 1, displayName = "John", canonicalKey = "john_doe", packageName = "com.whatsapp")
            coEvery { contactDao.findByCanonicalKey("john_doe") } returns existing

            memory.saveContact("John Doe", "john_doe", "com.google.android.apps.messaging")

            coVerify { contactDao.update(match { it.id == 1L && it.packageName == "com.google.android.apps.messaging" }) }
        }

        @Test
        fun should_findByCanonicalKey_when_exists() = runTest {
            val contact = ContactAssociation(displayName = "John", canonicalKey = "john", packageName = "com.whatsapp")
            coEvery { contactDao.findByCanonicalKey("john") } returns contact

            val result = memory.findContact("John")
            assertEquals("John", result?.displayName)
        }

        @Test
        fun should_fallbackToSearch_when_canonicalKeyMissing() = runTest {
            coEvery { contactDao.findByCanonicalKey("john doe") } returns null
            val contact = ContactAssociation(displayName = "John Doe", canonicalKey = "john_doe", packageName = "com.whatsapp")
            coEvery { contactDao.searchByName("John Doe") } returns listOf(contact)

            val result = memory.findContact("John Doe")
            assertEquals("John Doe", result?.displayName)
        }
    }

    @Nested
    @DisplayName("Bulk operations")
    inner class BulkOps {

        @Test
        fun should_clearAllTables_when_clearAllCalled() = runTest {
            memory.clearAll()

            coVerify { prefDao.deleteAll() }
            coVerify { workflowDao.deleteAll() }
            coVerify { contactDao.deleteAll() }
        }
    }
}
