package com.keyforge.chat.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * All motion lives here. Durations follow M3 guidance (short 100–150ms,
 * medium 200–300ms, long ≤ 400ms). No magic durations elsewhere. Screens and
 * components reference these tokens; the "reduce motion" accessibility path
 * collapses to instant via [reducedMotion].
 */
object MotionDuration {
    const val Short1 = 100
    const val Short2 = 150
    const val Medium1 = 200
    const val Medium2 = 250
    const val Medium3 = 300
    const val Long1 = 400
}

object MotionEasing {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

object MotionSpring {
    /** Default spatial spring — snappy but not bouncy. */
    fun <T> default(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    /** Used for state-layer / position swaps. */
    fun <T> gentle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow,
    )
}

object Motion {
    /** Standard enter/exit for content swaps. */
    fun <T> standardEnter() = tween<T>(MotionDuration.Medium2, easing = MotionEasing.EmphasizedDecelerate)
    fun <T> standardExit() = tween<T>(MotionDuration.Short2, easing = MotionEasing.EmphasizedAccelerate)
    fun <T> emphasized() = tween<T>(MotionDuration.Long1, easing = MotionEasing.Emphasized)
}

/**
 * When the system "Remove animations" setting is on, callers pass
 * `reduceMotion = true` and receive instant specs instead of animated ones.
 */
fun <T> motionSpec(reduceMotion: Boolean, spec: () -> FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (reduceMotion) tween(durationMillis = 0) else spec()
