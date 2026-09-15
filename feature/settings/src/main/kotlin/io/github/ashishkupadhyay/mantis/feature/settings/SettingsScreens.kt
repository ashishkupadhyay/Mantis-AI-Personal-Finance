package io.github.ashishkupadhyay.mantis.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.common.money.NumberStyle
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLargeTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisToggleGroup
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.PaceChip
import io.github.ashishkupadhyay.mantis.core.designsystem.component.PaceStatus
import io.github.ashishkupadhyay.mantis.core.designsystem.component.rememberExitUntilCollapsedScrollBehavior
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.PreviewMantis
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.MantisSeed
import io.github.ashishkupadhyay.mantis.core.model.preferences.AppLockPreferences
import io.github.ashishkupadhyay.mantis.core.model.preferences.ColorSourcePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.LockTimeout
import io.github.ashishkupadhyay.mantis.core.model.preferences.ThemePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import io.github.ashishkupadhyay.mantis.core.ui.component.PreferenceLink
import io.github.ashishkupadhyay.mantis.core.ui.component.PreferenceSwitch
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import io.github.ashishkupadhyay.mantis.core.ui.navigation.SettingsSectionKind
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Settings home: grouped sections (doc 05 §4.11). Sections not yet built are hidden rather than shown disabled. */
@Composable
fun SettingsHomeScreen(
    state: SettingsUiState,
    onOpen: (SettingsSectionKind) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = rememberExitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { MantisLargeTopAppBar(stringResource(R.string.settings_title), scrollBehavior = scrollBehavior) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceLink(
                stringResource(R.string.settings_section_accounts), onOpenAccounts,
                summary = stringResource(R.string.settings_section_accounts_summary), icon = Icons.Default.CreditCard,
            )
            PreferenceLink(
                stringResource(R.string.settings_section_categories), onOpenCategories,
                summary = stringResource(R.string.settings_section_categories_summary), icon = Icons.Default.Category,
            )
            PreferenceLink(
                stringResource(R.string.settings_section_appearance), { onOpen(SettingsSectionKind.APPEARANCE) },
                summary = stringResource(R.string.settings_section_appearance_summary), icon = Icons.Default.Palette,
            )
            PreferenceLink(
                stringResource(R.string.settings_section_money), { onOpen(SettingsSectionKind.MONEY) },
                summary = stringResource(R.string.settings_section_money_summary), icon = Icons.Default.AccountBalanceWallet,
            )
            PreferenceLink(
                stringResource(R.string.settings_section_security), { onOpen(SettingsSectionKind.SECURITY) },
                summary = stringResource(R.string.settings_section_security_summary), icon = Icons.Default.Lock,
            )
            PreferenceLink(
                stringResource(R.string.settings_section_privacy), { onOpen(SettingsSectionKind.PRIVACY) },
                summary = stringResource(R.string.settings_section_privacy_summary), icon = Icons.Default.PrivacyTip,
            )
            PreferenceLink(
                stringResource(R.string.settings_section_about), { onOpen(SettingsSectionKind.ABOUT) },
                summary = stringResource(R.string.settings_section_about_summary), icon = Icons.Default.Info,
            )
            if (state.isDebugBuild) {
                PreferenceLink(
                    stringResource(R.string.settings_section_developer), { onOpen(SettingsSectionKind.DEVELOPER) },
                    summary = stringResource(R.string.settings_section_developer_summary), icon = Icons.Default.Build,
                )
            }
        }
    }
}

/** Frame shared by every section: compact app bar with back, scrolling column, optional snackbar host. */
@Composable
private fun SectionScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MantisTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) { content() }
    }
}

@Composable
fun AppearanceSettingsScreen(
    prefs: UserPreferences,
    dynamicColorAvailable: Boolean,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionScaffold(stringResource(R.string.settings_section_appearance), onBack, modifier) {
        SectionTitle(stringResource(R.string.settings_theme))
        val themes = ThemePreference.entries
        MantisToggleGroup(
            options = persistentListOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark),
            ),
            selectedIndex = themes.indexOf(prefs.theme),
            onSelected = { onEvent(SettingsEvent.ThemeChanged(themes[it])) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        SectionTitle(stringResource(R.string.settings_colour_source))
        val sources = ColorSourcePreference.entries
        MantisToggleGroup(
            options = persistentListOf(stringResource(R.string.settings_colour_dynamic), stringResource(R.string.settings_colour_seed)),
            selectedIndex = sources.indexOf(prefs.colorSource),
            onSelected = { onEvent(SettingsEvent.ColorSourceChanged(sources[it])) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            enabled = dynamicColorAvailable,
        )
        if (!dynamicColorAvailable) {
            Text(
                stringResource(R.string.settings_colour_dynamic_unavailable),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MantisSeed.entries.forEach { seed ->
                val selected = prefs.colorSource == ColorSourcePreference.SEED && prefs.seedName == seed.name
                FilterChip(
                    selected = selected,
                    onClick = { onEvent(SettingsEvent.SeedChanged(seed.name)) },
                    label = { Text(seed.label) },
                    leadingIcon = { Box(Modifier.size(16.dp).background(seed.seed, CircleShape)) },
                )
            }
        }

        PreferenceSwitch(
            stringResource(R.string.settings_amoled), prefs.amoledBlack, { onEvent(SettingsEvent.AmoledChanged(it)) },
            summary = stringResource(R.string.settings_amoled_summary),
        )
        PreferenceSwitch(
            stringResource(R.string.settings_reduced_motion), prefs.reducedMotion, { onEvent(SettingsEvent.ReducedMotionChanged(it)) },
            summary = stringResource(R.string.settings_reduced_motion_summary),
        )

        SectionTitle(stringResource(R.string.settings_preview))
        Card(Modifier.padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("₹12,340 of ₹30,000", style = MaterialTheme.typography.titleLargeEmphasized)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaceChip(PaceStatus.ON_TRACK)
                    PaceChip(PaceStatus.WATCH)
                    PaceChip(PaceStatus.OVER)
                }
                Button(onClick = {}, shapes = ButtonDefaults.shapes()) { Text("Primary action") }
            }
        }
    }
}

@Composable
fun MoneySettingsScreen(prefs: UserPreferences, onEvent: (SettingsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SectionScaffold(stringResource(R.string.settings_section_money), onBack, modifier) {
        SectionTitle(stringResource(R.string.settings_currency))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CURRENCIES.forEach { code ->
                FilterChip(
                    selected = code == prefs.currencyCode,
                    onClick = { onEvent(SettingsEvent.CurrencyChanged(code)) },
                    label = { Text(code) },
                )
            }
        }

        SectionTitle(stringResource(R.string.settings_number_style))
        val styles = NumberStyle.entries
        MantisToggleGroup(
            options = persistentListOf(
                stringResource(R.string.settings_number_indian),
                stringResource(R.string.settings_number_international),
            ),
            selectedIndex = styles.indexOf(prefs.numberStyle),
            onSelected = { onEvent(SettingsEvent.NumberStyleChanged(styles[it])) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        SectionTitle(stringResource(R.string.settings_month_start, prefs.monthStartDay))
        Slider(
            value = prefs.monthStartDay.toFloat(),
            onValueChange = { onEvent(SettingsEvent.MonthStartChanged(it.toInt())) },
            valueRange = 1f..UserPreferences.MAX_MONTH_START_DAY.toFloat(),
            steps = UserPreferences.MAX_MONTH_START_DAY - 2,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            stringResource(R.string.settings_month_start_summary),
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(stringResource(R.string.settings_first_day_of_week))
        MantisToggleGroup(
            options = persistentListOf(stringResource(R.string.settings_monday), stringResource(R.string.settings_sunday)),
            selectedIndex = if (prefs.firstDayOfWeek == SUNDAY) 1 else 0,
            onSelected = { onEvent(SettingsEvent.FirstDayOfWeekChanged(if (it == 1) SUNDAY else MONDAY)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun SecuritySettingsScreen(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val prefs = state.preferences
    SectionScaffold(stringResource(R.string.settings_section_security), onBack, modifier) {
        PreferenceSwitch(
            stringResource(R.string.settings_app_lock), prefs.appLock.enabled, { onEvent(SettingsEvent.AppLockChanged(it)) },
            summary = stringResource(
                if (state.appLockAvailable) R.string.settings_app_lock_summary else R.string.settings_app_lock_unavailable,
            ),
            enabled = state.appLockAvailable,
        )
        if (prefs.appLock.enabled) {
            SectionTitle(stringResource(R.string.settings_lock_timeout))
            val timeouts = LockTimeout.entries
            MantisToggleGroup(
                options = timeouts.map { stringResource(it.labelRes()) }.toImmutableList(),
                selectedIndex = timeouts.indexOf(prefs.appLock.timeout),
                onSelected = { onEvent(SettingsEvent.LockTimeoutChanged(timeouts[it])) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            FilledTonalButton(onClick = { onEvent(SettingsEvent.LockNow) }, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_lock_now))
            }
        }
        PreferenceSwitch(
            stringResource(R.string.settings_secure_screens),
            prefs.secureSensitiveScreens,
            { onEvent(SettingsEvent.SecureScreensChanged(it)) },
            summary = stringResource(R.string.settings_secure_screens_summary),
        )
    }
}

@Composable
fun PrivacySettingsScreen(prefs: UserPreferences, onEvent: (SettingsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SectionScaffold(stringResource(R.string.settings_section_privacy), onBack, modifier) {
        PreferenceSwitch(
            stringResource(R.string.settings_hide_amounts), prefs.hideAmounts, { onEvent(SettingsEvent.HideAmountsChanged(it)) },
            summary = stringResource(R.string.settings_hide_amounts_summary),
        )
        Card(Modifier.padding(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_privacy_nothing_leaves), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_privacy_nothing_leaves_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SectionScaffold(stringResource(R.string.settings_section_about), onBack, modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Mantis", style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(stringResource(R.string.settings_about_version, versionName), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_about_open_source),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DeveloperSettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenCatalogue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionScaffold(stringResource(R.string.settings_section_developer), onBack, modifier, snackbarHostState) {
        PreferenceLink(
            stringResource(R.string.settings_seed_sample_data),
            onClick = { onEvent(SettingsEvent.SeedSampleData) },
            summary = stringResource(R.string.settings_seed_sample_data_summary),
            enabled = !state.seeding,
        )
        PreferenceLink(stringResource(R.string.settings_open_catalogue), onClick = onOpenCatalogue)
    }
}

private fun LockTimeout.labelRes(): Int = when (this) {
    LockTimeout.IMMEDIATELY -> R.string.settings_timeout_immediately
    LockTimeout.THIRTY_SECONDS -> R.string.settings_timeout_30s
    LockTimeout.ONE_MINUTE -> R.string.settings_timeout_1m
    LockTimeout.FIVE_MINUTES -> R.string.settings_timeout_5m
}

private val CURRENCIES = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD")
private const val MONDAY = 1
private const val SUNDAY = 7

@PreviewMantis
@Composable
private fun SettingsHomePreview() = MantisPreviewTheme {
    SettingsHomeScreen(SettingsUiState(isDebugBuild = true, loaded = true), onOpen = {}, onOpenAccounts = {}, onOpenCategories = {})
}

@PreviewMantis
@Composable
private fun AppearancePreview() = MantisPreviewTheme {
    AppearanceSettingsScreen(
        prefs = UserPreferences(colorSource = ColorSourcePreference.SEED),
        dynamicColorAvailable = true,
        onEvent = {},
        onBack = {},
    )
}

@PreviewMantis
@Composable
private fun SecurityPreview() = MantisPreviewTheme {
    val prefs = UserPreferences(appLock = AppLockPreferences(enabled = true))
    SecuritySettingsScreen(SettingsUiState(preferences = prefs, appLockAvailable = true, loaded = true), onEvent = {}, onBack = {})
}
