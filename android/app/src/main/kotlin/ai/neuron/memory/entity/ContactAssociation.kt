package ai.neuron.memory.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_associations")
data class ContactAssociation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val canonicalKey: String,
    val packageName: String,
    val lastUsed: Long = System.currentTimeMillis(),
)
