package io.github.ashishkupadhyay.mantis.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Large flexible app bar for top-level screens (Home, Reports): title + optional period subtitle. */
@Composable
fun MantisLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        subtitle = subtitle?.let { { Text(it) } },
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/** Medium flexible app bar for detail screens; the title is typically the amount (doc 05 §4.3). */
@Composable
fun MantisMediumTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    MediumFlexibleTopAppBar(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

/** Compact app bar for sheets and secondary screens. */
@Composable
fun MantisTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun rememberExitUntilCollapsedScrollBehavior(): TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
