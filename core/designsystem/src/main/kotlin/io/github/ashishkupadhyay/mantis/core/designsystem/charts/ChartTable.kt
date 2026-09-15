package io.github.ashishkupadhyay.mantis.core.designsystem.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.TABULAR_FIGURES
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One row of the table alternative every chart offers (doc 05 §3, NFR-25): what a screen reader or a "view as table" toggle shows. */
@Immutable
data class ChartTableRow(
    val key: String,
    val label: String,
    val valueLabel: String,
    val detail: String? = null,
    val color: Color? = null,
)

/** Donut / ranked slices as rows, with the share of the total as the detail column. */
fun ImmutableList<ChartSlice>.toTableRows(): ImmutableList<ChartTableRow> {
    val total = sumOf { it.value.toDouble() }.toFloat()
    return map { slice ->
        ChartTableRow(
            key = slice.key,
            label = slice.label,
            valueLabel = slice.valueLabel.ifEmpty { slice.value.toString() },
            detail = "${percent(slice.value, total)} %",
            color = slice.color,
        )
    }.toImmutableList()
}

/** Stacked bars as one row per period, with the segment breakdown as the detail column. */
fun ImmutableList<BarGroup>.toTableRows(formatValue: (Float) -> String): ImmutableList<ChartTableRow> =
    map { group ->
        ChartTableRow(
            key = group.key,
            label = group.label,
            valueLabel = formatValue(group.total),
            detail = group.segments.joinToString { "${it.label} ${formatValue(it.value)}" },
        )
    }.toImmutableList()

/**
 * The accessible, text-only rendering of chart data. Each row is one merged semantics node ("Food, ₹6,180, 37 %"),
 * rows share the chart's colours and selection, and tapping a row does what tapping the segment does.
 */
@Composable
fun ChartDataTable(
    rows: ImmutableList<ChartTableRow>,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    onRowClick: ((String) -> Unit)? = null,
) {
    Column(modifier) {
        rows.forEach { row ->
            TableRow(row, selected = row.key == selectedKey, onClick = onRowClick?.let { { it(row.key) } })
        }
    }
}

@Composable
private fun TableRow(row: ChartTableRow, selected: Boolean, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val background = if (selected) scheme.secondaryContainer else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { if (onClick != null) this.selected = selected },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.color != null) {
            Box(Modifier.size(10.dp).background(row.color, CircleShape))
        }
        Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            row.valueLabel,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
        )
        if (row.detail != null) {
            Text(row.detail, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}
