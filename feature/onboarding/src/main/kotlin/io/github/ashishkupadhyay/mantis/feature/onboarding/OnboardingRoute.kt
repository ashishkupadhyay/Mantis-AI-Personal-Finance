package io.github.ashishkupadhyay.mantis.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * ViewModel-aware entry point shown by the app shell until onboarding completes (conditional navigation, doc 02 §4.2).
 * [onFinished] fires after the defaults are written; the shell then switches to the main navigation.
 */
@Composable
fun OnboardingRoute(onFinished: () -> Unit, modifier: Modifier = Modifier, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.Finished -> onFinished()
            }
        }
    }
    OnboardingScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
