package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.collectAsLazyPagingItems
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.ui.navigation.AccountDetail
import io.github.ashishkupadhyay.mantis.core.ui.navigation.AccountEditor
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Accounts
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.core.ui.navigation.MantisNavigator
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionDetail
import java.time.LocalDate

object AccountsEntries : EntryProviderInstaller {

    override fun EntryProviderScope<NavKey>.install(navigator: MantisNavigator) {
        entry<Accounts> {
            AccountsRoute(
                onOpen = { navigator.navigate(AccountDetail(it.value)) },
                onAdd = { navigator.navigate(AccountEditor()) },
                onBack = navigator::goBack,
            )
        }
        entry<AccountDetail> { key ->
            AccountDetailRoute(
                accountId = key.id,
                onEdit = { navigator.navigate(AccountEditor(key.id)) },
                onOpenTransaction = { navigator.navigate(TransactionDetail(it.value)) },
                onBack = navigator::goBack,
            )
        }
        entry<AccountEditor> { key -> AccountEditorRoute(accountId = key.id, onDone = navigator::goBack) }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AccountsEntriesModule {
    @Provides
    @IntoSet
    fun accountsEntries(): EntryProviderInstaller = AccountsEntries
}

@Composable
private fun AccountsRoute(
    onOpen: (AccountId) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountsScreen(state = state, onOpen = onOpen, onAdd = onAdd, onBack = onBack)
}

@Composable
private fun AccountDetailRoute(
    accountId: String,
    onEdit: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<AccountDetailViewModel, AccountDetailViewModel.Factory>(creationCallback = { it.create(accountId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AccountDetailEffect.Deleted -> onBack()
                is AccountDetailEffect.Message -> snackbarHostState.showSnackbar(effect.text)
            }
        }
    }
    AccountDetailScreen(
        state = state,
        rows = rows,
        snackbarHostState = snackbarHostState,
        today = LocalDate.now(),
        onEvent = viewModel::onEvent,
        onEdit = onEdit,
        onOpenTransaction = onOpenTransaction,
        onBack = onBack,
    )
}

@Composable
private fun AccountEditorRoute(accountId: String?, onDone: () -> Unit) {
    val viewModel = hiltViewModel<AccountEditorViewModel, AccountEditorViewModel.Factory>(creationCallback = { it.create(accountId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AccountEditorEffect.Saved -> onDone()
            }
        }
    }
    AccountEditorScreen(state = state, today = LocalDate.now(), onEvent = viewModel::onEvent, onBack = onDone)
}
