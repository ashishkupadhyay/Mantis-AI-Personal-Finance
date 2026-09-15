package io.github.ashishkupadhyay.mantis.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.github.ashishkupadhyay.mantis.core.common.money.MoneyFormatter
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.ColorSource
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessage
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.MantisSeed
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.MantisTheme
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.ThemeMode
import io.github.ashishkupadhyay.mantis.core.model.preferences.ColorSourcePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.ThemePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import io.github.ashishkupadhyay.mantis.core.ui.money.LocalMoneyFormatter
import io.github.ashishkupadhyay.mantis.core.ui.navigation.BottomSheetSceneStrategy
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.core.ui.navigation.rememberMantisNavigationState
import io.github.ashishkupadhyay.mantis.core.ui.security.SecureWindow
import io.github.ashishkupadhyay.mantis.deeplink.DeepLink
import io.github.ashishkupadhyay.mantis.feature.onboarding.OnboardingRoute
import io.github.ashishkupadhyay.mantis.ui.lock.AppLockGate
import io.github.ashishkupadhyay.mantis.ui.placeholder.UnavailableScreen
import io.github.ashishkupadhyay.mantis.ui.shell.MantisNavigationSuite
import io.github.ashishkupadhyay.mantis.ui.shell.SystemBarsFollowTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Root of the UI (doc 02 §4.2): preferences → theme → onboarding gate → App Lock gate → navigation suite →
 * `NavDisplay` over the per-tab back stacks. Nothing renders until preferences have loaded, so the first frame
 * already has the right theme and lock state (NFR-20h).
 */
@Composable
fun MantisApp(
    installers: ImmutableList<EntryProviderInstaller>,
    pendingDeepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MantisAppContent(
        state = state,
        installers = installers,
        pendingDeepLink = pendingDeepLink,
        onDeepLinkConsumed = onDeepLinkConsumed,
        onUnlocked = viewModel::unlock,
        messages = viewModel.messages,
    )
}

/** The shell without Hilt, so screenshot tests can render it from a plain [MainUiState]. */
@Composable
fun MantisAppContent(
    state: MainUiState,
    installers: ImmutableList<EntryProviderInstaller>,
    pendingDeepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    onUnlocked: () -> Unit,
    messages: Flow<UserMessage> = emptyFlow(),
) {
    val prefs = state.preferences
    val themeMode = prefs.theme.toThemeMode()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SystemBarsFollowTheme(dark = dark)
    MantisTheme(
        themeMode = themeMode,
        colorSource = prefs.toColorSource(),
        amoledBlack = prefs.amoledBlack,
        reducedMotion = prefs.reducedMotion,
        hideAmounts = prefs.hideAmounts,
    ) {
        val formatter = remember(prefs.numberStyle) { MoneyFormatter(style = prefs.numberStyle) }
        CompositionLocalProvider(LocalMoneyFormatter provides formatter) {
            when {
                !state.loaded -> Unit
                !prefs.onboardingCompleted -> OnboardingRoute(onFinished = {})
                else -> AppLockGate(state = state.lockState, onUnlocked = onUnlocked) {
                    SecureWindow(secure = prefs.secureSensitiveScreens)
                    MainContent(installers, pendingDeepLink, onDeepLinkConsumed, messages)
                }
            }
        }
    }
}

@Composable
private fun MainContent(
    installers: ImmutableList<EntryProviderInstaller>,
    pendingDeepLink: DeepLink?,
    onDeepLinkConsumed: () -> Unit,
    messages: Flow<UserMessage>,
) {
    val navigationState = rememberMantisNavigationState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(messages) {
        messages.collect { message ->
            // Each message replaces the previous one; the action (e.g. Undo) runs in the shell scope so it
            // survives the screen that posted it being popped.
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = message.text,
                    actionLabel = message.actionLabel,
                    duration = if (message.long) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) message.action?.invoke()
            }
        }
    }
    LaunchedEffect(pendingDeepLink) {
        if (pendingDeepLink != null) {
            navigationState.openStack(pendingDeepLink.destination, pendingDeepLink.stack)
            onDeepLinkConsumed()
        }
    }
    val entryProvider = remember(installers, navigationState) {
        entryProvider<NavKey>(
            // A key nothing registered (a link from an old notification or widget, a feature behind a flag)
            // must degrade to a screen, never to a crash.
            fallback = { key -> NavEntry(key) { UnavailableScreen(onBack = navigationState::goBack) } },
        ) {
            installers.forEach { installer -> with(installer) { install(navigationState) } }
        }
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }

    MantisNavigationSuite(navigationState) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                entries = navigationState.rememberEntries(entryProvider),
                onBack = { navigationState.goBack() },
                // Overlay strategies first: a sheet on top of whatever the list-detail strategy laid out.
                sceneStrategies = listOf(bottomSheetStrategy, listDetailStrategy),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
            )
        }
    }
}

private fun ThemePreference.toThemeMode(): ThemeMode = when (this) {
    ThemePreference.SYSTEM -> ThemeMode.SYSTEM
    ThemePreference.LIGHT -> ThemeMode.LIGHT
    ThemePreference.DARK -> ThemeMode.DARK
}

private fun UserPreferences.toColorSource(): ColorSource = when (colorSource) {
    ColorSourcePreference.DYNAMIC -> ColorSource.Dynamic
    ColorSourcePreference.SEED -> ColorSource.Seed(MantisSeed.entries.firstOrNull { it.name == seedName } ?: MantisSeed.MANTIS_GREEN)
}
