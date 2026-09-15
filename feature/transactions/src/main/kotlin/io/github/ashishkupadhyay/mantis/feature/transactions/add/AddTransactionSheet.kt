package io.github.ashishkupadhyay.mantis.feature.transactions.add

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.CategoryAvatar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisSplitButton
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisToggleGroup
import io.github.ashishkupadhyay.mantis.core.designsystem.component.SplitMenuItem
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.format.Dates
import io.github.ashishkupadhyay.mantis.core.ui.icons.MantisIcons
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyText
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyTone
import io.github.ashishkupadhyay.mantis.feature.transactions.R
import io.github.ashishkupadhyay.mantis.core.ui.category.CategoryPickerSheet
import io.github.ashishkupadhyay.mantis.core.ui.category.kindsFor
import io.github.ashishkupadhyay.mantis.feature.transactions.list.toEpochMillis
import io.github.ashishkupadhyay.mantis.feature.transactions.list.toLocalDate
import kotlinx.collections.immutable.persistentListOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Add / edit sheet (doc 05 §4.4): big live amount, type toggle, likely-category chips, account and date chips,
 * merchant/notes/tags fields, the calculator keypad and a split Save button. Three taps for a common expense:
 * amount → category chip → Save (FR-TXN-1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    state: AddTransactionUiState,
    today: LocalDate,
    onEvent: (AddTransactionEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var browsing by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (state.isNew) R.string.add_title else R.string.edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
        }
        MantisToggleGroup(
            options = persistentListOf(
                stringResource(R.string.type_expense),
                stringResource(R.string.type_income),
                stringResource(R.string.type_transfer),
            ),
            selectedIndex = state.type.ordinal,
            onSelected = { onEvent(AddTransactionEvent.TypeChanged(TransactionType.entries[it])) },
            enabled = state.isNew,
        )
        AmountHero(state)
        if (!state.isTransfer) CategoryChips(state, onEvent, onBrowse = { browsing = true })
        AccountChips(state, onEvent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { pickingDate = true },
                label = { Text(Dates.short(state.date, today)) },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.add_pick_date)) },
            )
            AssistChip(
                onClick = { pickingTime = true },
                label = { Text(state.time.format(TIME)) },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.add_pick_time)) },
            )
        }
        OutlinedTextField(
            value = state.description,
            onValueChange = { onEvent(AddTransactionEvent.DescriptionChanged(it)) },
            label = { Text(stringResource(if (state.isTransfer) R.string.add_description else R.string.add_merchant)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Keypad(onKey = { onEvent(AddTransactionEvent.Key(it)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MantisSplitButton(
                label = stringResource(R.string.action_save),
                onClick = { onEvent(AddTransactionEvent.Save()) },
                enabled = state.loaded && !state.saving,
                menuItems = if (state.isNew) {
                    persistentListOf(
                        SplitMenuItem(stringResource(R.string.action_save_add_another)) {
                            onEvent(AddTransactionEvent.Save(addAnother = true))
                        },
                    )
                } else {
                    persistentListOf()
                },
                menuContentDescription = stringResource(R.string.action_more),
            )
        }
        // Secondary fields sit below the fold; the common path never needs them (FR-TXN-1: amount → chip → Save).
        OutlinedTextField(
            value = state.notes,
            onValueChange = { onEvent(AddTransactionEvent.NotesChanged(it)) },
            label = { Text(stringResource(R.string.add_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )
        TagsField(state, onEvent)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.add_exclude), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = state.isExcluded, onCheckedChange = { onEvent(AddTransactionEvent.ExcludedChanged(it)) })
        }
        Spacer(Modifier.height(8.dp))
    }

    if (browsing) {
        CategoryPickerSheet(
            categories = state.categories,
            kinds = kindsFor(state.type),
            selected = state.categoryId,
            onPick = { onEvent(AddTransactionEvent.CategoryChanged(it)); browsing = false },
            onDismiss = { browsing = false },
        )
    }
    if (pickingDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.date.toEpochMillis())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onEvent(AddTransactionEvent.DateChanged(it.toLocalDate())) }
                        pickingDate = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
    if (pickingTime) {
        val timeState = rememberTimePickerState(initialHour = state.time.hour, initialMinute = state.time.minute)
        TimePickerDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(stringResource(R.string.add_pick_time)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(AddTransactionEvent.TimeChanged(LocalTime.of(timeState.hour, timeState.minute)))
                        pickingTime = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { TimePicker(state = timeState) }
    }
}

@Composable
private fun AmountHero(state: AddTransactionUiState) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MoneyText(
            money = state.money,
            style = MaterialTheme.typography.displaySmallEmphasized,
            tone = MoneyTone.NEUTRAL,
            color = if (state.amountError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        // The expression line only appears while an operator is pending ("349 + 51") or something is wrong.
        val expression = state.amount.display.takeIf { state.amount.operator != null }
        Text(
            if (state.amountError) stringResource(R.string.add_amount_error) else expression.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.amountError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryChips(state: AddTransactionUiState, onEvent: (AddTransactionEvent) -> Unit, onBrowse: () -> Unit) {
    val selected = state.selectedCategory
    val chips = if (selected != null && state.likely.none { it.id == selected.id }) listOf(selected) + state.likely else state.likely
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { category ->
            val group = state.categories.firstOrNull { it.id == category.parentId } ?: category
            FilterChip(
                selected = category.id == state.categoryId,
                onClick = { onEvent(AddTransactionEvent.CategoryChanged(if (category.id == state.categoryId) null else category.id)) },
                label = { Text(category.name) },
                leadingIcon = {
                    CategoryAvatar(
                        groupKey = group.key?.let(DefaultTaxonomy::groupKeyOf) ?: "system",
                        colorKey = group.key ?: category.id.value,
                        icon = MantisIcons.forName(category.icon),
                        fallbackLetter = category.name.take(1),
                        size = 22.dp,
                    )
                },
            )
        }
        AssistChip(
            onClick = onBrowse,
            label = { Text(stringResource(R.string.add_more_categories)) },
            leadingIcon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
        )
    }
}

@Composable
private fun AccountChips(state: AddTransactionUiState, onEvent: (AddTransactionEvent) -> Unit) {
    AccountRow(
        label = stringResource(if (state.isTransfer) R.string.add_from_account else R.string.add_account),
        selected = state.accountId,
        error = state.accountError && state.accountId == null,
        state = state,
    ) { onEvent(AddTransactionEvent.AccountChanged(it)) }
    if (state.isTransfer) {
        AccountRow(
            label = stringResource(R.string.add_to_account),
            selected = state.toAccountId,
            error = state.accountError,
            state = state,
        ) { onEvent(AddTransactionEvent.ToAccountChanged(it)) }
    }
}

@Composable
private fun AccountRow(label: String, selected: AccountId?, error: Boolean, state: AddTransactionUiState, onPick: (AccountId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.accounts.forEach { account ->
                FilterChip(selected = account.id == selected, onClick = { onPick(account.id) }, label = { Text(account.name) })
            }
        }
    }
}

@Composable
private fun TagsField(state: AddTransactionUiState, onEvent: (AddTransactionEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.tagInput,
            onValueChange = { onEvent(AddTransactionEvent.TagInputChanged(it)) },
            label = { Text(stringResource(R.string.add_tags)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(AddTransactionEvent.CommitTag) }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onEvent(AddTransactionEvent.RemoveTag(tag)) },
                        label = { Text("#$tag") },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.add_remove_tag, tag)) },
                    )
                }
            }
        }
    }
}

/** 4 × 4 calculator keypad (doc 05 §4.4): digits, backspace, + − and =. Every key is at least 48 dp tall. */
@Composable
fun Keypad(onKey: (KeypadKey) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf(KeypadKey.Digit('7'), KeypadKey.Digit('8'), KeypadKey.Digit('9'), KeypadKey.Backspace),
        listOf(KeypadKey.Digit('4'), KeypadKey.Digit('5'), KeypadKey.Digit('6'), KeypadKey.Plus),
        listOf(KeypadKey.Digit('1'), KeypadKey.Digit('2'), KeypadKey.Digit('3'), KeypadKey.Minus),
        listOf(KeypadKey.Digit('0'), KeypadKey.Dot, KeypadKey.Equals, KeypadKey.Clear),
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val keyModifier = Modifier.weight(1f).height(KEY_HEIGHT)
                    when (key) {
                        is KeypadKey.Digit, KeypadKey.Dot -> FilledTonalButton(onClick = { onKey(key) }, modifier = keyModifier) {
                            Text(if (key is KeypadKey.Digit) key.value.toString() else ".", style = MaterialTheme.typography.titleLarge)
                        }
                        KeypadKey.Backspace -> OutlinedButton(onClick = { onKey(key) }, modifier = keyModifier) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.key_backspace))
                        }
                        KeypadKey.Clear -> OutlinedButton(onClick = { onKey(key) }, modifier = keyModifier) {
                            Text(stringResource(R.string.key_clear))
                        }
                        KeypadKey.Plus, KeypadKey.Minus, KeypadKey.Equals -> OutlinedButton(
                            onClick = { onKey(key) },
                            modifier = keyModifier,
                        ) {
                            Text(
                                when (key) {
                                    KeypadKey.Plus -> "+"
                                    KeypadKey.Minus -> "−"
                                    else -> "="
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val KEY_HEIGHT = 52.dp
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
