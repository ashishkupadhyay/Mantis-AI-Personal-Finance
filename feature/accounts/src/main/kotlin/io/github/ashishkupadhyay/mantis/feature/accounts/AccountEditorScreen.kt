package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.PreviewMantis
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import io.github.ashishkupadhyay.mantis.core.ui.format.Dates
import io.github.ashishkupadhyay.mantis.core.ui.icons.MantisIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Create / edit form (FR-ACC-1, FR-ACC-5). Everything is a chip or a field; Save validates and reports per field. */
@Composable
fun AccountEditorScreen(
    state: AccountEditorUiState,
    today: LocalDate,
    onEvent: (AccountEditorEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            MantisTopAppBar(
                title = stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title),
                navigationIcon = { BackButton(onBack) },
                actions = {
                    Button(
                        onClick = { onEvent(AccountEditorEvent.Save) },
                        enabled = state.loaded && !state.saving,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.editor_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(AccountEditorEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.editor_name)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.editor_name_error)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.editor_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { onEvent(AccountEditorEvent.TypeChanged(type)) },
                        label = { Text(type.label()) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.editor_institution))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.institutionId == null,
                    onClick = { onEvent(AccountEditorEvent.InstitutionChanged(null)) },
                    label = { Text(stringResource(R.string.editor_institution_none)) },
                )
                state.institutions.forEach { institution ->
                    FilterChip(
                        selected = state.institutionId == institution.id,
                        onClick = { onEvent(AccountEditorEvent.InstitutionChanged(institution.id)) },
                        label = { Text(institution.name) },
                        leadingIcon = {
                            Box(Modifier.size(12.dp).background(Color(institution.brandColor), CircleShape))
                        },
                    )
                }
            }

            OutlinedTextField(
                value = state.last4,
                onValueChange = { onEvent(AccountEditorEvent.Last4Changed(it)) },
                label = { Text(stringResource(R.string.editor_last4)) },
                isError = state.last4Error,
                supportingText = if (state.last4Error) ({ Text(stringResource(R.string.editor_last4_error)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.editor_currency))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CURRENCIES.forEach { code ->
                    FilterChip(
                        selected = state.currencyCode == code,
                        onClick = { onEvent(AccountEditorEvent.CurrencyChanged(code)) },
                        label = { Text(code) },
                    )
                }
            }

            OutlinedTextField(
                value = state.openingBalance,
                onValueChange = { onEvent(AccountEditorEvent.OpeningBalanceChanged(it)) },
                label = { Text(stringResource(R.string.editor_opening_balance)) },
                isError = state.balanceError,
                supportingText = if (state.balanceError) ({ Text(stringResource(R.string.editor_opening_balance_error)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = Dates.short(state.openingDate, today),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.editor_opening_date)) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.editor_pick_date))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isCard) {
                SectionTitle(stringResource(R.string.editor_statement_day, state.statementDay))
                Slider(
                    value = state.statementDay.toFloat(),
                    onValueChange = { onEvent(AccountEditorEvent.StatementDayChanged(it.toInt())) },
                    valueRange = 1f..MAX_DAY.toFloat(),
                    steps = MAX_DAY - 2,
                )
                SectionTitle(stringResource(R.string.editor_due_day, state.dueDay))
                Slider(
                    value = state.dueDay.toFloat(),
                    onValueChange = { onEvent(AccountEditorEvent.DueDayChanged(it.toInt())) },
                    valueRange = 1f..MAX_DAY.toFloat(),
                    steps = MAX_DAY - 2,
                )
            }

            SectionTitle(stringResource(R.string.editor_colour))
            val palette = LocalMantisColors.current.categoryPalette
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEachIndexed { index, color ->
                    FilterChip(
                        selected = state.colorSeed == index,
                        onClick = { onEvent(AccountEditorEvent.ColorChanged(index)) },
                        label = { Box(Modifier.size(20.dp).background(color, CircleShape)) },
                    )
                }
            }

            SectionTitle(stringResource(R.string.editor_icon))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ACCOUNT_ICONS.forEach { name ->
                    val vector = MantisIcons.forName(name) ?: return@forEach
                    FilterChip(
                        selected = state.icon == name,
                        onClick = { onEvent(AccountEditorEvent.IconChanged(name)) },
                        label = { Icon(vector, contentDescription = name) },
                    )
                }
            }
            Box(Modifier.size(24.dp))
        }
    }

    if (showDatePicker) {
        val initialMillis = state.openingDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onEvent(AccountEditorEvent.OpeningDateChanged(picked))
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.editor_date_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

private val CURRENCIES = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD")
private val ACCOUNT_ICONS = listOf(
    "account_balance", "credit_card", "account_balance_wallet", "payments", "savings", "trending_up", "home", "work",
)
private const val MAX_DAY = 28

@PreviewMantis
@Composable
private fun EditorPreview() = MantisPreviewTheme {
    AccountEditorScreen(
        state = AccountEditorUiState(
            name = "ICICI Card", type = AccountType.CREDIT_CARD, institutionId = "icici", last4 = "8831", loaded = true,
        ),
        today = LocalDate.of(2026, 9, 13),
        onEvent = {},
        onBack = {},
    )
}
