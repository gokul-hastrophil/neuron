package ai.neuron.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ai.neuron.input.WakeWordService

data class NeuronSettings(
    val geminiApiKey: String = "",
    val ollamaEndpoint: String = "",
    val ollamaApiKey: String = "",
    val openRouterApiKey: String = "",
    val picovoiceAccessKey: String = "",
    val wakeWordKeyword: String = "JARVIS",
    val wakeWordEnabled: Boolean = true,
    val cloudEnabled: Boolean = true,
    val sensitiveAppsOverrides: Set<String> = emptySet(),
)

@Composable
fun SettingsScreen(
    settings: NeuronSettings,
    onSettingsChanged: (NeuronSettings) -> Unit,
    onClearMemory: () -> Unit,
    onViewAuditLog: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text("\u2190 Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- API Keys Section ---
        SectionHeader("API Keys")

        ApiKeyField(
            label = "Gemini API Key",
            value = settings.geminiApiKey,
            onValueChange = { onSettingsChanged(settings.copy(geminiApiKey = it)) },
        )

        ApiKeyField(
            label = "OpenRouter API Key",
            value = settings.openRouterApiKey,
            onValueChange = { onSettingsChanged(settings.copy(openRouterApiKey = it)) },
        )

        ApiKeyField(
            label = "Ollama API Key",
            value = settings.ollamaApiKey,
            onValueChange = { onSettingsChanged(settings.copy(ollamaApiKey = it)) },
        )

        OutlinedTextField(
            value = settings.ollamaEndpoint,
            onValueChange = { onSettingsChanged(settings.copy(ollamaEndpoint = it)) },
            label = { Text("Ollama Endpoint") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- Privacy Section ---
        SectionHeader("Privacy")

        SettingsToggle(
            title = "Cloud LLM",
            subtitle = "Allow sending non-sensitive data to cloud LLMs (Gemini, OpenRouter)",
            checked = settings.cloudEnabled,
            onCheckedChange = { onSettingsChanged(settings.copy(cloudEnabled = it)) },
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- Wake Word Section ---
        SectionHeader("Wake Word")

        SettingsToggle(
            title = "Wake Word Detection",
            subtitle = "Say a keyword to activate Neuron hands-free",
            checked = settings.wakeWordEnabled,
            onCheckedChange = { onSettingsChanged(settings.copy(wakeWordEnabled = it)) },
        )

        ApiKeyField(
            label = "Picovoice Access Key",
            value = settings.picovoiceAccessKey,
            onValueChange = { onSettingsChanged(settings.copy(picovoiceAccessKey = it)) },
        )

        WakeWordSelector(
            selectedKeyword = settings.wakeWordKeyword,
            onKeywordSelected = { onSettingsChanged(settings.copy(wakeWordKeyword = it)) },
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- Memory Section ---
        SectionHeader("Memory")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showClearDialog = true },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Clear All Memory", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Delete all stored preferences, workflows, and contacts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // --- Audit Log ---
        SectionHeader("Audit")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onViewAuditLog() },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("View Audit Log", style = MaterialTheme.typography.titleSmall)
                Text(
                    "See all past actions with timestamps and target apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Memory?") },
            text = { Text("This will permanently delete all learned preferences, cached workflows, and contact associations.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearMemory()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun WakeWordSelector(
    selectedKeyword: String,
    onKeywordSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val keywords = WakeWordService.AVAILABLE_KEYWORDS

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Wake Word",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedKeyword,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "\u25B2" else "\u25BC",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (expanded) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    keywords.forEach { keyword ->
                        Text(
                            text = keyword,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onKeywordSelected(keyword)
                                    expanded = false
                                }
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (keyword == selectedKeyword) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
