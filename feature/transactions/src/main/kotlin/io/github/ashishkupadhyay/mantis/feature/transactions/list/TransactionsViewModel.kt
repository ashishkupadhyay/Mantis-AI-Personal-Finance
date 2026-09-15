package io.github.ashishkupadhyay.mantis.feature.transactions.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessage
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.SpendingRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TagRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.DayTotal
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.ui.transaction.TransactionRowUi
import io.github.ashishkupadhyay.mantis.core.ui.transaction.toRowUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** One element of the transactions list: month band, day header, or a row (doc 05 §4.2). */
sealed interface TransactionListItem {
    data class MonthHeader(val month: YearMonth, val total: Money?) : TransactionListItem
    data class DayHeader(val day: LocalDate, val total: Money?, val count: Int) : TransactionListItem
    data class Row(val row: TransactionRowUi) : TransactionListItem
}

data class TransactionsUiState(
    val filter: TransactionFilter = TransactionFilter.ALL,
    val selection: ImmutableSet<TransactionId> = persistentSetOf(),
    val accounts: ImmutableList<Account> = persistentListOf(),
    val categories: ImmutableList<Category> = persistentListOf(),
    val tags: ImmutableList<Tag> = persistentListOf(),
    val loaded: Boolean = false,
) {
    val selecting: Boolean get() = selection.isNotEmpty()
    val hasFilters: Boolean get() = !filter.copy(query = "").isEmpty
}

sealed interface TransactionsEvent {
    data class QueryChanged(val query: String) : TransactionsEvent
    data class FilterChanged(val filter: TransactionFilter) : TransactionsEvent
    data object ClearFilters : TransactionsEvent
    data class ToggleSelected(val id: TransactionId) : TransactionsEvent
    data object ClearSelection : TransactionsEvent
    data class Delete(val id: TransactionId) : TransactionsEvent
    data class Recategorize(val id: TransactionId, val categoryId: CategoryId?) : TransactionsEvent
    data class BulkRecategorize(val categoryId: CategoryId?) : TransactionsEvent
    data class BulkTag(val name: String) : TransactionsEvent
    data class BulkExclude(val excluded: Boolean) : TransactionsEvent
    data object BulkDelete : TransactionsEvent
    data object MarkTransfer : TransactionsEvent
}

/**
 * Paged, filtered, searchable list with day/month headers and bulk actions (FR-TXN-3/4/5/9, FR-TXN-2 undo).
 * The initial filter comes from a `TransactionsFiltered` link when [initialFilter] is set.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = TransactionsViewModel.Factory::class)
class TransactionsViewModel @AssistedInject constructor(
    @Assisted private val initialFilter: String?,
    private val transactions: TransactionRepository,
    private val spending: SpendingRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val tags: TagRepository,
    private val messages: UserMessages,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(initialFilter: String?): TransactionsViewModel
    }

    private val filter = MutableStateFlow(TransactionFilter.ALL)

    /** Search text as typed in the screen; reaches [filter] after a short debounce (FR-TXN-4). */
    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow<Set<TransactionId>>(emptySet())

    private val lookups = combine(categories.observeCategories(), accounts.observeAccounts(true), tags.observeTags()) { cats, accs, tags ->
        Lookups(cats, accs, tags)
    }

    val state: StateFlow<TransactionsUiState> = combine(filter, selection, lookups) { filter, selection, lookups ->
        TransactionsUiState(
            filter = filter,
            selection = selection.toImmutableSet(),
            accounts = lookups.accounts.toImmutableList(),
            categories = lookups.categories.toImmutableList(),
            tags = lookups.tags.toImmutableList(),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TransactionsUiState())

    /** Rows with headers; the Pager restarts on filter changes only, lookups and totals re-decorate cached pages. */
    val rows: Flow<PagingData<TransactionListItem>> = filter
        .flatMapLatest { transactions.pagedTransactions(it) }
        .cachedIn(viewModelScope)
        .combine(decorations) { page, (totals, lookups) -> page.decorate(lookups, totals) }

    private val decorations: Flow<Pair<List<DayTotal>, Lookups>>
        get() = filter.flatMapLatest { spending.observeDayTotals(it) }.combine(lookups) { totals, lookups -> totals to lookups }

    init {
        viewModelScope.launch {
            query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged().collect { text -> filter.update { it.copy(query = text) } }
        }
        initialFilter?.let { raw ->
            viewModelScope.launch {
                val lookups = lookups.first()
                filter.value = FilterQuery.parse(raw, lookups.categories, lookups.accounts, lookups.tags)
            }
        }
    }

    fun onEvent(event: TransactionsEvent) {
        when (event) {
            is TransactionsEvent.QueryChanged -> query.value = event.query
            is TransactionsEvent.FilterChanged -> filter.value = event.filter.copy(query = filter.value.query)
            TransactionsEvent.ClearFilters -> filter.value = TransactionFilter(query = filter.value.query)
            is TransactionsEvent.ToggleSelected -> selection.update { if (event.id in it) it - event.id else it + event.id }
            TransactionsEvent.ClearSelection -> selection.value = emptySet()
            is TransactionsEvent.Delete -> deleteWithUndo(setOf(event.id))
            is TransactionsEvent.Recategorize -> viewModelScope.launch {
                transactions.setCategory(event.id, event.categoryId, CategorySource.USER)
            }
            is TransactionsEvent.BulkRecategorize -> bulk {
                it.copy(categoryId = event.categoryId, categorySource = CategorySource.USER, categoryConfidence = null, needsReview = false)
            }
            is TransactionsEvent.BulkTag -> viewModelScope.launch {
                val tag = tags.findOrCreate(event.name)
                bulk { it.copy(tags = it.tags + tag.id) }
            }
            is TransactionsEvent.BulkExclude -> bulk { it.copy(isExcluded = event.excluded) }
            TransactionsEvent.BulkDelete -> deleteWithUndo(selection.value).also { selection.value = emptySet() }
            TransactionsEvent.MarkTransfer -> markTransfer()
        }
    }

    private fun bulk(transform: (Transaction) -> Transaction) {
        val ids = selection.value
        selection.value = emptySet()
        viewModelScope.launch {
            val changed = transactions.updateAll(ids, transform)
            messages.show("Updated $changed ${plural(changed)}")
        }
    }

    private fun deleteWithUndo(ids: Set<TransactionId>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val removed = transactions.deleteAll(ids)
            messages.show(
                UserMessage(
                    text = "Deleted $removed ${plural(removed)}",
                    actionLabel = "Undo",
                    action = { transactions.restoreAll(ids) },
                    long = true,
                ),
            )
        }
    }

    private fun markTransfer() {
        val ids = selection.value.toList()
        if (ids.size != 2) {
            messages.show("Select exactly two rows to mark a transfer")
            return
        }
        viewModelScope.launch {
            when (val result = transactions.markTransfer(ids[0], ids[1])) {
                is Outcome.Success -> {
                    selection.value = emptySet()
                    messages.show("Marked as a transfer")
                }
                is Outcome.Failure -> messages.show(result.error.message)
            }
        }
    }

    private fun plural(count: Int) = if (count == 1) "transaction" else "transactions"

    private fun PagingData<Transaction>.decorate(lookups: Lookups, totals: List<DayTotal>): PagingData<TransactionListItem> {
        val cats = lookups.categories.associateBy { it.id }
        val accs = lookups.accounts.associateBy { it.id }
        val byDay = totals.associateBy { it.day }
        val byMonth = totals.groupBy { YearMonth.from(it.day) }.mapValues { (_, days) ->
            days.fold(null as Money?) { acc, d -> if (acc == null) d.total else acc + d.total }
        }
        return map { TransactionListItem.Row(it.toRowUi(cats, accs)) as TransactionListItem }
            .insertSeparators { before, after ->
                val next = (after as? TransactionListItem.Row)?.row ?: return@insertSeparators null
                val previous = (before as? TransactionListItem.Row)?.row
                val day = next.date
                val newMonth = previous == null || YearMonth.from(previous.date) != YearMonth.from(day)
                val newDay = previous == null || previous.date != day
                when {
                    newMonth -> TransactionListItem.MonthHeader(YearMonth.from(day), byMonth[YearMonth.from(day)])
                    newDay -> TransactionListItem.DayHeader(day, byDay[day]?.total, byDay[day]?.count ?: 0)
                    else -> null
                }
            }
            .insertSeparators { before, after ->
                // A month band is immediately followed by that day's header so both totals are visible.
                val month = before as? TransactionListItem.MonthHeader ?: return@insertSeparators null
                val next = (after as? TransactionListItem.Row)?.row ?: return@insertSeparators null
                if (YearMonth.from(next.date) != month.month) return@insertSeparators null
                TransactionListItem.DayHeader(next.date, byDay[next.date]?.total, byDay[next.date]?.count ?: 0)
            }
    }

    private class Lookups(val categories: List<Category>, val accounts: List<Account>, val tags: List<Tag>)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
