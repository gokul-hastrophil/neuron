package ai.neuron.memory.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_log")
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String,
    val targetPackage: String,
    val command: String,
    val success: Boolean,
    val reasoning: String? = null,
    val stepIndex: Int = 0,
    val llmTier: String? = null,
    val durationMs: Long = 0,
)
