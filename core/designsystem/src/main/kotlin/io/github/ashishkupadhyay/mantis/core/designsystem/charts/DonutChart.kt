package io.github.ashishkupadhyay.mantis.core.designsystem.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Donut chart for categorical dimensions (doc 05 §4.8). Segment tap → [onSliceClick]; the selected slice is drawn
 * thicker. Accessibility: a single content description summarising every slice; pair with a table view.
 */
@Composable
fun DonutChart(
    slices: ImmutableList<ChartSlice>,
    modifier: Modifier = Modifier,
    title: String = "Spending",
    selectedKey: String? = null,
    onSliceClick: ((String) -> Unit)? = null,
    strokeWidth: Dp = 22.dp,
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = chartAnimationSpec(), label = "donut")
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val description = buildString {
        append(title).append(": ")
        if (total <= 0f) append("no data") else append(slices.joinToString { "${it.label} ${percent(it.value, total)} percent" })
    }
    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.toPx() }
    val selectedExtraPx = with(density) { 6.dp.toPx() }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slices, onSliceClick) {
                    if (onSliceClick == null || total <= 0f) return@pointerInput
                    detectTapGestures { tap ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height) / 2f
                        val d = hypot(tap.x - c.x, tap.y - c.y)
                        if (d < radius - strokePx - selectedExtraPx || d > radius) return@detectTapGestures
                        var angle = Math.toDegrees(atan2((tap.y - c.y).toDouble(), (tap.x - c.x).toDouble())).toFloat() - TOP
                        if (angle < 0) angle += FULL_CIRCLE
                        var start = 0f
                        for (slice in slices) {
                            val sweep = slice.value / total * FULL_CIRCLE
                            if (angle in start..(start + sweep)) {
                                onSliceClick(slice.key)
                                return@detectTapGestures
                            }
                            start += sweep
                        }
                    }
                },
        ) {
            val inset = strokePx / 2f + selectedExtraPx
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(trackColor, 0f, FULL_CIRCLE, false, topLeft, arcSize, style = Stroke(strokePx))
            if (total <= 0f) return@Canvas
            var start = TOP
            slices.forEach { slice ->
                val sweep = slice.value / total * FULL_CIRCLE * progress
                val gap = if (slices.size > 1) GAP_DEGREES else 0f
                val width = if (slice.key == selectedKey) strokePx + selectedExtraPx * 2 else strokePx
                if (sweep > gap) {
                    drawArc(
                        color = slice.color,
                        startAngle = start + gap / 2,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

private const val GAP_DEGREES = 2f
