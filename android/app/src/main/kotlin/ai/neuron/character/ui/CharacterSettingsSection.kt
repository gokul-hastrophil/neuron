@file:Suppress("ktlint:standard:function-naming")

package ai.neuron.character.ui

import ai.neuron.character.model.EmotionState
import ai.neuron.character.renderer.CharacterRenderView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings section showing current character info, stats, and navigation to gallery/customization.
 */
@Composable
fun CharacterSettingsSection(
    uiState: CharacterUiState,
    emotion: EmotionState,
    onChangeCharacter: () -> Unit,
    onCustomize: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "My Character",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.selectedType != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterRenderView(
                        characterType = uiState.selectedType,
                        emotion = emotion,
                        modifier =
                            Modifier
                                .width(80.dp)
                                .height(80.dp),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = uiState.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = uiState.selectedType.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${uiState.totalInteractions} interactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = "No character selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row {
                OutlinedButton(onClick = onChangeCharacter) {
                    Text("Change Character")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onCustomize) {
                    Text("Customize")
                }
            }
        }
    }
}
