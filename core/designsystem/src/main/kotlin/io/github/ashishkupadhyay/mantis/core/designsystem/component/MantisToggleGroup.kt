package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import kotlinx.collections.immutable.ImmutableList

/**
 * Connected single-select toggle group (Expressive `ButtonGroup` shapes): period switchers, Bills/Items,
 * Expense/Income (doc 05 §3). Exactly one option is selected.
 *
 * Buttons share the width equally while every label fits; when they don't (long labels, 200 % font scale —
 * NFR-25) each button takes its natural width and the group scrolls horizontally instead of clipping text.
 */
@Composable
fun MantisToggleGroup(
    options: ImmutableList<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacingPx = with(LocalDensity.current) { ButtonGroupDefaults.ConnectedSpaceBetween.roundToPx() }
    BoxWithConstraints(modifier.semantics { selectableGroup() }) {
        val viewportWidth = constraints.maxWidth
        Layout(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            content = {
                options.forEachIndexed { index, label ->
                    ConnectedToggle(
                        label = label,
                        checked = index == selectedIndex,
                        onClick = { onSelected(index) },
                        position = when (index) {
                            0 -> Position.LEADING
                            options.lastIndex -> Position.TRAILING
                            else -> Position.MIDDLE
                        },
                        enabled = enabled,
                    )
                }
            },
        ) { measurables, constraints ->
            val count = measurables.size
            val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
            val gaps = spacingPx * (count - 1).coerceAtLeast(0)
            val widths = if (viewportWidth != Constraints.Infinity && count > 0) {
                val available = (viewportWidth - gaps).coerceAtLeast(0)
                val equal = available / count
                if (natural.all { it <= equal }) {
                    List(count) { index -> equal + if (index < available % count) 1 else 0 }
                } else {
                    natural
                }
            } else {
                natural
            }
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints(minWidth = widths[index], maxWidth = widths[index], maxHeight = constraints.maxHeight))
            }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(widths.sum() + gaps, height) {
                var x = 0
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(x, 0)
                    x += widths[index] + spacingPx
                }
            }
        }
    }
}

private enum class Position { LEADING, MIDDLE, TRAILING }

@Composable
private fun ConnectedToggle(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    position: Position,
    enabled: Boolean,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { if (!checked) onClick() },
        enabled = enabled,
        shapes = when (position) {
            Position.LEADING -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            Position.MIDDLE -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            Position.TRAILING -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        },
    ) {
        Text(label, maxLines = 1, softWrap = false)
    }
}
