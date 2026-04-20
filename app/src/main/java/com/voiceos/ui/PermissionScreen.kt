package com.voiceos.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * PermissionScreen — Reusable permission card composables.
 *
 * Exposes:
 *   [PermissionCard]     — full card with icon, title, desc, status dot, button
 *   [StatusDot]          — small animated colour indicator
 *   [PermissionSection]  — groups all three permission cards with a section header
 */

// ── PermissionSection ─────────────────────────────────────────────────────

/**
 * Renders all three required permission cards in a vertical column.
 *
 * @param isMicGranted        whether RECORD_AUDIO permission is held
 * @param isAccessibilityOk   whether the VoiceOS accessibility service is running
 * @param isOverlayGranted    whether SYSTEM_ALERT_WINDOW is granted
 * @param onGrantMic          callback to request mic permission
 * @param onOpenAccessibility callback to open accessibility settings
 * @param onGrantOverlay      callback to open overlay settings
 */
@Composable
fun PermissionSection(
    isMicGranted: Boolean,
    isAccessibilityOk: Boolean,
    isOverlayGranted: Boolean,
    onGrantMic: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onGrantOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {

        SectionLabel(text = "Required Permissions")

        PermissionCard(
            icon = Icons.Default.Mic,
            title = "Microphone",
            description = "Voice command recognition",
            isGranted = isMicGranted,
            buttonLabel = if (isMicGranted) "Granted ✓" else "Grant",
            onAction = onGrantMic
        )

        PermissionCard(
            icon = Icons.Default.Accessibility,
            title = "Accessibility Service",
            description = "Read and control any app on screen",
            isGranted = isAccessibilityOk,
            buttonLabel = if (isAccessibilityOk) "Active ✓" else "Enable",
            onAction = onOpenAccessibility
        )

        PermissionCard(
            icon = Icons.Default.Layers,
            title = "Draw Over Apps",
            description = "Floating mic button + numbered labels",
            isGranted = isOverlayGranted,
            buttonLabel = if (isOverlayGranted) "Allowed ✓" else "Allow",
            onAction = onGrantOverlay
        )
    }
}

// ── Single PermissionCard ─────────────────────────────────────────────────

/**
 * Material3 card displaying one permission item with status and action button.
 */
@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Text column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StatusDot(isGranted = isGranted)
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Action button
            OutlinedButton(
                onClick = onAction,
                enabled = !isGranted,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isGranted) GreenOk else MaterialTheme.colorScheme.primary,
                    disabledContentColor = GreenOk
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ── StatusDot ─────────────────────────────────────────────────────────────

/**
 * Small animated circle: green when granted, red when denied.
 * Colour transitions smoothly via [animateColorAsState].
 */
@Composable
fun StatusDot(isGranted: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = if (isGranted) GreenOk else RedError,
        animationSpec = tween(durationMillis = 400),
        label = "statusDotColor"
    )
    Box(
        modifier = modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color)
    )
}

// ── SectionLabel helper ───────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

// ── Preview ───────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0F1117)
@Composable
private fun PermissionCardPreview() {
    VoiceOSTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PermissionCard(
                icon = Icons.Default.Mic,
                title = "Microphone",
                description = "Voice command recognition",
                isGranted = true,
                buttonLabel = "Granted ✓",
                onAction = {}
            )
            PermissionCard(
                icon = Icons.Default.Accessibility,
                title = "Accessibility Service",
                description = "Read and control any app on screen",
                isGranted = false,
                buttonLabel = "Enable",
                onAction = {}
            )
        }
    }
}
