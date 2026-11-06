package com.keyforge.chat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keyforge.chat.feature.chat.FileCardState
import com.keyforge.chat.feature.chat.FileStatus
import com.keyforge.chat.ui.theme.spacing

/**
 * File card (section 5.3): icon by type, name, size, status chip, and actions.
 * Download stays disabled until status == VERIFIED (section 5.4-E). For ZIPs,
 * an expandable tree is shown (here: a row count, expandable in a later pass).
 */
@Composable
fun FileCard(
    card: FileCardState,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onPreview: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = com.keyforge.chat.ui.theme.Elevation.Level1,
    ) {
        Column(Modifier.padding(MaterialTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Icon(
                    imageVector = iconFor(card),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("${humanSize(card.sizeBytes)} · ${card.mimeType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(card.status)
            }
            if (card.status == FileStatus.GENERATING || card.status == FileStatus.VALIDATING || card.status == FileStatus.FIXING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (card.status == FileStatus.NEEDS_ATTENTION && card.errors.isNotEmpty()) {
                card.errors.take(2).forEach { err ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                TextButton(onClick = onDownload, enabled = card.status == FileStatus.VERIFIED) { Text("Download") }
                TextButton(onClick = onShare, enabled = card.status == FileStatus.VERIFIED) { Text("Share") }
                TextButton(onClick = onPreview, enabled = card.status == FileStatus.VERIFIED) { Text("Preview") }
                TextButton(onClick = onCopy, enabled = card.status == FileStatus.VERIFIED) { Text("Copy") }
            }
        }
    }
}

@Composable
private fun StatusChip(status: FileStatus) {
    val (label, color, icon) = when (status) {
        FileStatus.GENERATING -> Triple("Generating", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.HourglassTop)
        FileStatus.VALIDATING -> Triple("Validating", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.HourglassTop)
        FileStatus.FIXING -> Triple("Fixing", MaterialTheme.colorScheme.tertiary, Icons.Rounded.HourglassTop)
        FileStatus.VERIFIED -> Triple("Verified", MaterialTheme.colorScheme.primary, Icons.Rounded.CheckCircle)
        FileStatus.NEEDS_ATTENTION -> Triple("Needs attention", MaterialTheme.colorScheme.error, Icons.Rounded.ErrorOutline)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = MaterialTheme.spacing.sm, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
    }
}

private fun iconFor(card: FileCardState): ImageVector = when {
    card.isZip -> Icons.Rounded.FolderZip
    card.mimeType.startsWith("image/") -> Icons.Rounded.Image
    card.mimeType.startsWith("text/") || card.path.endsWith(".js") || card.path.endsWith(".json") -> Icons.Rounded.Code
    else -> Icons.Rounded.Description
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}
