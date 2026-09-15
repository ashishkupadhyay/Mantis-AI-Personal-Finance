package io.github.ashishkupadhyay.mantis.core.designsystem.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.TABULAR_FIGURES
import kotlinx.collections.immutable.ImmutableList

/** Ranked horizontal bars with label, value and share — the list beside a donut, or a standalone view. */
@Composable
fun RankedBars(
    slices: ImmutableList<ChartSlice>,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    onRowClick: ((String) -> Unit)? = null,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val max = slices.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = chartAnimationSpec(), label = "ranked")
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { slice ->
            val selected = slice.key == selectedKey
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onRowClick != null) Modifier.clickable { onRowClick(slice.key) } else Modifier)
                    .padding(vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(10.dp).height(10.dp).background(slice.color, CircleShape))
                    Text(
                        slice.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        slice.valueLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                        fontWeight = if (selected) FontWeight.SemiBold else null,
                        maxLines = 1,
                        softWrap = false,
                    )
                    // Minimum width keeps the column aligned; at large font scales it grows instead of wrapping "%".
                    Text(
                        "${percent(slice.value, total)}%",
                        modifier = Modifier.widthIn(min = 44.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Box(Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp).background(track, CircleShape)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (slice.value / max * progress).coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(slice.color, CircleShape),
                    )
                }
            }
        }
    }
}
