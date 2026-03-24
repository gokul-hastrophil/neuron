package ai.neuron.character.ui

import ai.neuron.character.CharacterEngine
import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.PersonalityProfile
import ai.neuron.character.model.VoiceProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI state for character gallery, customization, and display.
 */
data class CharacterUiState(
    val selectedType: CharacterType? = null,
    val name: String = "",
    val emotion: EmotionState = EmotionState.NEUTRAL,
    val humor: Float = 0.6f,
    val formality: Float = 0.3f,
    val empathy: Float = 0.8f,
    val curiosity: Float = 0.7f,
    val sassiness: Float = 0.4f,
    val protectiveness: Float = 0.6f,
    val voiceProfileId: String = VoiceProfile.CALM.id,
    val totalInteractions: Int = 0,
)

/**
 * ViewModel for character gallery and customization screens.
 * Bridges [CharacterEngine] state to Compose UI.
 */
class CharacterViewModel(
    private val engine: CharacterEngine,
) {
    private val _uiState = MutableStateFlow(CharacterUiState())
    val uiState: StateFlow<CharacterUiState> = _uiState.asStateFlow()

    val availableCharacters: List<CharacterType> = CharacterType.entries

    /** Live emotion state from the engine (separate from uiState for real-time updates). */
    val emotionState: StateFlow<EmotionState> = engine.emotionState

    init {
        syncFromEngine()
    }

    fun selectCharacter(type: CharacterType) {
        engine.selectCharacter(type)
        _uiState.value =
            _uiState.value.copy(
                selectedType = type,
                name = type.defaultPersonality.name,
                humor = type.defaultPersonality.humor,
                formality = type.defaultPersonality.formality,
                empathy = type.defaultPersonality.empathy,
                curiosity = type.defaultPersonality.curiosity,
                sassiness = type.defaultPersonality.sassiness,
                protectiveness = type.defaultPersonality.protectiveness,
                voiceProfileId = type.defaultVoiceProfile.id,
                emotion = EmotionState.NEUTRAL,
            )
    }

    fun updateName(name: String) {
        engine.updateName(name)
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateHumor(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(humor = clamped)
        savePersonality()
    }

    fun updateFormality(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(formality = clamped)
        savePersonality()
    }

    fun updateEmpathy(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(empathy = clamped)
        savePersonality()
    }

    fun updateCuriosity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _uiState.value = _uiState.value.copy(curiosity = clamped)
        savePersonality()
    }

    fun updateVoiceProfile(profile: VoiceProfile) {
        engine.updateVoiceProfile(profile)
        _uiState.value = _uiState.value.copy(voiceProfileId = profile.id)
    }

    private fun syncFromEngine() {
        val state = engine.characterState.value
        val emotion = engine.emotionState.value
        if (state != null) {
            val profile = state.getPersonalityProfile()
            _uiState.value =
                CharacterUiState(
                    selectedType = state.getCharacterType(),
                    name = state.name,
                    emotion = emotion,
                    humor = profile.humor,
                    formality = profile.formality,
                    empathy = profile.empathy,
                    curiosity = profile.curiosity,
                    sassiness = profile.sassiness,
                    protectiveness = profile.protectiveness,
                    voiceProfileId = state.voiceProfileId,
                    totalInteractions = state.totalInteractions,
                )
        } else {
            _uiState.value = CharacterUiState(emotion = emotion)
        }
    }

    private fun savePersonality() {
        val ui = _uiState.value
        val profile =
            PersonalityProfile(
                name = ui.name.ifBlank { "Neuron" },
                humor = ui.humor,
                formality = ui.formality,
                empathy = ui.empathy,
                curiosity = ui.curiosity,
                sassiness = ui.sassiness,
                protectiveness = ui.protectiveness,
            )
        engine.savePersonality(profile)
    }
}
