package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class FabMenuItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Expressive FAB menu (doc 05 §3): the primary action on Home expands to Expense / Income / Transfer / Scan / Import.
 * The toggle icon morphs from + to × with `checkedProgress`.
 */
@Composable
fun MantisFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: ImmutableList<FabMenuItem>,
    modifier: Modifier = Modifier,
    collapsedContentDescription: String = "Add",
) {
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = Modifier.semantics {
                    contentDescription = collapsedContentDescription
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            ) {
                val icon by remember {
                    androidx.compose.runtime.derivedStateOf { if (checkedProgress > HALF) Icons.Default.Close else Icons.Default.Add }
                }
                Icon(
                    painter = rememberVectorPainter(icon),
                    contentDescription = null,
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    item.onClick()
                },
                text = { Text(item.label) },
                icon = { Icon(item.icon, contentDescription = null) },
            )
        }
    }
}

private const val HALF = 0.5f
