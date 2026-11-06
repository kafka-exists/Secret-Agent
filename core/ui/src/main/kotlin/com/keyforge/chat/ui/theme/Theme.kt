package com.keyforge.chat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** User-selectable appearance. Amoled is a true-black variant of dark. */
enum class ThemeMode { System, Light, Dark, Amoled }

/**
 * Single entry point for theming. Picks dynamic color on Android 12+ when
 * enabled, otherwise the hand-tuned fallback palette. The AMOLED mode forces
 * true-black surfaces even when dynamic color is on.
 */
@Composable
fun KeyForgeTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        themeMode == ThemeMode.Amoled -> AmoledColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    ProvideSpacing(Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KeyForgeTypography,
            shapes = KeyForgeShapes,
            content = content,
        )
    }
}
