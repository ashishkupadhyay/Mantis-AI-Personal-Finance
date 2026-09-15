package io.github.ashishkupadhyay.mantis.feature.settings

import android.content.Context
import android.os.Build
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Accounts
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Categories
import io.github.ashishkupadhyay.mantis.core.ui.navigation.DesignCatalogue
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.core.ui.navigation.MantisNavigator
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Settings
import io.github.ashishkupadhyay.mantis.core.ui.navigation.SettingsSection
import io.github.ashishkupadhyay.mantis.core.ui.navigation.SettingsSectionKind

/** The Settings tab root and its sections, each a key on the Settings back stack. */
object SettingsEntries : EntryProviderInstaller {

    override fun EntryProviderScope<NavKey>.install(navigator: MantisNavigator) {
        entry<Settings> {
            SettingsHomeRoute(
                onOpen = { navigator.navigate(SettingsSection(it)) },
                onOpenAccounts = { navigator.navigate(Accounts) },
                onOpenCategories = { navigator.navigate(Categories) },
            )
        }
        entry<SettingsSection> { key ->
            SettingsSectionRoute(kind = key.kind, onBack = navigator::goBack, onOpenCatalogue = { navigator.navigate(DesignCatalogue) })
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsEntriesModule {
    @Provides
    @IntoSet
    fun settingsEntries(): EntryProviderInstaller = SettingsEntries
}

@Composable
private fun SettingsHomeRoute(
    onOpen: (SettingsSectionKind) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsHomeScreen(state = state, onOpen = onOpen, onOpenAccounts = onOpenAccounts, onOpenCategories = onOpenCategories)
}

@Composable
private fun SettingsSectionRoute(
    kind: SettingsSectionKind,
    onBack: () -> Unit,
    onOpenCatalogue: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }
    val prefs = state.preferences
    when (kind) {
        SettingsSectionKind.APPEARANCE -> AppearanceSettingsScreen(
            prefs = prefs,
            dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onEvent = viewModel::onEvent,
            onBack = onBack,
        )
        SettingsSectionKind.MONEY -> MoneySettingsScreen(prefs, viewModel::onEvent, onBack)
        SettingsSectionKind.SECURITY -> SecuritySettingsScreen(state, viewModel::onEvent, onBack)
        SettingsSectionKind.PRIVACY -> PrivacySettingsScreen(prefs, viewModel::onEvent, onBack)
        SettingsSectionKind.ABOUT -> AboutScreen(versionName = LocalContext.current.versionName(), onBack = onBack)
        SettingsSectionKind.DEVELOPER -> DeveloperSettingsScreen(state, snackbarHostState, viewModel::onEvent, onOpenCatalogue, onBack)
    }
}

private fun Context.versionName(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
