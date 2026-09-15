package io.github.ashishkupadhyay.mantis.core.designsystem.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisCircularWavy
import io.github.ashishkupadhyay.mantis.core.designsystem.component.PaceStatus
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * The hero budget ring (doc 05 §4.1): spent arc, lighter projected arc (dashed when the projection is low
 * confidence), a tick where "today" sits in the period, and an overflow lap in the pace-over colour when
 * spending exceeds the limit. When the pace is WATCH/OVER a wavy indicator pulses behind the ring.
 *
 * All values are fractions of the limit (spent 0.64 = 64 %), so the ring never touches `Money`.
 */
@Composable
fun PaceRing(
    spentFraction: Float,
    projectedFraction: Float,
    periodFraction: Float,
    status: PaceStatus,
    modifier: Modifier = Modifier,
    lowConfidence: Boolean = false,
    strokeWidth: Dp = 12.dp,
    contentDescription: String = paceDescription(spentFraction, projectedFraction, status),
    center: @Composable () -> Unit = {},
) {
    val colors = LocalMantisColors.current
    val scheme = MaterialTheme.colorScheme
    val spent by animateFloatAsState(spentFraction.coerceAtLeast(0f), chartAnimationSpec(), label = "spent")
    val projected by animateFloatAsState(projectedFraction.coerceAtLeast(0f), chartAnimationSpec(), label = "projected")
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val dash = PathEffect.dashPathEffect(floatArrayOf(strokePx, strokePx * DASH_GAP))

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (status != PaceStatus.ON_TRACK) {
            MantisCircularWavy(progress = { spent.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize().padding(strokeWidth / 2))
        }
        Canvas(Modifier.fillMaxSize()) {
            val inset = strokePx
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val stroke = Stroke(strokePx, cap = StrokeCap.Round)

            drawArc(scheme.surfaceContainerHigh, 0f, FULL_CIRCLE, false, topLeft, arcSize, style = Stroke(strokePx))

            // Projected (lighter) lap, from spent to projected, capped at 100 %.
            val projectedEnd = projected.coerceIn(0f, 1f)
            val spentEnd = spent.coerceIn(0f, 1f)
            if (projectedEnd > spentEnd) {
                val projectedStyle = if (lowConfidence) Stroke(strokePx, cap = StrokeCap.Butt, pathEffect = dash) else stroke
                drawArc(
                    color = scheme.primary.copy(alpha = PROJECTED_ALPHA),
                    startAngle = TOP + spentEnd * FULL_CIRCLE,
                    sweepAngle = (projectedEnd - spentEnd) * FULL_CIRCLE,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = projectedStyle,
                )
            }
            // Spent lap.
            if (spentEnd > 0f) drawArc(scheme.primary, TOP, spentEnd * FULL_CIRCLE, false, topLeft, arcSize, style = stroke)
            // Overflow: a second lap past 12 o'clock in the pace-over colour (capped at one extra lap).
            if (spent > 1f) {
                drawArc(colors.paceOver, TOP, (spent - 1f).coerceAtMost(1f) * FULL_CIRCLE, false, topLeft, arcSize, style = stroke)
            }
            // "Today" tick.
            val angle = Math.toRadians((TOP + periodFraction.coerceIn(0f, 1f) * FULL_CIRCLE).toDouble())
            val r = arcSize.width / 2
            val c = Offset(size.width / 2, size.height / 2)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            val outer = Offset(c.x + cosA * (r + strokePx * TICK_OUT), c.y + sinA * (r + strokePx * TICK_OUT))
            val inner = Offset(c.x + cosA * (r - strokePx * TICK_IN), c.y + sinA * (r - strokePx * TICK_IN))
            drawLine(scheme.outline, inner, outer, strokeWidth = TICK_STROKE, cap = StrokeCap.Round)
        }
        center()
    }
}

private fun paceDescription(spent: Float, projected: Float, status: PaceStatus): String =
    "Spent ${(spent * PERCENT).toInt()} percent, projected ${(projected * PERCENT).toInt()} percent, ${status.label}"

private const val PROJECTED_ALPHA = 0.4f
private const val DASH_GAP = 0.6f
private const val TICK_OUT = 0.9f
private const val TICK_IN = 0.9f
private const val TICK_STROKE = 3f
