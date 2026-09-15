package io.github.ashishkupadhyay.mantis.core.designsystem.charts

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalReducedMotion
import kotlinx.collections.immutable.ImmutableList

/** One segment of a donut / ranked list. Values are already in the unit the caller wants to show; labels are pre-formatted. */
@Immutable
data class ChartSlice(val key: String, val label: String, val value: Float, val color: Color, val valueLabel: String = "")

@Immutable
data class BarSegment(val key: String, val label: String, val value: Float, val color: Color)

/** A bar (one period) made of stacked segments; [ghostTotal] draws the comparison period behind it (FR-VIEW-9). */
@Immutable
data class BarGroup(val key: String, val label: String, val segments: ImmutableList<BarSegment>, val ghostTotal: Float? = null) {
    val total: Float get() = segments.sumOf { it.value.toDouble() }.toFloat()
}

/** Chart entrance/update animation: the theme's spatial spring, or a snap under reduced motion (NFR-29). */
@Composable
internal fun chartAnimationSpec(): AnimationSpec<Float> =
    if (LocalReducedMotion.current) snap() else MaterialTheme.motionScheme.slowEffectsSpec()

internal fun percent(part: Float, whole: Float): Int = if (whole <= 0f) 0 else ((part / whole) * PERCENT).toInt()

internal const val PERCENT = 100f
internal const val FULL_CIRCLE = 360f
internal const val TOP = -90f
