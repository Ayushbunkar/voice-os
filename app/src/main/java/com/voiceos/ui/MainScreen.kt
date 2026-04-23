package com.voiceos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceos.ui.theme.*

@Composable
fun MainScreen(
    uiState: MainViewModel.UiState,
    onGrantMic: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit,
    onToggleAiMode: () -> Unit,
    onToggleContinuousListening: () -> Unit,
    onLaunch: () -> Unit,
    onStopAssistant: () -> Unit,
    onOpenMacros: () -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. App Logo & Title
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "VoiceOS",
                style = MaterialTheme.typography.displayMedium.copy(
                    brush = Brush.horizontalGradient(listOf(Blue400, Blue600))
                ),
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "AI Voice Automation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // 2. Status Indicator
            ReadyStatusChip(allGranted = uiState.allPermissionsGranted)

            Spacer(Modifier.height(24.dp))

            // 3. Main Action Hub (Logical grouping)
            PermissionSection(
                isMicGranted = uiState.isMicGranted,
                isAccessibilityOk = uiState.isAccessibilityEnabled,
                isOverlayGranted = uiState.isOverlayGranted,
                onGrantMic = onGrantMic,
                onOpenAccessibility = onOpenAccessibility,
                onGrantOverlay = onGrantOverlay
            )

            Spacer(Modifier.height(16.dp))

            ControlPanel(
                isAiModeEnabled = uiState.isAiModeEnabled,
                isContinuousListening = uiState.isContinuousListening,
                isListening = uiState.isListening,
                isProcessing = uiState.isProcessing,
                lastCommand = uiState.lastCommand,
                onToggleAiMode = onToggleAiMode,
                onToggleContinuousListening = onToggleContinuousListening
            )

            Spacer(Modifier.height(24.dp))

            // 4. Primary Launch Button (Centered and large)
            Button(
                onClick = onLaunch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                enabled = uiState.allPermissionsGranted,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.RocketLaunch, null)
                Spacer(Modifier.width(12.dp))
                Text("Start Assistant", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))

            // 5. Emergency Stop & Exit Button
            OutlinedButton(
                onClick = { 
                    onStopAssistant()
                    onExitApp()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PowerSettingsNew, null)
                Spacer(Modifier.width(12.dp))
                Text("Shut Down & Exit", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            
            // Minimalist Macro link
            TextButton(onClick = onOpenMacros) {
                Icon(Icons.Default.FlashOn, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Automation Macros", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ReadyStatusChip(allGranted: Boolean) {
    val color = if (allGranted) GreenOk else AmberWarning
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (allGranted) "Ready to Go" else "Setup Required",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
