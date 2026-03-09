package ai.neuron.memory.dao

import ai.neuron.memory.entity.AppWorkflow
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AppWorkflowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workflow: AppWorkflow): Long

    @Update
    suspend fun update(workflow: AppWorkflow)

    @Delete
    suspend fun delete(workflow: AppWorkflow)

    @Query("SELECT * FROM app_workflows WHERE packageName = :packageName AND taskType = :taskType LIMIT 1")
    suspend fun findByPackageAndTask(packageName: String, taskType: String): AppWorkflow?

    @Query("SELECT * FROM app_workflows WHERE packageName = :packageName ORDER BY lastUsed DESC")
    suspend fun findByPackage(packageName: String): List<AppWorkflow>

    @Query("SELECT * FROM app_workflows ORDER BY successCount DESC LIMIT :limit")
    suspend fun getMostSuccessful(limit: Int = 20): List<AppWorkflow>

    @Query("SELECT * FROM app_workflows ORDER BY lastUsed DESC")
    suspend fun getAll(): List<AppWorkflow>

    @Query("DELETE FROM app_workflows")
    suspend fun deleteAll()
}
