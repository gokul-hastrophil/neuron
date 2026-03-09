package ai.neuron.memory.dao

import ai.neuron.memory.entity.UserPreference
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preference: UserPreference): Long

    @Update
    suspend fun update(preference: UserPreference)

    @Delete
    suspend fun delete(preference: UserPreference)

    @Query("SELECT * FROM user_preferences WHERE category = :category AND `key` = :key LIMIT 1")
    suspend fun findByKey(category: String, key: String): UserPreference?

    @Query("SELECT * FROM user_preferences WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun findByCategory(category: String): List<UserPreference>

    @Query("SELECT * FROM user_preferences ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<UserPreference>>

    @Query("SELECT * FROM user_preferences ORDER BY updatedAt DESC")
    suspend fun getAll(): List<UserPreference>

    @Query("DELETE FROM user_preferences")
    suspend fun deleteAll()
}
