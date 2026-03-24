package ai.neuron.memory

import ai.neuron.memory.dao.AppWorkflowDao
import ai.neuron.memory.dao.ContactAssociationDao
import ai.neuron.memory.dao.UserPreferenceDao
import ai.neuron.memory.entity.AppWorkflow
import ai.neuron.memory.entity.ContactAssociation
import ai.neuron.memory.entity.UserPreference
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LongTermMemory
    @Inject
    constructor(
        private val preferenceDao: UserPreferenceDao,
        private val workflowDao: AppWorkflowDao,
        private val contactDao: ContactAssociationDao,
    ) {
        // --- Preferences ---

        suspend fun savePreference(
            category: String,
            key: String,
            value: String,
            confidence: Float = 1.0f,
        ) {
            val existing = preferenceDao.findByKey(category, key)
            if (existing != null) {
                preferenceDao.update(
                    existing.copy(
                        value = value,
                        confidence = confidence,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                preferenceDao.insert(
                    UserPreference(
                        category = category,
                        key = key,
                        value = value,
                        confidence = confidence,
                    ),
                )
            }
        }

        suspend fun getPreference(
            category: String,
            key: String,
        ): String? = preferenceDao.findByKey(category, key)?.value

        suspend fun getPreferencesByCategory(category: String): List<UserPreference> = preferenceDao.findByCategory(category)

        fun observeAllPreferences(): Flow<List<UserPreference>> = preferenceDao.observeAll()

        suspend fun deletePreference(
            category: String,
            key: String,
        ) {
            preferenceDao.findByKey(category, key)?.let { preferenceDao.delete(it) }
        }

        suspend fun clearAllPreferences() = preferenceDao.deleteAll()

        // --- Workflows ---

        suspend fun saveWorkflow(
            packageName: String,
            taskType: String,
            actionSequenceJson: String,
            latencyMs: Long,
            success: Boolean,
        ) {
            val existing = workflowDao.findByPackageAndTask(packageName, taskType)
            if (existing != null) {
                val newSuccessCount = if (success) existing.successCount + 1 else existing.successCount
                val newFailCount = if (!success) existing.failCount + 1 else existing.failCount
                val totalRuns = newSuccessCount + newFailCount
                val newAvgLatency = ((existing.avgLatencyMs * (totalRuns - 1)) + latencyMs) / totalRuns
                workflowDao.update(
                    existing.copy(
                        actionSequenceJson = if (success) actionSequenceJson else existing.actionSequenceJson,
                        successCount = newSuccessCount,
                        failCount = newFailCount,
                        avgLatencyMs = newAvgLatency,
                        lastUsed = System.currentTimeMillis(),
                    ),
                )
            } else {
                workflowDao.insert(
                    AppWorkflow(
                        packageName = packageName,
                        taskType = taskType,
                        actionSequenceJson = actionSequenceJson,
                        successCount = if (success) 1 else 0,
                        failCount = if (!success) 1 else 0,
                        avgLatencyMs = latencyMs,
                    ),
                )
            }
        }

        suspend fun getCachedWorkflow(
            packageName: String,
            taskType: String,
        ): AppWorkflow? = workflowDao.findByPackageAndTask(packageName, taskType)

        suspend fun getMostSuccessfulWorkflows(limit: Int = 20): List<AppWorkflow> = workflowDao.getMostSuccessful(limit)

        suspend fun clearAllWorkflows() = workflowDao.deleteAll()

        // --- Contacts ---

        suspend fun saveContact(
            displayName: String,
            canonicalKey: String,
            packageName: String,
        ) {
            val existing = contactDao.findByCanonicalKey(canonicalKey)
            if (existing != null) {
                contactDao.update(
                    existing.copy(
                        displayName = displayName,
                        packageName = packageName,
                        lastUsed = System.currentTimeMillis(),
                    ),
                )
            } else {
                contactDao.insert(
                    ContactAssociation(
                        displayName = displayName,
                        canonicalKey = canonicalKey,
                        packageName = packageName,
                    ),
                )
            }
        }

        suspend fun findContact(name: String): ContactAssociation? =
            contactDao.findByCanonicalKey(name.lowercase()) ?: contactDao.searchByName(name).firstOrNull()

        suspend fun clearAllContacts() = contactDao.deleteAll()

        // --- Bulk ---

        suspend fun clearAll() {
            clearAllPreferences()
            clearAllWorkflows()
            clearAllContacts()
        }
    }
