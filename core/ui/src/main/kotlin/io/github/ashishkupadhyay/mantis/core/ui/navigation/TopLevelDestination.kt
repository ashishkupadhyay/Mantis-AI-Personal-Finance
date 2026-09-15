package io.github.ashishkupadhyay.mantis.core.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.ashishkupadhyay.mantis.core.ui.R

/** The five navigation-suite destinations in display order (doc 05 §4). [HOME] is the start route the user exits through. */
enum class TopLevelDestination(
    val key: MantisKey,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME(Home, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TRANSACTIONS(Transactions, R.string.nav_transactions, Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    BUDGETS(Budgets, R.string.nav_budgets, Icons.Outlined.Savings, Icons.Filled.Savings),
    REPORTS(Reports, R.string.nav_reports, Icons.Outlined.Insights, Icons.Filled.Insights),
    SETTINGS(Settings, R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
    ;

    companion object {
        val start: TopLevelDestination = HOME

        fun of(key: MantisKey): TopLevelDestination? = entries.firstOrNull { it.key == key }
    }
}
