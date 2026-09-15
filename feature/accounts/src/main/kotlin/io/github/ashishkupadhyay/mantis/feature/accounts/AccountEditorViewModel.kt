package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.Institutions
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.defaultIcon
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

/** The editor form (FR-ACC-1). Money is typed as major units and converted on save; validation is per field. */
data class AccountEditorUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val institutionId: String? = null,
    val last4: String = "",
    val currencyCode: String = "INR",
    val openingBalance: String = "0",
    val openingDate: LocalDate = LocalDate.ofEpochDay(0),
    val colorSeed: Int = 0,
    val icon: String = AccountType.BANK.defaultIcon,
    val statementDay: Int = 18,
    val dueDay: Int = 6,
    val nameError: Boolean = false,
    val last4Error: Boolean = false,
    val balanceError: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = false,
) {
    val isCard: Boolean get() = type == AccountType.CREDIT_CARD
    val institutions get() = Institutions.forType(type)
}

sealed interface AccountEditorEvent {
    data class NameChanged(val value: String) : AccountEditorEvent
    data class TypeChanged(val type: AccountType) : AccountEditorEvent
    data class InstitutionChanged(val id: String?) : AccountEditorEvent
    data class Last4Changed(val value: String) : AccountEditorEvent
    data class CurrencyChanged(val code: String) : AccountEditorEvent
    data class OpeningBalanceChanged(val value: String) : AccountEditorEvent
    data class OpeningDateChanged(val date: LocalDate) : AccountEditorEvent
    data class ColorChanged(val seed: Int) : AccountEditorEvent
    data class IconChanged(val icon: String) : AccountEditorEvent
    data class StatementDayChanged(val day: Int) : AccountEditorEvent
    data class DueDayChanged(val day: Int) : AccountEditorEvent
    data object Save : AccountEditorEvent
}

sealed interface AccountEditorEffect {
    data class Saved(val id: AccountId) : AccountEditorEffect
}

@HiltViewModel(assistedFactory = AccountEditorViewModel.Factory::class)
class AccountEditorViewModel @AssistedInject constructor(
    @Assisted private val accountId: String?,
    private val accounts: AccountRepository,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
    private val ids: UuidV7,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(accountId: String?): AccountEditorViewModel
    }

    private val _state = MutableStateFlow(AccountEditorUiState())
    val state: StateFlow<AccountEditorUiState> = _state.asStateFlow()

    private val _effects = Channel<AccountEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var existing: Account? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val prefs = preferences.preferences.first()
        val account = accountId?.let { accounts.getAccount(AccountId(it)) }
        existing = account
        _state.value = if (account == null) {
            AccountEditorUiState(currencyCode = prefs.currencyCode, openingDate = clock.today().withDayOfMonth(1), loaded = true)
        } else {
            AccountEditorUiState(
                isNew = false,
                name = account.name,
                type = account.type,
                institutionId = account.institutionId,
                last4 = account.last4.orEmpty(),
                currencyCode = account.currency.code,
                openingBalance = account.openingBalance.major.toPlainString(),
                openingDate = account.openingDate,
                colorSeed = account.colorSeed,
                icon = account.icon,
                statementDay = account.statementDay ?: DEFAULT_STATEMENT_DAY,
                dueDay = account.dueDay ?: DEFAULT_DUE_DAY,
                loaded = true,
            )
        }
    }

    fun onEvent(event: AccountEditorEvent) {
        when (event) {
            is AccountEditorEvent.NameChanged -> _state.update { it.copy(name = event.value.take(MAX_NAME), nameError = false) }
            is AccountEditorEvent.TypeChanged -> _state.update { s ->
                val keepInstitution = s.institutionId?.let { id -> Institutions.byId(id)?.kinds?.contains(event.type) == true } ?: false
                // An icon the user never touched follows the type; a chosen one stays.
                val icon = if (s.icon == s.type.defaultIcon) event.type.defaultIcon else s.icon
                s.copy(type = event.type, institutionId = if (keepInstitution) s.institutionId else null, icon = icon)
            }
            is AccountEditorEvent.InstitutionChanged -> _state.update { it.copy(institutionId = event.id) }
            is AccountEditorEvent.Last4Changed ->
                _state.update { it.copy(last4 = event.value.filter(Char::isDigit).take(LAST4), last4Error = false) }
            is AccountEditorEvent.CurrencyChanged -> _state.update { it.copy(currencyCode = event.code) }
            is AccountEditorEvent.OpeningBalanceChanged ->
                _state.update { it.copy(openingBalance = event.value.take(MAX_AMOUNT), balanceError = false) }
            is AccountEditorEvent.OpeningDateChanged -> _state.update { it.copy(openingDate = event.date) }
            is AccountEditorEvent.ColorChanged -> _state.update { it.copy(colorSeed = event.seed) }
            is AccountEditorEvent.IconChanged -> _state.update { it.copy(icon = event.icon) }
            is AccountEditorEvent.StatementDayChanged -> _state.update { it.copy(statementDay = event.day.coerceIn(1, MAX_DAY)) }
            is AccountEditorEvent.DueDayChanged -> _state.update { it.copy(dueDay = event.day.coerceIn(1, MAX_DAY)) }
            AccountEditorEvent.Save -> save()
        }
    }

    private fun save() {
        val s = _state.value
        if (s.saving) return
        val nameOk = s.name.isNotBlank()
        val last4Ok = s.last4.isEmpty() || s.last4.length == LAST4
        val balance = parseMajor(s.openingBalance)
        if (!nameOk || !last4Ok || balance == null) {
            _state.update { it.copy(nameError = !nameOk, last4Error = !last4Ok, balanceError = balance == null) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val currency = Currency.of(s.currencyCode)
            val id = existing?.id ?: AccountId(ids.nextString())
            // New accounts go to the end of the list; edits keep their slot.
            val sortOrder = existing?.sortOrder
                ?: accounts.observeAccounts(includeArchived = true).first().maxOfOrNull { it.sortOrder + 1 }
                ?: 0
            val account = Account(
                id = id,
                name = s.name.trim(),
                type = s.type,
                currency = currency,
                openingBalance = Money.ofMajor(balance, currency),
                openingDate = s.openingDate,
                institutionId = s.institutionId,
                last4 = s.last4.ifEmpty { null },
                colorSeed = s.colorSeed,
                icon = s.icon,
                statementDay = if (s.isCard) s.statementDay else null,
                dueDay = if (s.isCard) s.dueDay else null,
                isArchived = existing?.isArchived ?: false,
                sortOrder = sortOrder,
                meta = existing?.meta ?: SyncMeta.unstamped(),
            )
            accounts.save(account)
            _effects.send(AccountEditorEffect.Saved(id))
        }
    }

    private fun parseMajor(text: String): BigDecimal? =
        text.trim().replace(",", "").takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it) }.getOrNull() }

    private companion object {
        const val MAX_NAME = 40
        const val LAST4 = 4
        const val MAX_AMOUNT = 16
        const val MAX_DAY = 28
        const val DEFAULT_STATEMENT_DAY = 18
        const val DEFAULT_DUE_DAY = 6
    }
}
