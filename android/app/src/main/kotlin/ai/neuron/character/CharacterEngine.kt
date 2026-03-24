package ai.neuron.character

import ai.neuron.character.dao.CharacterDao
import ai.neuron.character.model.CharacterState
import ai.neuron.character.model.CharacterType
import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.PersonalityProfile
import ai.neuron.character.model.VoiceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the character's lifecycle, state transitions, and personality.
 * Single source of truth for the active character.
 */
@Singleton
class CharacterEngine
    @Inject
    constructor(
        private val characterDao: CharacterDao,
        private val scope: CoroutineScope,
    ) {
        private val _characterState = MutableStateFlow<CharacterState?>(null)
        val characterState: StateFlow<CharacterState?> = _characterState.asStateFlow()

        private val _emotionState = MutableStateFlow(EmotionState.NEUTRAL)
        val emotionState: StateFlow<EmotionState> = _emotionState.asStateFlow()

        private val emotionHistory = mutableListOf<EmotionEntry>()
        private var decayJob: Job? = null
        private var debounceJob: Job? = null

        companion object {
            private const val EMOTION_DECAY_MS = 30_000L
            private const val DEBOUNCE_MS = 100L
            private const val MAX_HISTORY = 10
        }

        /** Load existing character from Room, if any. */
        fun initialize() {
            scope.launch {
                val existing = characterDao.getActiveCharacter()
                if (existing != null) {
                    _characterState.value = existing
                    _emotionState.value = existing.getEmotionState()
                }
            }
        }

        /**
         * Set the character's current emotion with debounce.
         * Rapid calls within [DEBOUNCE_MS] only apply the last value.
         * Non-neutral emotions auto-decay to NEUTRAL after [EMOTION_DECAY_MS].
         */
        fun setEmotion(
            emotion: EmotionState,
            reason: String,
        ) {
            debounceJob?.cancel()
            debounceJob =
                scope.launch {
                    delay(DEBOUNCE_MS)
                    _emotionState.value = emotion
                    addToHistory(emotion, reason)

                    // Persist if character exists
                    if (_characterState.value != null) {
                        characterDao.updateEmotion(emotion.name)
                    }

                    // Schedule decay to neutral
                    scheduleDecay(emotion)
                }
        }

        /**
         * Select a character type. Creates a new CharacterState in Room
         * with defaults from the type. Resets emotion to neutral.
         */
        fun selectCharacter(type: CharacterType) {
            scope.launch {
                val personality = type.defaultPersonality
                val state =
                    CharacterState(
                        selectedType = type.name,
                        name = personality.name,
                        personalityJson = personality.toJson(),
                        currentEmotion = EmotionState.NEUTRAL.name,
                        voiceProfileId = type.defaultVoiceProfile.id,
                        createdAt = System.currentTimeMillis(),
                    )
                characterDao.insertOrReplace(state)
                _characterState.value = state
                _emotionState.value = EmotionState.NEUTRAL
                decayJob?.cancel()
            }
        }

        /** Save an updated personality profile for the active character. */
        fun savePersonality(profile: PersonalityProfile) {
            scope.launch {
                val json = profile.toJson()
                characterDao.updatePersonality(json)
                _characterState.value = _characterState.value?.copy(personalityJson = json)
            }
        }

        /** Get the active character's personality, or null if no character. */
        fun getPersonality(): PersonalityProfile? {
            return _characterState.value?.getPersonalityProfile()
        }

        /** Record a user interaction (increments counter, updates timestamp). */
        fun recordInteraction() {
            scope.launch {
                val timestamp = System.currentTimeMillis()
                characterDao.incrementInteraction(timestamp)
                _characterState.value?.let { current ->
                    _characterState.value =
                        current.copy(
                            totalInteractions = current.totalInteractions + 1,
                            lastInteractionAt = timestamp,
                        )
                }
            }
        }

        /** Update the character's display name. */
        fun updateName(name: String) {
            scope.launch {
                characterDao.updateName(name)
                _characterState.value = _characterState.value?.copy(name = name)
            }
        }

        /** Update the character's voice profile. */
        fun updateVoiceProfile(voice: VoiceProfile) {
            scope.launch {
                characterDao.updateVoiceProfile(voice.id)
                _characterState.value = _characterState.value?.copy(voiceProfileId = voice.id)
            }
        }

        /** Return the last N emotion transitions (max [MAX_HISTORY]). */
        fun getEmotionHistory(): List<EmotionEntry> = emotionHistory.toList()

        private fun addToHistory(
            emotion: EmotionState,
            reason: String,
        ) {
            emotionHistory.add(EmotionEntry(emotion, reason, System.currentTimeMillis()))
            if (emotionHistory.size > MAX_HISTORY) {
                emotionHistory.removeAt(0)
            }
        }

        private fun scheduleDecay(emotion: EmotionState) {
            decayJob?.cancel()
            if (emotion == EmotionState.NEUTRAL) return
            decayJob =
                scope.launch {
                    delay(EMOTION_DECAY_MS)
                    _emotionState.value = EmotionState.NEUTRAL
                    if (_characterState.value != null) {
                        characterDao.updateEmotion(EmotionState.NEUTRAL.name)
                    }
                }
        }
    }

/** A recorded emotion transition. */
data class EmotionEntry(
    val emotion: EmotionState,
    val reason: String,
    val timestamp: Long,
)
