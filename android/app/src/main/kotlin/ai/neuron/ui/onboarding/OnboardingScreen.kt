package ai.neuron.ui.onboarding

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Onboarding flow: Welcome → Grant Accessibility → Test Command → Done
 */
@Composable
fun OnboardingScreen(
    isAccessibilityEnabled: Boolean,
    onComplete: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (step) {
            0 -> WelcomeStep(onNext = { step = 1 })
            1 -> PermissionsStep(
                isEnabled = isAccessibilityEnabled,
                onOpenSettings = { openAccessibilitySettings(context) },
                onNext = { step = 2 },
            )
            2 -> TestCommandStep(onNext = { step = 3 })
            3 -> DoneStep(onComplete = onComplete)
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Text(
        text = "Welcome to Neuron",
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Your AI-powered phone assistant. Control any app with natural language.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text("Get Started")
    }
}

@Composable
private fun PermissionsStep(
    isEnabled: Boolean,
    onOpenSettings: () -> Unit,
    onNext: () -> Unit,
) {
    Text(
        text = "Accessibility Permission",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "What Neuron reads:",
                style = MaterialTheme.typography.titleSmall,
            )
            Text("- UI element IDs, text labels, and positions")
            Text("- Screen layout to understand what's visible")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "What Neuron does NOT do:",
                style = MaterialTheme.typography.titleSmall,
            )
            Text("- Never reads password field contents")
            Text("- Never sends banking screen data to cloud")
            Text("- Never stores screenshots of sensitive apps")
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    if (isEnabled) {
        Text(
            text = "Accessibility service is enabled",
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    } else {
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open Accessibility Settings")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun TestCommandStep(onNext: () -> Unit) {
    Text(
        text = "Try a Command",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Try saying or typing: \"Open Settings\"",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))
    OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text("Skip Test")
    }
}

@Composable
private fun DoneStep(onComplete: () -> Unit) {
    Text(
        text = "You're all set!",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Neuron is ready. Use the floating bubble or voice to give commands.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
        Text("Start Using Neuron")
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
