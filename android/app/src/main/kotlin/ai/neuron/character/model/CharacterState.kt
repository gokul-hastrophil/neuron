package ai.neuron.character.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted character state in Room DB.
 * Only one active character at a time (id = 1 singleton row).
 */
@Entity(tableName = "character_state")
data class CharacterState(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "selected_type") val selectedType: String = CharacterType.DEFAULT.name,
    val name: String = CharacterType.DEFAULT.defaultPersonality.name,
    @ColumnInfo(name = "personality_json") val personalityJson: String = "",
    @ColumnInfo(name = "current_emotion") val currentEmotion: String = EmotionState.NEUTRAL.name,
    @ColumnInfo(name = "voice_profile_id") val voiceProfileId: String = VoiceProfile.CALM.id,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "total_interactions") val totalInteractions: Int = 0,
    @ColumnInfo(name = "last_interaction_at") val lastInteractionAt: Long = 0,
) {
    fun getCharacterType(): CharacterType = CharacterType.fromString(selectedType)

    fun getEmotionState(): EmotionState = EmotionState.fromString(currentEmotion)

    fun getVoiceProfile(): VoiceProfile = VoiceProfile.fromId(voiceProfileId)

    fun getPersonalityProfile(): PersonalityProfile =
        if (personalityJson.isNotBlank()) {
            try {
                PersonalityProfile.fromJson(personalityJson)
            } catch (_: Exception) {
                getCharacterType().defaultPersonality.copy(name = name)
            }
        } else {
            getCharacterType().defaultPersonality.copy(name = name)
        }
}
