package io.github.ashishkupadhyay.mantis.feature.transactions.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.usecase.AddTransactionUseCase
import io.github.ashishkupadhyay.mantis.core.domain.usecase.TransactionDraft
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionPrefill
import io.github.ashishkupadhyay.mantis.core.ui.category.kindsFor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The add/edit form (doc 05 §4.4). Amount comes from the keypad; everything else is a chip or a field. */
data class AddTransactionUiState(
    val editId: TransactionId? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: AmountEntry = AmountEntry(),
    val currency: Currency = Currency.INR,
    val accountId: AccountId? = null,
    val toAccountId: AccountId? = null,
    val categoryId: CategoryId? = null,
    val date: LocalDate = LocalDate.ofEpochDay(0),
    val time: LocalTime = LocalTime.NOON,
    val description: String = "",
    val notes: String = "",
    val tags: ImmutableList<String> = persistentListOf(),
    val tagInput: String = "",
    val isExcluded: Boolean = false,
    val accounts: ImmutableList<Account> = persistentListOf(),
    val categories: ImmutableList<Category> = persistentListOf(),
    /** Recent/likely categories for the current type, shown as the first chips (doc 05 §4.4). */
    val likely: ImmutableList<Category> = persistentListOf(),
    val amountError: Boolean = false,
    val accountError: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = false,
) {
    val isNew: Boolean get() = editId == null
    val isTransfer: Boolean get() = type == TransactionType.TRANSFER
    val money: Money get() = Money.ofMajor(amount.value, currency)
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == categoryId }
}

sealed interface AddTransactionEvent {
    data class Key(val key: KeypadKey) : AddTransactionEvent
    data class TypeChanged(val type: TransactionType) : AddTransactionEvent
    data class AccountChanged(val id: AccountId) : AddTransactionEvent
    data class ToAccountChanged(val id: AccountId) : AddTransactionEvent
    data class CategoryChanged(val id: CategoryId?) : AddTransactionEvent
    data class DateChanged(val date: LocalDate) : AddTransactionEvent
    data class TimeChanged(val time: LocalTime) : AddTransactionEvent
    data class DescriptionChanged(val text: String) : AddTransactionEvent
    data class NotesChanged(val text: String) : AddTransactionEvent
    data class TagInputChanged(val text: String) : AddTransactionEvent
    data object CommitTag : AddTransactionEvent
    data class RemoveTag(val name: String) : AddTransactionEvent
    data class ExcludedChanged(val excluded: Boolean) : AddTransactionEvent
    /** [addAnother] keeps the sheet open with the amount cleared (doc 05 §4.4 split button). */
    data class Save(val addAnother: Boolean = false, val ignoreDuplicates: Boolean = false) : AddTransactionEvent
}

sealed interface AddTransactionEffect {
    data class Saved(val id: TransactionId, val addAnother: Boolean) : AddTransactionEffect
    /** FR-TXN-11: the entry looks like [duplicates]; the sheet asks before saving anyway. */
    data class PossibleDuplicate(val duplicates: List<Transaction>, val addAnother: Boolean) : AddTransactionEffect
}

@HiltViewModel(assistedFactory = AddTransactionViewModel.Factory::class)
class AddTransactionViewModel @AssistedInject constructor(
    @Assisted private val editId: String?,
    @Assisted private val prefill: TransactionPrefill?,
    private val addTransaction: AddTransactionUseCase,
    private val lookups: EditorLookups,
    private val clock: Clock,
    private val messages: UserMessages,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(editId: String?, prefill: TransactionPrefill?): AddTransactionViewModel
    }

    private val _state = MutableStateFlow(AddTransactionUiState())
    val state: StateFlow<AddTransactionUiState> = _state.asStateFlow()

    private val _effects = Channel<AddTransactionEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val zone: ZoneId get() = clock.zone

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val accounts = lookups.accounts()
        val categories = lookups.categories()
        val existing = editId?.let { lookups.transaction(TransactionId(it)) }
        val now = clock.nowLocal()
        val base = AddTransactionUiState(
            accounts = accounts.toImmutableList(),
            categories = categories.toImmutableList(),
            date = now.toLocalDate(),
            time = now.toLocalTime().withSecond(0).withNano(0),
            loaded = true,
        )
        _state.value = if (existing != null) base.fromExisting(existing) else base.fromPrefill(prefill, accounts, categories)
        refreshLikely()
    }

    private suspend fun AddTransactionUiState.fromExisting(existing: Transaction): AddTransactionUiState {
        val at = existing.postedAt.atZone(zone)
        return copy(
            editId = existing.id,
            type = existing.type,
            amount = AmountEntry(operand = existing.amount.abs().major.stripTrailingZeros().toPlainString()),
            currency = existing.amount.currency,
            accountId = existing.accountId,
            toAccountId = lookups.pairOf(existing)?.accountId,
            categoryId = existing.categoryId,
            date = at.toLocalDate(),
            time = at.toLocalTime().withSecond(0).withNano(0),
            description = existing.descriptionRaw,
            notes = existing.notes.orEmpty(),
            tags = lookups.tagNames(existing).toImmutableList(),
            isExcluded = existing.isExcluded,
        )
    }

    private fun AddTransactionUiState.fromPrefill(
        prefill: TransactionPrefill?,
        accounts: List<Account>,
        categories: List<Category>,
    ): AddTransactionUiState {
        val type = prefill?.type?.let { name -> TransactionType.entries.firstOrNull { it.name == name } } ?: TransactionType.EXPENSE
        val account = prefill?.accountId?.let { id -> accounts.firstOrNull { it.id.value == id } } ?: accounts.firstOrNull()
        val currency = account?.currency ?: currency
        return copy(
            type = type,
            currency = currency,
            amount = prefill?.amountMinor?.takeIf { it > 0 }
                ?.let { AmountEntry(operand = Money(it, currency).major.stripTrailingZeros().toPlainString()) }
                ?: AmountEntry(),
            accountId = account?.id,
            toAccountId = accounts.firstOrNull { it.id != account?.id }?.id,
            categoryId = prefill?.categoryKey?.let { key -> categories.firstOrNull { it.key == key }?.id },
            description = prefill?.merchant.orEmpty(),
            notes = prefill?.note.orEmpty(),
        )
    }

    fun onEvent(event: AddTransactionEvent) {
        when (event) {
            is AddTransactionEvent.Save -> save(event.addAnother, event.ignoreDuplicates)
            is AddTransactionEvent.TypeChanged -> {
                _state.update { s ->
                    val keep = s.selectedCategory?.kind in kindsFor(event.type)
                    s.copy(type = event.type, categoryId = if (keep) s.categoryId else null)
                }
                refreshLikely()
            }
            is AddTransactionEvent.AccountChanged -> _state.update { s ->
                val currency = s.accounts.firstOrNull { it.id == event.id }?.currency ?: s.currency
                s.copy(
                    accountId = event.id,
                    currency = currency,
                    toAccountId = if (s.toAccountId == event.id) null else s.toAccountId,
                    accountError = false,
                )
            }
            is AddTransactionEvent.ToAccountChanged -> _state.update { it.copy(toAccountId = event.id, accountError = false) }
            else -> _state.update { it.edited(event) }
        }
    }

    /** Field edits are pure state transitions. */
    private fun AddTransactionUiState.edited(event: AddTransactionEvent): AddTransactionUiState = when (event) {
        is AddTransactionEvent.Key -> copy(amount = amount.press(event.key), amountError = false)
        is AddTransactionEvent.CategoryChanged -> copy(categoryId = event.id)
        is AddTransactionEvent.DateChanged -> copy(date = event.date)
        is AddTransactionEvent.TimeChanged -> copy(time = event.time)
        is AddTransactionEvent.DescriptionChanged -> copy(description = event.text.take(MAX_TEXT))
        is AddTransactionEvent.NotesChanged -> copy(notes = event.text.take(MAX_NOTES))
        is AddTransactionEvent.TagInputChanged -> copy(tagInput = event.text.take(MAX_TAG))
        AddTransactionEvent.CommitTag -> {
            val name = tagInput.trim().removePrefix("#")
            if (name.isEmpty()) copy(tagInput = "") else copy(tags = (tags + name).distinct().toImmutableList(), tagInput = "")
        }
        is AddTransactionEvent.RemoveTag -> copy(tags = tags.filter { it != event.name }.toImmutableList())
        is AddTransactionEvent.ExcludedChanged -> copy(isExcluded = event.excluded)
        else -> this
    }

    private fun save(addAnother: Boolean, ignoreDuplicates: Boolean) {
        val s = _state.value
        if (s.saving) return
        val amountOk = s.amount.value.signum() > 0
        val accountOk = s.accountId != null && (!s.isTransfer || (s.toAccountId != null && s.toAccountId != s.accountId))
        if (!amountOk || !accountOk) {
            _state.update { it.copy(amountError = !amountOk, accountError = !accountOk) }
            return
        }
        val draft = s.toDraft()
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (!ignoreDuplicates) {
                val duplicates = addTransaction.duplicatesOf(draft)
                if (duplicates.isNotEmpty()) {
                    _state.update { it.copy(saving = false) }
                    _effects.send(AddTransactionEffect.PossibleDuplicate(duplicates, addAnother))
                    return@launch
                }
            }
            when (val result = addTransaction(draft)) {
                is Outcome.Success -> {
                    _state.update { if (addAnother) it.cleared() else it.copy(saving = false) }
                    messages.show(if (s.isNew) "Transaction saved" else "Transaction updated")
                    _effects.send(AddTransactionEffect.Saved(result.value, addAnother))
                }
                is Outcome.Failure -> {
                    _state.update { it.copy(saving = false) }
                    messages.show(result.error.message)
                }
            }
        }
    }

    /** "Save & add another": same type, account and date; a fresh amount, text and tags. */
    private fun AddTransactionUiState.cleared() =
        copy(saving = false, amount = AmountEntry(), description = "", notes = "", tags = persistentListOf(), categoryId = null)

    private fun AddTransactionUiState.toDraft(): TransactionDraft = TransactionDraft(
        type = type,
        amount = money,
        accountId = checkNotNull(accountId),
        postedAt = date.atTime(time).atZone(zone).toInstant(),
        description = description,
        id = editId,
        toAccountId = if (isTransfer) toAccountId else null,
        categoryId = if (isTransfer) null else categoryId,
        notes = notes,
        tagNames = tags,
        isExcluded = isExcluded,
    )

    private fun refreshLikely() {
        viewModelScope.launch {
            val s = _state.value
            val kinds = kindsFor(s.type)
            val ids = if (s.isTransfer) emptyList() else lookups.likely(s.type, LIKELY_LIMIT)
            val byId = s.categories.associateBy { it.id }
            val recent = ids.mapNotNull(byId::get).filter { it.kind in kinds && !it.isGroup }
            val fallback = s.categories
                .filter { it.kind in kinds && !it.isGroup && it.key != DefaultTaxonomy.KEY_UNCATEGORIZED }
                .sortedWith(compareBy({ it.colorSeed }, { it.sortOrder }))
            _state.update { it.copy(likely = (recent + fallback).distinctBy { c -> c.id }.take(LIKELY_LIMIT).toImmutableList()) }
        }
    }

    private companion object {
        const val MAX_TEXT = 120
        const val MAX_NOTES = 500
        const val MAX_TAG = 40
        const val LIKELY_LIMIT = 8
    }
}
