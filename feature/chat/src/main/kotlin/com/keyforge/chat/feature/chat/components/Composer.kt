package com.keyforge.chat.feature.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import com.keyforge.chat.ui.theme.MotionDuration
import com.keyforge.chat.ui.theme.spacing

/**
 * Composer: multi-line input that grows to a max height, an attach button
 * (slot), and an animated send-to-stop morph. Disabled when empty. IME insets
 * are applied by the caller. Send triggers a haptic tick.
 */
@Composable
fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    isSending: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth().padding(MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Message to send" },
            placeholder = { Text("Message KeyForge", style = MaterialTheme.typography.bodyLarge) },
            minLines = 1,
            maxLines = 5,
            shape = MaterialTheme.shapes.large,
        )
        AnimatedContent(
            targetState = isSending,
            transitionSpec = {
                scaleIn(tween(MotionDuration.Short2)) togetherWith scaleOut(tween(MotionDuration.Short1))
            },
            label = "sendStop",
        ) { sending ->
            FilledIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (sending) onStop() else onSend()
                },
                enabled = sending || text.isNotBlank(),
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (sending) "Stop generating" else "Send message"
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (sending) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                )
            }
        }
    }
}
