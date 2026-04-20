package com.voiceos.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceos.ui.theme.*

/**
 * MainScreen — Top-level Compose screen for VoiceOS.
 *
 * Layout (scrollable column):
 *   1. Animated gradient hero header
 *   2. Ready status chip
 *   3. Permission section  (PermissionScreen.kt)
 *   4. Control panel       (ControlPanel.kt)
 *   5. Macros shortcut card
 *   6. Launch button
 *   7. Example commands footer
 */
@Composable
fun MainScreen(
    uiState: MainViewModel.UiState,
    onGrantMic: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit,
    onToggleAiMode: () -> Unit,
    onToggleContinuousListening: () -> Unit,
    onLaunch: () -> Unit,
    onOpenMacros: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        Spacer(Modifier.height(48.dp))

        // ── 1. Hero Header ─────────────────────────────────────────────
        HeroHeader()

        Spacer(Modifier.height(20.dp))

        // ── 2. Ready-status chip ───────────────────────────────────────
        ReadyStatusChip(allGranted = uiState.allPermissionsGranted)

        Spacer(Modifier.height(20.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Spacer(Modifier.height(20.dp))

        // ── 3. Permission section ──────────────────────────────────────
        PermissionSection(
            isMicGranted = uiState.isMicGranted,
            isAccessibilityOk = uiState.isAccessibilityEnabled,
            isOverlayGranted = uiState.isOverlayGranted,
            onGrantMic = onGrantMic,
            onOpenAccessibility = onOpenAccessibility,
            onGrantOverlay = onGrantOverlay
        )

        Spacer(Modifier.height(24.dp))

        // ── 4. Control panel ───────────────────────────────────────────
        ControlPanel(
            isAiModeEnabled = uiState.isAiModeEnabled,
            isContinuousListening = uiState.isContinuousListening,
            isListening = uiState.isListening,
            isProcessing = uiState.isProcessing,
            lastCommand = uiState.lastCommand,
            onToggleAiMode = onToggleAiMode,
            onToggleContinuousListening = onToggleContinuousListening
        )

        Spacer(Modifier.height(12.dp))

        // ── 5. Macros shortcut card ────────────────────────────────────
        MacrosCard(onClick = onOpenMacros)

        Spacer(Modifier.height(24.dp))

        // ── 6. Launch button ───────────────────────────────────────────
        LaunchButton(
            allPermissionsGranted = uiState.allPermissionsGranted,
            onClick = onLaunch
        )

        Spacer(Modifier.height(16.dp))

        // ── 7. Example commands strip ──────────────────────────────────
        ExampleCommandsRow()
    }
}

// ── HeroHeader ────────────────────────────────────────────────────────────

@Composable
private fun HeroHeader() {
    // Infinite floating glow animation on the icon
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation   = tween(1800, easing = EaseInOut),
            repeatMode  = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Gradient glow layer behind the icon
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Blue500.copy(alpha = glowAlpha * 0.5f),
                                Color.Transparent
                            ),
                            center = Offset.Unspecified,
                            radius = 120f
                        )
                    )
            )
            Icon(
                imageVector = Icons.Default.RecordVoiceOver,
                contentDescription = "VoiceOS",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        // App title
        Text(
            text = "VoiceOS",
            style = MaterialTheme.typography.displayLarge.copy(
                brush = Brush.horizontalGradient(
                    colors = listOf(Blue400, Blue500, Blue600)
                )
            ),
            fontWeight = FontWeight.ExtraBold
        )

        Text(
            text = "AI Voice Automation System",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Text(
            text = "Phase 2",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

// ── ReadyStatusChip ───────────────────────────────────────────────────────

@Composable
private fun ReadyStatusChip(allGranted: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (allGranted) GreenOk.copy(0.15f) else AmberWarning.copy(0.12f),
        animationSpec = tween(500),
        label = "statusChipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (allGranted) GreenOk else AmberWarning,
        animationSpec = tween(500),
        label = "statusChipText"
    )
    val icon = if (allGranted) Icons.Default.CheckCircle else Icons.Default.Warning
    val label = if (allGranted) "✅  Ready to launch!" else "⚠  Complete setup below"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor
        )
    }
}

// ── MacrosCard ────────────────────────────────────────────────────────────

@Composable
private fun MacrosCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FlashOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Automation Macros",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Create and run multi-step automations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open macros",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── LaunchButton ──────────────────────────────────────────────────────────

@Composable
private fun LaunchButton(
    allPermissionsGranted: Boolean,
    onClick: () -> Unit
) {
    // Subtle scale animation while the button is visible
    val scale by animateFloatAsState(
        targetValue = if (allPermissionsGranted) 1f else 0.96f,
        animationSpec = tween(300),
        label = "launchScale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = allPermissionsGranted,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.RocketLaunch,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Launch VoiceOS Assistant",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold
        )
    }
}

// ── ExampleCommandsRow ────────────────────────────────────────────────────

@Composable
private fun ExampleCommandsRow() {
    val examples = listOf(
        "\"send hi to Riya\"",
        "\"scroll down\"",
        "\"click 3\"",
        "\"good morning\""
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Try saying…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            examples.take(2).forEach { cmd ->
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            examples.drop(2).forEach { cmd ->
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = cmd,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainScreenPreview() {
    VoiceOSTheme(darkTheme = true) {
        MainScreen(
            uiState = MainViewModel.UiState(
                isMicGranted = true,
                isAccessibilityEnabled = true,
                isOverlayGranted = false,
                isAiModeEnabled = true,
                lastCommand = "send hello to Riya"
            ),
            onGrantMic = {},
            onOpenAccessibility = {},
            onGrantOverlay = {},
            onToggleAiMode = {},
            onToggleContinuousListening = {},
            onLaunch = {},
            onOpenMacros = {}
        )
    }
}
