package com.voiceos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.voiceos.ui.theme.*

/**
 * ControlPanel — Phase 2 AI control and monitoring composable.
 *
 * Contains:
 *   [AiModeCard]            — toggle for AI mode + wake-word hint
 *   [ContinuousListenCard]  — toggle for always-on listening
 *   [CommandLogCard]        — animated chip showing the last executed command
 *   [ControlPanel]          — groups all three cards together
 */

// ── ControlPanel (group) ──────────────────────────────────────────────────

/**
 * Renders the full control panel section.
 *
 * @param isAiModeEnabled       current AI mode state
 * @param isContinuousListening current continuous listening state
 * @param isListening           mic is actively recording
 * @param isProcessing          AI is routing/executing a command
 * @param lastCommand           text of the most recently executed command
 */
@Composable
fun ControlPanel(
    isAiModeEnabled: Boolean,
    isContinuousListening: Boolean,
    isListening: Boolean,
    isProcessing: Boolean,
    lastCommand: String,
    onToggleAiMode: () -> Unit,
    onToggleContinuousListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {

        SectionLabel(text = "AI Controls", modifier = Modifier.padding(horizontal = 4.dp))

        // AI Mode toggle
        AiModeCard(
            isEnabled = isAiModeEnabled,
            onToggle = onToggleAiMode
        )

        // Continuous listening toggle
        ContinuousListenCard(
            isEnabled = isContinuousListening,
            onToggle = onToggleContinuousListening
        )

        // Status indicator row
        AssistantStatusRow(
            isListening = isListening,
            isProcessing = isProcessing
        )

        // Last command log
        if (lastCommand.isNotEmpty()) {
            CommandLogCard(lastCommand = lastCommand)
        }
    }
}

// ── AiModeCard ────────────────────────────────────────────────────────────

@Composable
fun AiModeCard(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isEnabled)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(400),
        label = "aiCardColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "AI Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            AnimatedVisibility(
                visible = isEnabled,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Say \"Hey VoiceOS\" then your command",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, start = 32.dp)
                )
            }
            AnimatedVisibility(
                visible = !isEnabled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "Tap the mic bubble to speak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 32.dp)
                )
            }
        }
    }
}

// ── ContinuousListenCard ──────────────────────────────────────────────────

@Composable
fun ContinuousListenCard(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.HearingDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Continuous Listening",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isEnabled) "Always listening for commands"
                    else "Manual tap-to-talk mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

// ── AssistantStatusRow ────────────────────────────────────────────────────

/**
 * Compact animated status row showing Listening / Processing indicator chips.
 */
@Composable
fun AssistantStatusRow(
    isListening: Boolean,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusChip(
            label = "Listening",
            isActive = isListening,
            activeColor = MicListening,
            icon = Icons.Default.Mic
        )
        StatusChip(
            label = "Processing",
            isActive = isProcessing,
            activeColor = MicProcessing,
            icon = Icons.Default.AutoAwesome
        )
    }
}

@Composable
fun StatusChip(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeColor.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "chipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) activeColor
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "chipText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) activeColor
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(300),
        label = "chipBorder"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Pulsing dot when active
        if (isActive) {
            val pulse = rememberInfiniteTransition(label = "pulse")
            val alpha by pulse.animateFloat(
                initialValue = 0.4f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(activeColor.copy(alpha = alpha))
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = textColor
        )
    }
}

// ── CommandLogCard ────────────────────────────────────────────────────────

/**
 * Shows the last executed voice command text in a styled card.
 */
@Composable
fun CommandLogCard(
    lastCommand: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = "Last command",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "\"$lastCommand\"",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0F1117)
@Composable
private fun ControlPanelPreview() {
    VoiceOSTheme(darkTheme = true) {
        Column(modifier = Modifier.padding(16.dp)) {
            ControlPanel(
                isAiModeEnabled = true,
                isContinuousListening = false,
                isListening = true,
                isProcessing = false,
                lastCommand = "send hello to Riya",
                onToggleAiMode = {},
                onToggleContinuousListening = {}
            )
        }
    }
}
