package io.github.ashishkupadhyay.mantis.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.ShapeHero
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.PreviewMantis
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors

private data class IntroPage(val group: String, val icon: ImageVector, val title: Int, val body: Int)

private val introPages = listOf(
    IntroPage("finance", Icons.Default.UploadFile, R.string.onboarding_page1_title, R.string.onboarding_page1_body),
    IntroPage("shopping", Icons.Default.AutoAwesome, R.string.onboarding_page2_title, R.string.onboarding_page2_body),
    IntroPage("income", Icons.Default.Savings, R.string.onboarding_page3_title, R.string.onboarding_page3_body),
)

/** Pure screen (doc 02 §4.1): three intro pages with Skip everywhere, then the setup form. */
@Composable
fun OnboardingScreen(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            AnimatedContent(targetState = state.isSetup, label = "onboarding") { setup ->
                if (setup) SetupPage(state, onEvent) else IntroPager(state, onEvent)
            }
        }
    }
}

@Composable
private fun IntroPager(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onEvent(OnboardingEvent.Skip) }) { Text(stringResource(R.string.onboarding_skip)) }
        }
        Spacer(Modifier.weight(1f))
        AnimatedContent(targetState = state.page, label = "introPage") { index ->
            val current = introPages[index.coerceIn(0, introPages.lastIndex)]
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                ShapeHero(groupKey = current.group, icon = current.icon)
                Text(stringResource(current.title), style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
                Text(
                    stringResource(current.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PageIndicator(current = state.page, count = introPages.size)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (state.page > 0) {
                TextButton(onClick = { onEvent(OnboardingEvent.Back) }) { Text(stringResource(R.string.onboarding_back)) }
            } else {
                Spacer(Modifier.size(1.dp))
            }
            Button(onClick = { onEvent(OnboardingEvent.Next) }, shapes = ButtonDefaults.shapes()) {
                val last = state.page == introPages.lastIndex
                Text(stringResource(if (last) R.string.onboarding_get_started else R.string.onboarding_next))
            }
        }
    }
}

@Composable
private fun PageIndicator(current: Int, count: Int) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(
        modifier = Modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                Modifier
                    .size(width = if (selected) 24.dp else 8.dp, height = 8.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.shapes.extraSmall,
                    ),
            )
        }
    }
}

@Composable
private fun SetupPage(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = { onEvent(OnboardingEvent.Back) }) { Text(stringResource(R.string.onboarding_back)) }
        }
        Text(stringResource(R.string.onboarding_setup_title), style = MaterialTheme.typography.headlineMediumEmphasized)

        CurrencyPicker(selected = state.currencyCode, onSelected = { onEvent(OnboardingEvent.CurrencyChanged(it)) })

        Column {
            Text(stringResource(R.string.onboarding_month_start) + ": ${state.monthStartDay}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = state.monthStartDay.toFloat(),
                onValueChange = { onEvent(OnboardingEvent.MonthStartChanged(it.toInt())) },
                valueRange = 1f..28f,
                steps = 26,
            )
            Text(
                stringResource(R.string.onboarding_month_start_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SwitchRow(
            label = stringResource(R.string.onboarding_create_defaults),
            checked = state.createDefaults,
            onCheckedChange = { onEvent(OnboardingEvent.CreateDefaultsChanged(it)) },
        )
        SwitchRow(
            label = stringResource(R.string.onboarding_app_lock),
            supporting = if (state.appLockAvailable) null else stringResource(R.string.onboarding_app_lock_unavailable),
            checked = state.appLockEnabled,
            enabled = state.appLockAvailable,
            onCheckedChange = { onEvent(OnboardingEvent.AppLockChanged(it)) },
        )

        Spacer(Modifier.weight(1f, fill = false))

        Button(
            onClick = { onEvent(OnboardingEvent.ContinueLocalOnly) },
            enabled = !state.saving,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.onboarding_continue_local)) }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_sign_in_google))
        }
        Text(
            stringResource(R.string.onboarding_sign_in_soon),
            style = MaterialTheme.typography.bodySmall,
            color = LocalMantisColors.current.confidenceMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CurrencyPicker(selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.onboarding_currency), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnboardingUiState.CURRENCIES.forEach { code ->
                FilterChip(selected = code == selected, onClick = { onSelected(code) }, label = { Text(code) })
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@PreviewMantis
@Composable
private fun IntroPreview() = MantisPreviewTheme { OnboardingScreen(OnboardingUiState(page = 1), onEvent = {}) }

@PreviewMantis
@Composable
private fun SetupPreview() = MantisPreviewTheme { OnboardingScreen(OnboardingUiState(page = 3, appLockAvailable = true), onEvent = {}) }
