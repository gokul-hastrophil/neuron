@file:Suppress("ktlint:standard:function-naming")

package ai.neuron.character.ui

import ai.neuron.character.model.EmotionState
import ai.neuron.character.model.VoiceProfile
import ai.neuron.character.renderer.CharacterRenderView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

/**
 * Character customization screen — name, voice, personality sliders.
 * Live character preview at top reflects changes in real-time.
 */
@Composable
fun CharacterCustomizationScreen(
    uiState: CharacterUiState,
    emotionState: StateFlow<EmotionState>,
    onNameChange: (String) -> Unit,
    onHumorChange: (Float) -> Unit,
    onFormalityChange: (Float) -> Unit,
    onEmpathyChange: (Float) -> Unit,
    onCuriosityChange: (Float) -> Unit,
    onVoiceSelect: (VoiceProfile) -> Unit,
    onSave: () -> Unit,
) {
    val emotion by emotionState.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        // Character preview
        if (uiState.selectedType != null) {
            CharacterRenderView(
                characterType = uiState.selectedType,
                emotion = emotion,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Name
        Text("Name", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Voice selection
        Text("Voice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            VoiceProfile.DEFAULTS.forEach { profile ->
                FilterChip(
                    selected = uiState.voiceProfileId == profile.id,
                    onClick = { onVoiceSelect(profile) },
                    label = { Text(profile.displayName) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Personality sliders
        Text("Personality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        PersonalitySlider("Humor", uiState.humor, onHumorChange)
        PersonalitySlider("Formality", uiState.formality, onFormalityChange)
        PersonalitySlider("Empathy", uiState.empathy, onEmpathyChange)
        PersonalitySlider("Curiosity", uiState.curiosity, onCuriosityChange)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PersonalitySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp),
        )
    }
}
