package io.github.ashishkupadhyay.mantis.feature.transactions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.DateRange
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import io.github.ashishkupadhyay.mantis.core.ui.format.Dates
import io.github.ashishkupadhyay.mantis.feature.transactions.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Which filter chip opened the sheet (FR-TXN-5). */
enum class FilterSheetKind { ACCOUNT, CATEGORY, TYPE, DATE, TAG }

/** Date presets offered before "custom range". */
enum class DatePreset(val labelRes: Int) {
    THIS_MONTH(R.string.filter_date_this_month),
    LAST_MONTH(R.string.filter_date_last_month),
    LAST_90_DAYS(R.string.filter_date_last_90_days),
    THIS_YEAR(R.string.filter_date_this_year),
    ALL_TIME(R.string.filter_date_all_time),
    ;

    fun range(today: LocalDate): DateRange? = when (this) {
        THIS_MONTH -> DateRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
        LAST_MONTH -> today.minusMonths(1).let { DateRange(it.withDayOfMonth(1), it.withDayOfMonth(it.lengthOfMonth())) }
        LAST_90_DAYS -> DateRange(today.minusDays(NINETY), today)
        THIS_YEAR -> DateRange(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()))
        ALL_TIME -> null
    }

    private companion object {
        const val NINETY = 89L
    }
}

/**
 * One sheet per filter chip: edits a local copy of the filter and hands it back on *Apply*, so cancelling never
 * touches the list. All choices are multi-select except the date range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    kind: FilterSheetKind,
    state: TransactionsUiState,
    today: LocalDate,
    onApply: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(kind) { mutableStateOf(state.filter) }
    var customRange by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (kind) {
                FilterSheetKind.ACCOUNT -> AccountChoices(state, draft) { draft = it }
                FilterSheetKind.CATEGORY -> CategoryChoices(state.categories, draft) { draft = it }
                FilterSheetKind.TYPE -> TypeChoices(draft) { draft = it }
                FilterSheetKind.DATE -> DateChoices(draft, today, onCustom = { customRange = true }) { draft = it }
                FilterSheetKind.TAG -> TagChoices(state, draft) { draft = it }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onApply(draft) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.filter_apply)) }
            }
        }
    }
    if (customRange) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = draft.dateRange?.start?.toEpochMillis(),
            initialSelectedEndDateMillis = draft.dateRange?.endInclusive?.toEpochMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { customRange = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.toLocalDate()
                        val end = pickerState.selectedEndDateMillis?.toLocalDate()
                        if (start != null && end != null) draft = draft.copy(dateRange = DateRange(start, end))
                        customRange = false
                    },
                ) { Text(stringResource(R.string.filter_apply)) }
            },
            dismissButton = { TextButton(onClick = { customRange = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DateRangePicker(state = pickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun AccountChoices(state: TransactionsUiState, draft: TransactionFilter, onChange: (TransactionFilter) -> Unit) {
    SectionTitle(stringResource(R.string.filter_accounts))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.accounts.forEach { account ->
            val selected = account.id in draft.accountIds
            FilterChip(
                selected = selected,
                onClick = {
                    onChange(draft.copy(accountIds = if (selected) draft.accountIds - account.id else draft.accountIds + account.id))
                },
                label = { Text(account.name) },
            )
        }
    }
}

@Composable
private fun TypeChoices(draft: TransactionFilter, onChange: (TransactionFilter) -> Unit) {
    SectionTitle(stringResource(R.string.filter_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransactionType.entries.forEach { type ->
            val selected = type in draft.types
            FilterChip(
                selected = selected,
                onClick = { onChange(draft.copy(types = if (selected) draft.types - type else draft.types + type)) },
                label = { Text(stringResource(type.labelRes())) },
            )
        }
    }
    SectionTitle(stringResource(R.string.filter_flags))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = draft.needsReviewOnly,
            onClick = { onChange(draft.copy(needsReviewOnly = !draft.needsReviewOnly)) },
            label = { Text(stringResource(R.string.filter_needs_review)) },
        )
        FilterChip(
            selected = draft.uncategorizedOnly,
            onClick = { onChange(draft.copy(uncategorizedOnly = !draft.uncategorizedOnly)) },
            label = { Text(stringResource(R.string.filter_uncategorized)) },
        )
        FilterChip(
            selected = !draft.includeExcluded,
            onClick = { onChange(draft.copy(includeExcluded = !draft.includeExcluded)) },
            label = { Text(stringResource(R.string.filter_hide_excluded)) },
        )
    }
}

@Composable
private fun DateChoices(draft: TransactionFilter, today: LocalDate, onCustom: () -> Unit, onChange: (TransactionFilter) -> Unit) {
    SectionTitle(stringResource(R.string.filter_date))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DatePreset.entries.forEach { preset ->
            val range = preset.range(today)
            FilterChip(
                selected = draft.dateRange == range,
                onClick = { onChange(draft.copy(dateRange = range)) },
                label = { Text(stringResource(preset.labelRes)) },
            )
        }
        FilterChip(
            selected = draft.dateRange != null && DatePreset.entries.none { it.range(today) == draft.dateRange },
            onClick = onCustom,
            label = { Text(stringResource(R.string.filter_date_custom)) },
        )
    }
    draft.dateRange?.let { range ->
        Text(
            "${Dates.short(range.start, today)} – ${Dates.short(range.endInclusive, today)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagChoices(state: TransactionsUiState, draft: TransactionFilter, onChange: (TransactionFilter) -> Unit) {
    SectionTitle(stringResource(R.string.filter_tags))
    if (state.tags.isEmpty()) {
        Text(stringResource(R.string.filter_no_tags), style = MaterialTheme.typography.bodyMedium)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.tags.forEach { tag ->
            val selected = tag.id in draft.tagIds
            FilterChip(
                selected = selected,
                onClick = { onChange(draft.copy(tagIds = if (selected) draft.tagIds - tag.id else draft.tagIds + tag.id)) },
                label = { Text("#${tag.name}") },
            )
        }
    }
}

@Composable
private fun CategoryChoices(categories: List<Category>, draft: TransactionFilter, onChange: (TransactionFilter) -> Unit) {
    val leaves = categories.filter { !it.isGroup && !it.isHidden && it.key != DefaultTaxonomy.KEY_UNCATEGORIZED }.groupBy { it.parentId }
    val groups = categories.filter { it.isGroup && it.kind != CategoryKind.SYSTEM }.sortedBy { it.sortOrder }
    groups.forEach { group ->
        val children = leaves[group.id].orEmpty().sortedBy { it.sortOrder }
        if (children.isEmpty()) return@forEach
        SectionTitle(group.name)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            children.forEach { leaf ->
                val selected = leaf.id in draft.categoryIds
                FilterChip(
                    selected = selected,
                    onClick = {
                        onChange(draft.copy(categoryIds = if (selected) draft.categoryIds - leaf.id else draft.categoryIds + leaf.id))
                    },
                    label = { Text(leaf.name) },
                )
            }
        }
    }
}

internal fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.EXPENSE -> R.string.type_expense
    TransactionType.INCOME -> R.string.type_income
    TransactionType.TRANSFER -> R.string.type_transfer
}

internal fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
