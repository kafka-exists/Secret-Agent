package com.keyforge.chat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.StaticCompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 4dp/8dp spacing grid. Every layout dimension in screens comes from these
 * tokens via [MaterialTheme.spacing]; raw dp values are banned outside the
 * theme package by the lint rule.
 */
data class Spacing(
    val none: Dp = 0.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
) {
    val contentPadding: Dp = lg
    val listSpacing: Dp = md
    val sectionSpacing: Dp = xl
}

internal val LocalSpacing = compositionLocalOf { Spacing() }

internal val MaterialThemeSpacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current

/** Use as `MaterialTheme.spacing.sm`. Mirrors the M3 token accessor pattern. */
val androidx.compose.material3.MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = MaterialThemeSpacing

@Composable
internal fun ProvideSpacing(spacing: Spacing, content: @Composable () -> Unit) {
    val current = remember(spacing) { spacing }
    StaticCompositionLocalProvider(LocalSpacing provides current, content = content)
}
