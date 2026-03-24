@file:Suppress("ktlint:standard:function-naming")

package ai.neuron.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainScreen(
    isAccessibilityEnabled: Boolean,
    isMicrophoneGranted: Boolean,
    onOpenSettings: () -> Unit,
    onRunOnboarding: () -> Unit,
    onRequestMicrophone: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Neuron",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "AI Agent for Android",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- Activation Status Card ---
        ActivationCard(
            isAccessibilityEnabled = isAccessibilityEnabled,
            isMicrophoneGranted = isMicrophoneGranted,
            onEnableAccessibility = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    },
                )
            },
            onGrantMicrophone = onRequestMicrophone,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- How to Use ---
        if (isAccessibilityEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How to Use",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HowToRow(step = "1", text = "Press and hold the floating bubble")
                    HowToRow(step = "2", text = "Speak your command")
                    HowToRow(step = "3", text = "Release to execute")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try: \"Open calculator\" or \"Open Chrome\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Buttons ---
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onRunOnboarding, modifier = Modifier.fillMaxWidth()) {
            Text("Run Onboarding Again")
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun ActivationCard(
    isAccessibilityEnabled: Boolean,
    isMicrophoneGranted: Boolean,
    onEnableAccessibility: () -> Unit,
    onGrantMicrophone: () -> Unit,
) {
    val allEnabled = isAccessibilityEnabled && isMicrophoneGranted

    val cardColor by animateColorAsState(
        targetValue = if (allEnabled) Color(0xFF1B5E20) else MaterialTheme.colorScheme.errorContainer,
        label = "cardColor",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (allEnabled) {
                // Active state — pulsing green dot
                val transition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.3f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse,
                        ),
                    label = "dotPulse",
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(12.dp)
                                .scale(pulseScale)
                                .background(Color(0xFF69F0AE), CircleShape),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Neuron is Active",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The floating bubble is ready. Hold it to speak.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xCCFFFFFF),
                    textAlign = TextAlign.Center,
                )
            } else {
                // Inactive state
                Text(
                    text = "Neuron is Inactive",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Accessibility service toggle
                PermissionRow(
                    label = "Accessibility Service",
                    granted = isAccessibilityEnabled,
                    buttonText = "Enable",
                    onAction = onEnableAccessibility,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Microphone permission
                PermissionRow(
                    label = "Microphone Permission",
                    granted = isMicrophoneGranted,
                    buttonText = "Grant",
                    onAction = onGrantMicrophone,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    buttonText: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(
                        if (granted) Color(0xFF69F0AE) else Color(0xFFFF8A80),
                        CircleShape,
                    ),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        if (!granted) {
            Button(
                onClick = onAction,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(buttonText, fontSize = 13.sp)
            }
        } else {
            Text(
                text = "\u2713",
                color = Color(0xFF69F0AE),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HowToRow(
    step: String,
    text: String,
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Text(
                text = step,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
