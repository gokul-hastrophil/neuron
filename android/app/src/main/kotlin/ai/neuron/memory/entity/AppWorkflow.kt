package ai.neuron.memory.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_workflows")
data class AppWorkflow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val taskType: String,
    val actionSequenceJson: String,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val avgLatencyMs: Long = 0,
    val lastUsed: Long = System.currentTimeMillis(),
)
