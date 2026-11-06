package com.keyforge.chat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keyforge.chat.feature.chat.ChatUiMessage
import com.keyforge.chat.ui.theme.spacing

/**
 * One message row. Per section 6.3: user messages in a tonal container with a
 * max readable width; assistant messages full-width without a heavy bubble.
 * Streaming text reserves space and never reflows the layout — the column
 * width is fixed by the parent, only the height grows.
 */
@Composable
fun MessageRow(
    message: ChatUiMessage,
    modifier: Modifier = Modifier,
) {
    if (message.role == "USER") {
        Box(modifier = modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.xs), contentAlignment = Alignment.CenterEnd) {
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                tonalElevation = com.keyforge.chat.ui.theme.Elevation.Level0,
                modifier = Modifier.widthIn(max = 320.dp).padding(MaterialTheme.spacing.lg),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            if (message.routingNote != null) {
                RoutingChip(note = message.routingNote)
            }
            message.files.forEach { card ->
                FileCard(card = card, onDownload = {}, onShare = {}, onPreview = {}, onCopy = {})
            }
        }
    }
}
