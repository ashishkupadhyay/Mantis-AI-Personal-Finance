package io.github.ashishkupadhyay.mantis.ui.placeholder

import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.ashishkupadhyay.mantis.R
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Budgets
import io.github.ashishkupadhyay.mantis.core.ui.navigation.DesignCatalogue
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Home
import io.github.ashishkupadhyay.mantis.core.ui.navigation.MantisNavigator
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Reports
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TopLevelDestination
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Transactions

/**
 * Stand-ins for the five top-level destinations until their features land (M1/M2). They exercise the shell:
 * tab switching, per-tab back stacks, the list-detail scene on wide screens, and the design catalogue.
 */
object PlaceholderEntries : EntryProviderInstaller {

    override fun EntryProviderScope<NavKey>.install(navigator: MantisNavigator) {
        entry<Home> {
            PlaceholderScreen(
                title = stringResource(TopLevelDestination.HOME.labelRes),
                landsIn = "WP-1.8",
                action = stringResource(R.string.placeholder_open_transactions) to { navigator.navigate(Transactions) },
            )
        }
        entry<Budgets> { PlaceholderScreen(title = stringResource(TopLevelDestination.BUDGETS.labelRes), landsIn = "WP-1.6") }
        entry<Reports> { PlaceholderScreen(title = stringResource(TopLevelDestination.REPORTS.labelRes), landsIn = "WP-2.5") }
        entry<DesignCatalogue> { CatalogueScreen(onBack = navigator::goBack) }
    }
}
