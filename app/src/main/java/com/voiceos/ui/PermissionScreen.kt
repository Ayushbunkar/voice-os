package com.voiceos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiceos.ui.theme.*

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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PermissionRow(
                icon = Icons.Default.Mic,
                title = "Microphone",
                isGranted = isMicGranted,
                onClick = onGrantMic
            )
            PermissionRow(
                icon = Icons.Default.Accessibility,
                title = "Accessibility",
                isGranted = isAccessibilityOk,
                onClick = onOpenAccessibility
            )
            PermissionRow(
                icon = Icons.Default.Layers,
                title = "Overlay",
                isGranted = isOverlayGranted,
                onClick = onGrantOverlay
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isGranted) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) GreenOk else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isGranted) FontWeight.Normal else FontWeight.SemiBold,
            color = if (isGranted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isGranted) {
            Icon(Icons.Default.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(18.dp))
        } else {
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Setup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}
