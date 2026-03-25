package ai.neuron.memory

import ai.neuron.memory.dao.AuditDao
import ai.neuron.memory.entity.AuditEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRepository
    @Inject
    constructor(
        private val auditDao: AuditDao,
    ) {
        suspend fun logAction(
            actionType: String,
            targetPackage: String,
            command: String,
            success: Boolean,
            reasoning: String? = null,
            stepIndex: Int = 0,
            llmTier: String? = null,
            durationMs: Long = 0,
        ) {
            auditDao.insert(
                AuditEntry(
                    actionType = actionType,
                    targetPackage = targetPackage,
                    command = command,
                    success = success,
                    reasoning = reasoning,
                    stepIndex = stepIndex,
                    llmTier = llmTier,
                    durationMs = durationMs,
                ),
            )
        }

        fun observeAll(): Flow<List<AuditEntry>> = auditDao.observeAll()

        suspend fun getAll(): List<AuditEntry> = auditDao.getAll()

        suspend fun getRecent(limit: Int = 100): List<AuditEntry> = auditDao.getRecent(limit)

        suspend fun deleteAll() = auditDao.deleteAll()
    }
