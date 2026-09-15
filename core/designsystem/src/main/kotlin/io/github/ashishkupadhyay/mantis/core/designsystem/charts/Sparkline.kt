package io.github.ashishkupadhyay.mantis.core.designsystem.charts

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.collections.immutable.ImmutableList

/** Tiny trend line for insight cards and pinned views. No axes; the surrounding text carries the numbers. */
@Composable
fun Sparkline(
    values: ImmutableList<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fill: Boolean = true,
    contentDescription: String = "Trend over ${values.size} points",
) {
    Canvas(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v - min) / range * size.height * VERTICAL_FILL - size.height * VERTICAL_PAD
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        if (fill) {
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = FILL_ALPHA), Color.Transparent)))
        }
        drawPath(line, color, style = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private const val STROKE = 4f
private const val FILL_ALPHA = 0.25f
private const val VERTICAL_FILL = 0.8f
private const val VERTICAL_PAD = 0.1f
