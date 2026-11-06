package com.keyforge.chat.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keyforge.chat.ui.theme.Elevation
import com.keyforge.chat.ui.theme.MotionDuration
import com.keyforge.chat.ui.theme.MotionEasing
import com.keyforge.chat.ui.theme.spacing

/** A skeleton shimmer block that matches the layout of real content. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Int = 16,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(MotionDuration.Medium3, easing = MotionEasing.Standard),
            RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = androidx.compose.ui.geometry.Offset(progress * -400f, 0f),
        end = androidx.compose.ui.geometry.Offset(progress * 400f, 0f),
    )
    Box(
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(brush),
    )
}

/** Loading placeholder that mirrors a list of message rows. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, rows: Int = 4) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MaterialTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        repeat(rows) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(if (it % 2 == 0) 0.7f else 0.5f), height = 14)
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.9f), height = 14)
            if (it < rows - 1) Box(modifier = Modifier.height(MaterialTheme.spacing.md))
        }
    }
}

/** Purposeful empty state: icon, one-line explanation, optional primary action. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    icon: ImageVector = Icons.Rounded.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MaterialTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Specific, actionable error with a retry. Microcopy is set by the caller. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    retryLabel: String = "Retry",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(MaterialTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) { Text(retryLabel) }
    }
}
