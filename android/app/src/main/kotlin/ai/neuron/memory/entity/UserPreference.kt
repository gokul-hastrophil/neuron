package ai.neuron.memory.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
data class UserPreference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis(),
)
