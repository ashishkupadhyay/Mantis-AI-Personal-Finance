package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.BillingCycle
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.transaction.TransactionRowUi
import io.github.ashishkupadhyay.mantis.core.ui.transaction.toRowUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

/** Statement-cycle facts for a credit card (FR-ACC-5). */
data class CardCycleUi(val cycle: BillingCycle, val cycleSpend: Money, val daysUntilDue: Long, val daysUntilStatement: Long)

data class AccountDetailUiState(
    val account: Account? = null,
    val balance: Money? = null,
    val card: CardCycleUi? = null,
    val loaded: Boolean = false,
    val missing: Boolean = false,
)

sealed interface AccountDetailEvent {
    data class SetArchived(val archived: Boolean) : AccountDetailEvent
    data object Delete : AccountDetailEvent
}

sealed interface AccountDetailEffect {
    data class Deleted(val removedTransactions: Int) : AccountDetailEffect
    data class Message(val text: String) : AccountDetailEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AccountDetailViewModel.Factory::class)
class AccountDetailViewModel @AssistedInject constructor(
    @Assisted private val accountId: String,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(accountId: String): AccountDetailViewModel
    }

    private val id = AccountId(accountId)

    val state: StateFlow<AccountDetailUiState> = combine(accounts.observeAccount(id), accounts.observeBalance(id)) { account, balance ->
        if (account == null) return@combine AccountDetailUiState(loaded = true, missing = true)
        AccountDetailUiState(account = account, balance = balance, card = cardCycle(account), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountDetailUiState())

    /** Rows for this account, newest first, with category/account names resolved. */
    val rows: Flow<PagingData<TransactionRowUi>> = combine(categories.observeCategories(), accounts.observeAccounts(true)) { cats, accs ->
        cats.associateBy { it.id } to accs.associateBy { it.id }
    }.flatMapLatest { (cats, accs) ->
        transactions.pagedTransactions(TransactionFilter(accountIds = setOf(id)))
            .map { page: PagingData<Transaction> -> page.map { transaction -> transaction.toRowUi(cats, accs) } }
    }.cachedIn(viewModelScope)

    private val _effects = Channel<AccountDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: AccountDetailEvent) {
        when (event) {
            is AccountDetailEvent.SetArchived -> viewModelScope.launch { accounts.setArchived(id, event.archived) }
            AccountDetailEvent.Delete -> viewModelScope.launch {
                when (val result = accounts.delete(id)) {
                    is Outcome.Success -> _effects.send(AccountDetailEffect.Deleted(result.value))
                    is Outcome.Failure -> _effects.send(AccountDetailEffect.Message(result.error.message))
                }
            }
        }
    }

    private suspend fun cardCycle(account: Account): CardCycleUi? {
        val statementDay = account.statementDay ?: return null
        val dueDay = account.dueDay ?: return null
        val today = clock.today()
        val cycle = BillingCycle.of(statementDay, dueDay, today)
        val spend = transactions.transactions(id, cycle.cycle)
            .filter { it.type == TransactionType.EXPENSE && !it.isExcluded }
            .fold(Money.zero(account.currency)) { acc, t -> acc + t.amount }
        return CardCycleUi(
            cycle = cycle,
            cycleSpend = spend,
            daysUntilDue = cycle.daysUntilDue(today),
            daysUntilStatement = ChronoUnit.DAYS.between(today, cycle.statementDate),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
