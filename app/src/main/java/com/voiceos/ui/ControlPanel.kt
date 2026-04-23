package com.voiceos.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voiceos.ui.theme.*
import com.voiceos.utils.PerfSnapshot

/**
 * Optimized Control Panel — Lightweight and clean.
 */
@Composable
fun ControlPanel(
    isAiModeEnabled: Boolean,
    isContinuousListening: Boolean,
    isListening: Boolean,
    isProcessing: Boolean,
    lastCommand: String,
    showPerformancePanel: Boolean = false,
    performanceSnapshot: PerfSnapshot? = null,
    onToggleAiMode: () -> Unit,
    onToggleContinuousListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        
        // Unified Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                // AI Mode Row
                ControlRow(
                    icon = Icons.Default.Psychology,
                    title = "AI Smart Mode",
                    subtitle = if (isAiModeEnabled) "Listening for \"Hey VoiceOS\"" else "Manual Mode",
                    checked = isAiModeEnabled,
                    onCheckedChange = { onToggleAiMode() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Continuous Row
                ControlRow(
                    icon = Icons.Default.Hearing,
                    title = "Always Listening",
                    subtitle = if (isContinuousListening) "Continuous Active" else "Tap to speak",
                    checked = isContinuousListening,
                    onCheckedChange = { onToggleContinuousListening() }
                )
            }
        }

        // Live Status Row (Compact)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusIndicator(
                label = "Listening",
                isActive = isListening,
                activeColor = MicListening,
                modifier = Modifier.weight(1f)
            )
            StatusIndicator(
                label = "Processing",
                isActive = isProcessing,
                activeColor = MicProcessing,
                modifier = Modifier.weight(1f)
            )
        }

        // Animated Last Command Log
        AnimatedVisibility(
            visible = lastCommand.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            CommandLogCard(lastCommand = lastCommand)
        }
    }
}

@Composable
private fun ControlRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
private fun StatusIndicator(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (isActive) activeColor else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isActive) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(activeColor))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun CommandLogCard(lastCommand: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.KeyboardVoice, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "\"$lastCommand\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}
