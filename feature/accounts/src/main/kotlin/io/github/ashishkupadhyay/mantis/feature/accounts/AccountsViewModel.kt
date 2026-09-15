package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.model.AccountBalance
import io.github.ashishkupadhyay.mantis.core.model.AccountTotals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AccountsUiState(
    val active: ImmutableList<AccountBalance> = persistentListOf(),
    val archived: ImmutableList<AccountBalance> = persistentListOf(),
    val totals: AccountTotals? = null,
    val loaded: Boolean = false,
)

/** FR-ACC-1/2: every account with its balance, plus the net position in the home currency. */
@HiltViewModel
class AccountsViewModel @Inject constructor(accounts: AccountRepository, preferences: PreferencesRepository) : ViewModel() {

    val state: StateFlow<AccountsUiState> = combine(
        accounts.observeBalances(includeArchived = true),
        preferences.preferences,
    ) { all, prefs ->
        val currency = Currency.of(prefs.currencyCode)
        AccountsUiState(
            active = all.filter { !it.account.isArchived }.toImmutableList(),
            archived = all.filter { it.account.isArchived }.toImmutableList(),
            totals = AccountTotals.of(all, currency),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AccountsUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
