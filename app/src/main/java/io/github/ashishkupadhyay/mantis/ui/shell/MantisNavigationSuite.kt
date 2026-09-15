package io.github.ashishkupadhyay.mantis.ui.shell

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ashishkupadhyay.mantis.core.ui.navigation.MantisNavigationState
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TopLevelDestination

/**
 * Bottom bar on compact widths, rail on medium/expanded (doc 02 §4.2, `adaptive` guidance). Insets are handled
 * per screen, never on this scaffold, so screens can draw edge-to-edge behind the bar.
 */
@Composable
fun MantisNavigationSuite(state: MantisNavigationState, content: @Composable () -> Unit) {
    NavigationSuiteScaffold(
        navigationItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == state.selected
                NavigationSuiteItem(
                    selected = selected,
                    onClick = { if (selected) state.reselect(destination) else state.navigate(destination.key) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
        content = content,
    )
}
