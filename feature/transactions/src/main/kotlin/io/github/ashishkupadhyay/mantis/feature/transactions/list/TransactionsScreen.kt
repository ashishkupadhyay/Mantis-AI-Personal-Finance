package io.github.ashishkupadhyay.mantis.feature.transactions.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.ashishkupadhyay.mantis.core.designsystem.component.EmptyState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.FabMenuItem
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisFabMenu
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisFloatingToolbar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.ToolbarAction
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.format.Dates
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyText
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyTone
import io.github.ashishkupadhyay.mantis.core.ui.transaction.TransactionRow
import io.github.ashishkupadhyay.mantis.feature.transactions.R
import io.github.ashishkupadhyay.mantis.core.ui.category.CategoryPickerSheet
import kotlinx.collections.immutable.persistentListOf
import java.time.LocalDate

/**
 * Transactions list (doc 05 §4.2): search bar that expands full screen, filter chips, paged rows under sticky
 * month bands and day headers, swipe to recategorize / delete, long-press multi-select with a floating toolbar,
 * and the FAB menu for adding. Pure: everything comes in through [state], [rows] and [onEvent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    state: TransactionsUiState,
    rows: LazyPagingItems<TransactionListItem>,
    today: LocalDate,
    onEvent: (TransactionsEvent) -> Unit,
    onOpen: (TransactionId) -> Unit,
    onAdd: (TransactionType) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    // The text lives here, not in the ViewModel: a hoisted value that round-trips through a StateFlow drops fast keystrokes.
    var query by rememberSaveable { mutableStateOf(state.filter.query) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<FilterSheetKind?>(null) }
    var recategorizing by remember { mutableStateOf<TransactionId?>(null) }
    var bulkCategory by rememberSaveable { mutableStateOf(false) }
    var bulkTag by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!state.selecting && !searchExpanded) {
                MantisFabMenu(
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    items = persistentListOf(
                        FabMenuItem(stringResource(R.string.type_expense), Icons.Default.ArrowUpward) { onAdd(TransactionType.EXPENSE) },
                        FabMenuItem(stringResource(R.string.type_income), Icons.Default.ArrowDownward) { onAdd(TransactionType.INCOME) },
                        FabMenuItem(stringResource(R.string.type_transfer), Icons.Default.SwapHoriz) { onAdd(TransactionType.TRANSFER) },
                        FabMenuItem(stringResource(R.string.action_import_csv), Icons.Default.UploadFile, onImport),
                    ),
                    collapsedContentDescription = stringResource(R.string.add_transaction),
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().consumeWindowInsets(innerPadding)) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null && !searchExpanded) {
                        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = query,
                                onQueryChange = { query = it; onEvent(TransactionsEvent.QueryChanged(it)) },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                leadingIcon = {
                                    if (searchExpanded) {
                                        IconButton(onClick = { searchExpanded = false }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = stringResource(R.string.action_back),
                                            )
                                        }
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = null)
                                    }
                                },
                                trailingIcon = {
                                    if (query.isNotEmpty()) {
                                        IconButton(onClick = { query = ""; onEvent(TransactionsEvent.QueryChanged("")) }) {
                                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear))
                                        }
                                    }
                                },
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        modifier = Modifier.weight(1f).padding(horizontal = if (searchExpanded) 0.dp else 16.dp),
                    ) {
                        TransactionList(state, rows, today, onEvent, onOpen, onRecategorize = { recategorizing = it }, bottomPadding = 0.dp)
                    }
                }
                FilterChips(state, onOpenSheet = { sheet = it }, onClear = { onEvent(TransactionsEvent.ClearFilters) })
                TransactionList(
                    state, rows, today, onEvent, onOpen,
                    onRecategorize = { recategorizing = it },
                    bottomPadding = innerPadding.calculateBottomPadding() + LIST_BOTTOM_CLEARANCE,
                )
            }
            SelectionToolbar(
                state = state,
                onEvent = onEvent,
                onRecategorize = { bulkCategory = true },
                onTag = { bulkTag = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = innerPadding.calculateBottomPadding() + 16.dp),
            )
        }
    }

    sheet?.let { kind ->
        FilterSheet(
            kind = kind,
            state = state,
            today = today,
            onApply = { onEvent(TransactionsEvent.FilterChanged(it)); sheet = null },
            onDismiss = { sheet = null },
        )
    }
    recategorizing?.let { id ->
        CategoryPickerSheet(
            categories = state.categories,
            kinds = setOf(CategoryKind.EXPENSE, CategoryKind.INCOME),
            selected = null,
            onPick = { onEvent(TransactionsEvent.Recategorize(id, it)); recategorizing = null },
            onDismiss = { recategorizing = null },
        )
    }
    if (bulkCategory) {
        CategoryPickerSheet(
            categories = state.categories,
            kinds = setOf(CategoryKind.EXPENSE, CategoryKind.INCOME),
            selected = null,
            onPick = { onEvent(TransactionsEvent.BulkRecategorize(it)); bulkCategory = false },
            onDismiss = { bulkCategory = false },
        )
    }
    if (bulkTag) {
        TagDialog(onConfirm = { onEvent(TransactionsEvent.BulkTag(it)); bulkTag = false }, onDismiss = { bulkTag = false })
    }
}

@Composable
private fun FilterChips(state: TransactionsUiState, onOpenSheet: (FilterSheetKind) -> Unit, onClear: () -> Unit) {
    val filter = state.filter
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.hasFilters) {
            IconButton(onClick = onClear, modifier = Modifier.padding(end = 4.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.filter_clear_all))
            }
        }
        FilterChipButton(accountLabel(state), filter.accountIds.isNotEmpty()) { onOpenSheet(FilterSheetKind.ACCOUNT) }
        FilterChipButton(categoryLabel(state), filter.categoryIds.isNotEmpty()) { onOpenSheet(FilterSheetKind.CATEGORY) }
        val typeActive = filter.types.isNotEmpty() || filter.needsReviewOnly || filter.uncategorizedOnly || !filter.includeExcluded
        FilterChipButton(typeLabel(filter), typeActive) { onOpenSheet(FilterSheetKind.TYPE) }
        FilterChipButton(stringResource(R.string.filter_date), filter.dateRange != null) { onOpenSheet(FilterSheetKind.DATE) }
        val tagLabel = if (filter.tagIds.isEmpty()) {
            stringResource(R.string.filter_tags)
        } else {
            pluralStringResource(R.plurals.filter_n_tags, filter.tagIds.size, filter.tagIds.size)
        }
        FilterChipButton(tagLabel, filter.tagIds.isNotEmpty()) { onOpenSheet(FilterSheetKind.TAG) }
    }
}

@Composable
private fun accountLabel(state: TransactionsUiState): String = when (state.filter.accountIds.size) {
    0 -> stringResource(R.string.filter_accounts)
    1 -> state.accounts.firstOrNull { it.id == state.filter.accountIds.first() }?.name ?: stringResource(R.string.filter_accounts)
    else -> pluralStringResource(R.plurals.filter_n_accounts, state.filter.accountIds.size, state.filter.accountIds.size)
}

@Composable
private fun categoryLabel(state: TransactionsUiState): String = when (state.filter.categoryIds.size) {
    0 -> stringResource(R.string.filter_categories)
    1 -> state.categories.firstOrNull { it.id == state.filter.categoryIds.first() }?.name ?: stringResource(R.string.filter_categories)
    else -> pluralStringResource(R.plurals.filter_n_categories, state.filter.categoryIds.size, state.filter.categoryIds.size)
}

@Composable
private fun typeLabel(filter: TransactionFilter): String = when {
    filter.types.size == 1 -> stringResource(filter.types.first().labelRes())
    filter.needsReviewOnly -> stringResource(R.string.filter_needs_review)
    filter.uncategorizedOnly -> stringResource(R.string.filter_uncategorized)
    else -> stringResource(R.string.filter_type)
}

@Composable
private fun FilterChipButton(label: String, active: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionList(
    state: TransactionsUiState,
    rows: LazyPagingItems<TransactionListItem>,
    today: LocalDate,
    onEvent: (TransactionsEvent) -> Unit,
    onOpen: (TransactionId) -> Unit,
    onRecategorize: (TransactionId) -> Unit,
    bottomPadding: Dp,
) {
    if (rows.itemCount == 0 && rows.loadState.refresh is LoadState.NotLoading) {
        val narrowed = state.hasFilters || state.filter.query.isNotBlank()
        EmptyState(
            icon = Icons.Default.ReceiptLong,
            title = stringResource(if (narrowed) R.string.empty_filtered_title else R.string.empty_title),
            body = stringResource(if (narrowed) R.string.empty_filtered_body else R.string.empty_body),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
        for (index in 0 until rows.itemCount) {
            when (val item = rows.peek(index)) {
                is TransactionListItem.MonthHeader -> stickyHeader(key = "m-${item.month}") { MonthBand(item) }
                is TransactionListItem.DayHeader -> item(key = "d-${item.day}") { DayHeader(item, today) }
                is TransactionListItem.Row -> item(key = item.row.id.value) {
                    val row = (rows[index] as? TransactionListItem.Row)?.row ?: return@item
                    SwipeableRow(
                        selected = row.id in state.selection,
                        selecting = state.selecting,
                        onDelete = { onEvent(TransactionsEvent.Delete(row.id)) },
                        onRecategorize = { onRecategorize(row.id) },
                    ) {
                        TransactionRow(
                            row = row,
                            onClick = { if (state.selecting) onEvent(TransactionsEvent.ToggleSelected(row.id)) else onOpen(row.id) },
                            onLongClick = { onEvent(TransactionsEvent.ToggleSelected(row.id)) },
                            selected = row.id in state.selection,
                        )
                    }
                }
                null -> item(key = "placeholder-$index") { rows[index] }
            }
        }
    }
}

/** Swipe right = recategorize (snaps back and opens the picker), swipe left = delete with undo (doc 05 §4.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    selected: Boolean,
    selecting: Boolean,
    onDelete: () -> Unit,
    onRecategorize: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onRecategorize()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        gesturesEnabled = !selecting && !selected,
        backgroundContent = {
            val toEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (toEnd) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(
                    if (toEnd) Icons.Default.Category else Icons.Default.Delete,
                    contentDescription = stringResource(if (toEnd) R.string.action_recategorize else R.string.action_delete),
                )
            }
        },
    ) { content() }
}

@Composable
private fun MonthBand(item: TransactionListItem.MonthHeader) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Dates.month(item.month.atDay(1)), style = MaterialTheme.typography.titleMedium)
        item.total?.let { MoneyText(it, style = MaterialTheme.typography.titleMedium, tone = MoneyTone.SIGNED, showMinor = false) }
    }
}

@Composable
private fun DayHeader(item: TransactionListItem.DayHeader, today: LocalDate) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            Dates.relative(item.day, today, stringResource(R.string.day_today), stringResource(R.string.day_yesterday)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.total?.let {
            MoneyText(it, style = MaterialTheme.typography.labelLarge, tone = MoneyTone.NEUTRAL, showMinor = false)
        }
    }
}

@Composable
private fun SelectionToolbar(
    state: TransactionsUiState,
    onEvent: (TransactionsEvent) -> Unit,
    onRecategorize: () -> Unit,
    onTag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.selecting) {
            Text(
                pluralStringResource(R.plurals.selection_count, state.selection.size, state.selection.size),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        MantisFloatingToolbar(
            expanded = state.selecting,
            actions = persistentListOf(
                ToolbarAction(stringResource(R.string.action_recategorize), Icons.Default.Category, onClick = onRecategorize),
                ToolbarAction(stringResource(R.string.action_tag), Icons.Default.Label, onClick = onTag),
                ToolbarAction(
                    stringResource(R.string.action_exclude), Icons.Default.VisibilityOff,
                    onClick = { onEvent(TransactionsEvent.BulkExclude(true)) },
                ),
                ToolbarAction(
                    stringResource(R.string.action_transfer), Icons.Default.SwapHoriz,
                    onClick = { onEvent(TransactionsEvent.MarkTransfer) }, enabled = state.selection.size == 2,
                ),
                ToolbarAction(
                    stringResource(R.string.action_delete), Icons.Default.Delete,
                    onClick = { onEvent(TransactionsEvent.BulkDelete) },
                ),
                ToolbarAction(
                    stringResource(R.string.action_clear_selection), Icons.Default.Close,
                    onClick = { onEvent(TransactionsEvent.ClearSelection) },
                ),
            ),
        )
    }
}

@Composable
private fun TagDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.tag_dialog_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private val LIST_BOTTOM_CLEARANCE = 96.dp
