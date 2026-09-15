package io.github.ashishkupadhyay.mantis.core.designsystem.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList

/**
 * Stacked bars for time dimensions (Month × Category…). Ghost outlines behind each bar show the previous
 * period (FR-VIEW-9). Tap a bar → [onBarClick]. Labels render below in composition, not on the canvas.
 */
@Composable
fun StackedBars(
    groups: ImmutableList<BarGroup>,
    modifier: Modifier = Modifier,
    title: String = "Trend",
    selectedKey: String? = null,
    onBarClick: ((String) -> Unit)? = null,
    chartHeight: Dp = 160.dp,
    valueLabel: (Float) -> String = { it.toInt().toString() },
) {
    val maxValue = groups.maxOfOrNull { maxOf(it.total, it.ghostTotal ?: 0f) }?.takeIf { it > 0f } ?: 1f
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = chartAnimationSpec(), label = "bars")
    val ghostColor = MaterialTheme.colorScheme.outlineVariant
    val selectedOutline = MaterialTheme.colorScheme.primary
    val description = buildString {
        append(title).append(": ")
        append(groups.joinToString { "${it.label} ${valueLabel(it.total)}" })
    }

    Column(modifier = modifier.semantics { contentDescription = description }) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(groups, onBarClick) {
                    if (onBarClick == null || groups.isEmpty()) return@pointerInput
                    detectTapGestures { tap ->
                        val slot = size.width / groups.size
                        val index = (tap.x / slot).toInt().coerceIn(0, groups.lastIndex)
                        onBarClick(groups[index].key)
                    }
                },
        ) {
            if (groups.isEmpty()) return@Canvas
            val slot = size.width / groups.size
            val barWidth = slot * BAR_FRACTION
            val radius = CornerRadius(barWidth * CORNER_FRACTION)
            groups.forEachIndexed { index, group ->
                val x = slot * index + (slot - barWidth) / 2
                group.ghostTotal?.let { ghost ->
                    val h = size.height * (ghost / maxValue) * progress
                    drawRoundRect(ghostColor, Offset(x, size.height - h), Size(barWidth, h), radius, style = Stroke(GHOST_STROKE))
                }
                var top = size.height
                group.segments.forEach { segment ->
                    val h = size.height * (segment.value / maxValue) * progress
                    top -= h
                    drawRoundRect(segment.color, Offset(x, top), Size(barWidth, h), radius)
                }
                if (group.key == selectedKey) {
                    val h = size.height * (group.total / maxValue) * progress
                    drawRoundRect(selectedOutline, Offset(x, size.height - h), Size(barWidth, h), radius, style = Stroke(SELECT_STROKE))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            groups.forEach { group ->
                Text(
                    group.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val BAR_FRACTION = 0.6f
private const val CORNER_FRACTION = 0.25f
private const val GHOST_STROKE = 3f
private const val SELECT_STROKE = 4f
