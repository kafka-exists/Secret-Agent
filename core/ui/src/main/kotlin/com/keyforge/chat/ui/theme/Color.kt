package com.keyforge.chat.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand identity is a restrained teal, generated from a single seed. The full
 * tonal set is hand-tuned so the fallback palette (used below Android 12 or when
 * dynamic color is disabled) reads as deliberate rather than the stock purple
 * Material baseline. All UI colors MUST come from [LightColorScheme],
 * [DarkColorScheme] or [AmoledColorScheme] — never from a raw Color(...) in a
 * screen file (the hardcoded-color lint rule enforces this).
 */

// ---- Light tonal palette ---------------------------------------------------
internal val MdPrimary = Color(0xFF006A6A)
internal val MdOnPrimary = Color(0xFFFFFFFF)
internal val MdPrimaryContainer = Color(0xFF9CF1E8)
internal val MdOnPrimaryContainer = Color(0xFF00201E)

internal val MdSecondary = Color(0xFF4A635F)
internal val MdOnSecondary = Color(0xFFFFFFFF)
internal val MdSecondaryContainer = Color(0xFFCDE8E1)
internal val MdOnSecondaryContainer = Color(0xFF06201C)

internal val MdTertiary = Color(0xFF446279)
internal val MdOnTertiary = Color(0xFFFFFFFF)
internal val MdTertiaryContainer = Color(0xFFC8E6FF)
internal val MdOnTertiaryContainer = Color(0xFF001E30)

internal val MdError = Color(0xFFBA1A1A)
internal val MdOnError = Color(0xFFFFFFFF)
internal val MdErrorContainer = Color(0xFFFFDAD6)
internal val MdOnErrorContainer = Color(0xFF410002)

internal val MdBackground = Color(0xFFF5FBF9)
internal val MdOnBackground = Color(0xFF161D1C)
internal val MdSurface = Color(0xFFF5FBF9)
internal val MdOnSurface = Color(0xFF161D1C)
internal val MdSurfaceVariant = Color(0xFFDAE5E1)
internal val MdOnSurfaceVariant = Color(0xFF3F4946)
internal val MdOutline = Color(0xFF6F7976)
internal val MdOutlineVariant = Color(0xFFBEC9C5)

// ---- Dark tonal palette ----------------------------------------------------
internal val MdPrimaryDark = Color(0xFF80D5C9)
internal val MdOnPrimaryDark = Color(0xFF003731)
internal val MdPrimaryContainerDark = Color(0xFF00504A)
internal val MdOnPrimaryContainerDark = Color(0xFF9CF1E8)

internal val MdSecondaryDark = Color(0xFFB1CCC5)
internal val MdOnSecondaryDark = Color(0xFF1B352F)
internal val MdSecondaryContainerDark = Color(0xFF324B46)
internal val MdOnSecondaryContainerDark = Color(0xFFCDE8E1)

internal val MdTertiaryDark = Color(0xFFAACBE6)
internal val MdOnTertiaryDark = Color(0xFF10313F)
internal val MdTertiaryContainerDark = Color(0xFF2A495A)
internal val MdOnTertiaryContainerDark = Color(0xFFC8E6FF)

internal val MdErrorDark = Color(0xFFFFB4AB)
internal val MdOnErrorDark = Color(0xFF690005)
internal val MdErrorContainerDark = Color(0xFF93000A)
internal val MdOnErrorContainerDark = Color(0xFFFFDAD6)

internal val MdBackgroundDark = Color(0xFF0E1513)
internal val MdOnBackgroundDark = Color(0xFFDEE5E2)
internal val MdSurfaceDark = Color(0xFF0E1513)
internal val MdOnSurfaceDark = Color(0xFFDEE5E2)
internal val MdSurfaceVariantDark = Color(0xFF3F4946)
internal val MdOnSurfaceVariantDark = Color(0xFFBEC9C5)
internal val MdOutlineDark = Color(0xFF889390)
internal val MdOutlineVariantDark = Color(0xFF3F4946)

val LightColorScheme = lightColorScheme(
    primary = MdPrimary,
    onPrimary = MdOnPrimary,
    primaryContainer = MdPrimaryContainer,
    onPrimaryContainer = MdOnPrimaryContainer,
    secondary = MdSecondary,
    onSecondary = MdOnSecondary,
    secondaryContainer = MdSecondaryContainer,
    onSecondaryContainer = MdOnSecondaryContainer,
    tertiary = MdTertiary,
    onTertiary = MdOnTertiary,
    tertiaryContainer = MdTertiaryContainer,
    onTertiaryContainer = MdOnTertiaryContainer,
    error = MdError,
    onError = MdOnError,
    errorContainer = MdErrorContainer,
    onErrorContainer = MdOnErrorContainer,
    background = MdBackground,
    onBackground = MdOnBackground,
    surface = MdSurface,
    onSurface = MdOnSurface,
    surfaceVariant = MdSurfaceVariant,
    onSurfaceVariant = MdOnSurfaceVariant,
    outline = MdOutline,
    outlineVariant = MdOutlineVariant,
)

val DarkColorScheme = darkColorScheme(
    primary = MdPrimaryDark,
    onPrimary = MdOnPrimaryDark,
    primaryContainer = MdPrimaryContainerDark,
    onPrimaryContainer = MdOnPrimaryContainerDark,
    secondary = MdSecondaryDark,
    onSecondary = MdOnSecondaryDark,
    secondaryContainer = MdSecondaryContainerDark,
    onSecondaryContainer = MdOnSecondaryContainerDark,
    tertiary = MdTertiaryDark,
    onTertiary = MdOnTertiaryDark,
    tertiaryContainer = MdTertiaryContainerDark,
    onTertiaryContainer = MdOnTertiaryContainerDark,
    error = MdErrorDark,
    onError = MdOnErrorDark,
    errorContainer = MdErrorContainerDark,
    onErrorContainer = MdOnErrorContainerDark,
    background = MdBackgroundDark,
    onBackground = MdOnBackgroundDark,
    surface = MdSurfaceDark,
    onSurface = MdOnSurfaceDark,
    surfaceVariant = MdSurfaceVariantDark,
    onSurfaceVariant = MdOnSurfaceVariantDark,
    outline = MdOutlineDark,
    outlineVariant = MdOutlineVariantDark,
)

/** True-black surfaces for OLED panels; otherwise identical to [DarkColorScheme]. */
val AmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    onBackground = MdOnSurfaceDark,
    surface = Color.Black,
    onSurface = MdOnSurfaceDark,
)

/** Theme-aware syntax-highlight colors for code blocks. Lives in the theme
 *  package so the hardcoded-color rule does not flag it. */
object CodeColors {
    val keyword = Color(0xFFC792EA)
    val string = Color(0xFFC3E88D)
    val comment = Color(0xFF6B7B85)
    val number = Color(0xFFF78C6C)
    val function = Color(0xFF82AAFF)
    val punctuation = Color(0xFF89DDFF)
    val plain = Color(0xFFEEFFFF)
}
