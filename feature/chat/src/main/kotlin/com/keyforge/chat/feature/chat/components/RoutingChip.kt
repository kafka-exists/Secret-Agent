package com.keyforge.chat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keyforge.chat.ui.theme.MotionDuration
import com.keyforge.chat.ui.theme.spacing

/**
 * The subtle inline "Switched to Groq · llama… (key 2)" indicator. Animates in
 * when a routing note is present and out when it clears.
 */
@Composable
fun RoutingChip(
    note: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = note != null,
        enter = slideInVertically { it } + fadeIn(tween(MotionDuration.Short2)),
        exit = slideOutVertically { it } + fadeOut(tween(MotionDuration.Short1)),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.tertiaryContainer,
                    RoundedCornerShape(50),
                )
                .padding(horizontal = MaterialTheme.spacing.sm, vertical = 4.dp),
        ) {
            Icon(
                Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = note ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

private fun tween(d: Int) = androidx.compose.animation.core.tween<Float>(d)
