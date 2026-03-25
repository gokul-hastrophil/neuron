package ai.neuron.memory.dao

import ai.neuron.memory.entity.AuditEntry
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntry): Long

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC")
    suspend fun getAll(): List<AuditEntry>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AuditEntry>

    @Query("SELECT * FROM audit_log WHERE targetPackage = :packageName ORDER BY timestamp DESC")
    suspend fun getByPackage(packageName: String): List<AuditEntry>

    @Query("DELETE FROM audit_log")
    suspend fun deleteAll()
}
