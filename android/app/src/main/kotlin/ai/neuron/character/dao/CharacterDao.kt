package ai.neuron.character.dao

import ai.neuron.character.model.CharacterState
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(state: CharacterState)

    @Update
    suspend fun update(state: CharacterState)

    @Query("SELECT * FROM character_state WHERE id = 1 LIMIT 1")
    suspend fun getActiveCharacter(): CharacterState?

    @Query("SELECT * FROM character_state WHERE id = 1 LIMIT 1")
    fun observeActiveCharacter(): Flow<CharacterState?>

    @Query("UPDATE character_state SET current_emotion = :emotion WHERE id = 1")
    suspend fun updateEmotion(emotion: String)

    @Query("UPDATE character_state SET personality_json = :json WHERE id = 1")
    suspend fun updatePersonality(json: String)

    @Query("UPDATE character_state SET total_interactions = total_interactions + 1, last_interaction_at = :timestamp WHERE id = 1")
    suspend fun incrementInteraction(timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE character_state SET name = :name WHERE id = 1")
    suspend fun updateName(name: String)

    @Query("UPDATE character_state SET voice_profile_id = :voiceId WHERE id = 1")
    suspend fun updateVoiceProfile(voiceId: String)

    @Query("DELETE FROM character_state")
    suspend fun deleteAll()
}
