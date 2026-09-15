package io.github.ashishkupadhyay.mantis.feature.transactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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
import io.github.ashishkupadhyay.mantis.core.designsystem.component.EmptyState
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.format.Dates
import io.github.ashishkupadhyay.mantis.core.ui.money.LocalMoneyFormatter
import io.github.ashishkupadhyay.mantis.core.ui.navigation.AddTransaction
import io.github.ashishkupadhyay.mantis.core.ui.navigation.BottomSheetSceneStrategy
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.core.ui.navigation.ImportWizard
import io.github.ashishkupadhyay.mantis.core.ui.navigation.MantisNavigator
import io.github.ashishkupadhyay.mantis.core.ui.navigation.RuleEditor
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionDetail
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionPrefill
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Transactions
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionsFiltered
import io.github.ashishkupadhyay.mantis.feature.transactions.add.AddTransactionEffect
import io.github.ashishkupadhyay.mantis.feature.transactions.add.AddTransactionEvent
import io.github.ashishkupadhyay.mantis.feature.transactions.add.AddTransactionSheet
import io.github.ashishkupadhyay.mantis.feature.transactions.add.AddTransactionViewModel
import io.github.ashishkupadhyay.mantis.feature.transactions.detail.RuleOffer
import io.github.ashishkupadhyay.mantis.feature.transactions.detail.TransactionDetailEffect
import io.github.ashishkupadhyay.mantis.feature.transactions.detail.TransactionDetailScreen
import io.github.ashishkupadhyay.mantis.feature.transactions.detail.TransactionDetailViewModel
import io.github.ashishkupadhyay.mantis.feature.transactions.list.TransactionsScreen
import io.github.ashishkupadhyay.mantis.feature.transactions.list.TransactionsViewModel
import java.time.LocalDate

/** Transactions tab, filtered lists from links, the detail pane and the add/edit bottom sheet (doc 02 §4.2). */
object TransactionsEntries : EntryProviderInstaller {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun EntryProviderScope<NavKey>.install(navigator: MantisNavigator) {
        entry<Transactions>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() })) {
            TransactionsRoute(initialFilter = null, navigator = navigator)
        }
        entry<TransactionsFiltered>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() })) { key ->
            TransactionsRoute(initialFilter = key.filter, navigator = navigator, onBack = navigator::goBack)
        }
        entry<TransactionDetail>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
            TransactionDetailRoute(
                transactionId = key.id,
                onEdit = { navigator.navigate(AddTransaction(editId = key.id)) },
                onOpenTransaction = { navigator.navigate(TransactionDetail(it.value)) },
                onCreateRule = { offer ->
                    navigator.navigate(RuleEditor(pattern = offer.pattern, categoryId = offer.categoryId.value, fromTransactionId = key.id))
                },
                onBack = navigator::goBack,
            )
        }
        entry<AddTransaction>(metadata = BottomSheetSceneStrategy.bottomSheet()) { key ->
            AddTransactionRoute(editId = key.editId, prefill = key.prefill, onDone = navigator::goBack)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object TransactionsEntriesModule {
    @Provides
    @IntoSet
    fun transactionsEntries(): EntryProviderInstaller = TransactionsEntries
}

@Composable
private fun DetailPlaceholder() {
    EmptyState(
        icon = Icons.Default.ReceiptLong,
        title = stringResource(R.string.detail_placeholder_title),
        body = stringResource(R.string.detail_placeholder_body),
    )
}

@Composable
private fun TransactionsRoute(initialFilter: String?, navigator: MantisNavigator, onBack: (() -> Unit)? = null) {
    val viewModel = hiltViewModel<TransactionsViewModel, TransactionsViewModel.Factory>(
        key = initialFilter,
        creationCallback = { it.create(initialFilter) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    TransactionsScreen(
        state = state,
        rows = rows,
        today = LocalDate.now(),
        onEvent = viewModel::onEvent,
        onOpen = { navigator.navigate(TransactionDetail(it.value)) },
        onAdd = { type -> navigator.navigate(AddTransaction(prefill = TransactionPrefill(type = type.name))) },
        onImport = { navigator.navigate(ImportWizard()) },
        onBack = onBack,
    )
}

@Composable
private fun TransactionDetailRoute(
    transactionId: String,
    onEdit: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onCreateRule: (RuleOffer) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<TransactionDetailViewModel, TransactionDetailViewModel.Factory>(
        key = transactionId,
        creationCallback = { it.create(transactionId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TransactionDetailEffect.Deleted -> onBack()
            }
        }
    }
    TransactionDetailScreen(
        state = state,
        today = LocalDate.now(),
        onEvent = viewModel::onEvent,
        onEdit = onEdit,
        onOpenTransaction = onOpenTransaction,
        onBack = onBack,
        onCreateRule = onCreateRule,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionRoute(editId: String?, prefill: TransactionPrefill?, onDone: () -> Unit) {
    val viewModel = hiltViewModel<AddTransactionViewModel, AddTransactionViewModel.Factory>(
        creationCallback = { it.create(editId, prefill) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var duplicate by remember { mutableStateOf<AddTransactionEffect.PossibleDuplicate?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AddTransactionEffect.Saved -> if (!effect.addAnother) onDone()
                is AddTransactionEffect.PossibleDuplicate -> duplicate = effect
            }
        }
    }
    AddTransactionSheet(state = state, today = LocalDate.now(), onEvent = viewModel::onEvent, onClose = onDone)
    duplicate?.let { effect ->
        val formatter = LocalMoneyFormatter.current
        val first = effect.duplicates.first()
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text(stringResource(R.string.duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.duplicate_body,
                        first.descriptionRaw,
                        formatter.format(first.amount),
                        Dates.short(first.postedLocalDate, LocalDate.now()),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        duplicate = null
                        viewModel.onEvent(AddTransactionEvent.Save(addAnother = effect.addAnother, ignoreDuplicates = true))
                    },
                ) { Text(stringResource(R.string.duplicate_save_anyway)) }
            },
            dismissButton = { TextButton(onClick = { duplicate = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
