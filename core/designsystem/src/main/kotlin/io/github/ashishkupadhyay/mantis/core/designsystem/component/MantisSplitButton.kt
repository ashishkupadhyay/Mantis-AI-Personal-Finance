package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class SplitMenuItem(val label: String, val onClick: () -> Unit)

/** "Save ▾" — primary action plus alternatives in a menu (Save & add another, Save as template) — doc 05 §4.4. */
@Composable
fun MantisSplitButton(
    label: String,
    onClick: () -> Unit,
    menuItems: ImmutableList<SplitMenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuContentDescription: String = "More options",
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = onClick, enabled = enabled) { Text(label) }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = menuContentDescription },
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menuItems.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        menuOpen = false
                        item.onClick()
                    },
                )
            }
        }
    }
}
