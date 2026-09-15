package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class ToolbarAction(val label: String, val icon: ImageVector, val onClick: () -> Unit, val enabled: Boolean = true)

/** Contextual multi-select toolbar (Recategorize · Tag · Exclude · Delete) — doc 05 §4.2. */
@Composable
fun MantisFloatingToolbar(
    expanded: Boolean,
    actions: ImmutableList<ToolbarAction>,
    modifier: Modifier = Modifier,
) {
    // The toolbar exists only while something is selected: it slides up from the bottom and away again.
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        HorizontalFloatingToolbar(expanded = true) {
            actions.forEach { action ->
                IconButton(onClick = action.onClick, enabled = action.enabled) {
                    Icon(action.icon, contentDescription = action.label)
                }
            }
        }
    }
}
