package io.github.ashishkupadhyay.mantis.core.designsystem.charts

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors
import kotlinx.collections.immutable.ImmutableList

/**
 * Budget detail chart (doc 05 §4.7): this period's cumulative spend (solid) against the historical median curve
 * (dashed) and the limit (horizontal line), with a marker at today. Values are fractions of the limit, indexed by
 * day-of-period (index 0 = day 1); [current] may be shorter than [historical] (future days unknown).
 */
@Composable
fun CumulativeCurve(
    current: ImmutableList<Float>,
    historical: ImmutableList<Float>,
    periodDays: Int,
    todayIndex: Int,
    modifier: Modifier = Modifier,
    contentDescription: String = "Cumulative spend vs typical month",
) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalMantisColors.current
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        if (periodDays < 2) return@Canvas
        val maxY = maxOf(1f, current.maxOrNull() ?: 0f, historical.maxOrNull() ?: 0f) * HEADROOM
        fun x(i: Int) = i.toFloat() / (periodDays - 1) * size.width
        fun y(v: Float) = size.height - v / maxY * size.height

        // Limit line (100 %).
        drawLine(colors.paceOver.copy(alpha = LIMIT_ALPHA), Offset(0f, y(1f)), Offset(size.width, y(1f)), strokeWidth = LINE)

        // Historical median (dashed).
        if (historical.size >= 2) {
            val path = Path()
            historical.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, scheme.outline, style = Stroke(LINE, pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH))))
        }

        // Current period (solid, primary).
        if (current.size >= 2) {
            val path = Path()
            current.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, scheme.primary, style = Stroke(LINE_BOLD, cap = StrokeCap.Round))
        }

        // Today marker.
        val tx = x(todayIndex.coerceIn(0, periodDays - 1))
        drawLine(scheme.outlineVariant, Offset(tx, 0f), Offset(tx, size.height), strokeWidth = LINE)
    }
}

private const val HEADROOM = 1.1f
private const val LINE = 3f
private const val LINE_BOLD = 5f
private const val DASH = 12f
private const val LIMIT_ALPHA = 0.7f
