package io.github.ashishkupadhyay.mantis.ui.placeholder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import io.github.ashishkupadhyay.mantis.R
import io.github.ashishkupadhyay.mantis.core.designsystem.catalogue.ComponentCatalogue
import io.github.ashishkupadhyay.mantis.core.designsystem.component.EmptyState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.ErrorState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLargeTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.rememberExitUntilCollapsedScrollBehavior

/**
 * A screen-shaped placeholder: its own `Scaffold` applies the window insets (edge-to-edge guidance — the adaptive
 * scaffold above it does not), a collapsing app bar, and an [EmptyState] naming the work package that replaces it.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    landsIn: String,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    action: Pair<String, () -> Unit>? = null,
) {
    val scrollBehavior = rememberExitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (large) {
                MantisLargeTopAppBar(title, scrollBehavior = scrollBehavior)
            } else {
                MantisTopAppBar(title, scrollBehavior = scrollBehavior)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            EmptyState(
                icon = Icons.Default.Construction,
                title = stringResource(R.string.placeholder_title, title),
                body = stringResource(R.string.placeholder_body, landsIn),
                primaryAction = action,
            )
        }
    }
}

/** Shown for a key no installer registered: the link was valid but the screen is not in this build. */
@Composable
fun UnavailableScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MantisTopAppBar(
                title = stringResource(R.string.unavailable_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            ErrorState(
                title = stringResource(R.string.unavailable_title),
                body = stringResource(R.string.unavailable_body),
                onRetry = onBack,
                retryLabel = stringResource(R.string.action_back),
            )
        }
    }
}

/** The design-system catalogue behind a compact app bar (doc 05 §11). */
@Composable
fun CatalogueScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MantisTopAppBar(
                title = stringResource(R.string.placeholder_catalogue_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        ComponentCatalogue(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}
