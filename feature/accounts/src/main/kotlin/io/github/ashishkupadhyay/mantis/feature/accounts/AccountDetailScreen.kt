package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import io.github.ashishkupadhyay.mantis.core.designsystem.component.ErrorState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLargeTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.rememberExitUntilCollapsedScrollBehavior
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import io.github.ashishkupadhyay.mantis.core.ui.format.Dates
import io.github.ashishkupadhyay.mantis.core.ui.money.LocalMoneyFormatter
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyText
import io.github.ashishkupadhyay.mantis.core.ui.transaction.TransactionRow
import io.github.ashishkupadhyay.mantis.core.ui.transaction.TransactionRowUi
import io.github.ashishkupadhyay.mantis.core.model.AccountBalance
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import java.time.LocalDate

/** Account detail (doc 05 §4.11): balance hero, card cycle facts, actions, and the account's transactions. */
@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    rows: LazyPagingItems<TransactionRowUi>,
    snackbarHostState: SnackbarHostState,
    today: LocalDate,
    onEvent: (AccountDetailEvent) -> Unit,
    onEdit: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = state.account
    var confirmDelete by remember { mutableStateOf(false) }
    val scrollBehavior = rememberExitUntilCollapsedScrollBehavior()
    val formatter = LocalMoneyFormatter.current
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MantisLargeTopAppBar(
                title = account?.name ?: "",
                subtitle = state.balance?.let { balance ->
                    val owed = account?.let { AccountBalance(it, balance).owed }
                    if (owed != null) {
                        stringResource(R.string.account_detail_owed, formatter.format(owed, showMinor = false))
                    } else {
                        formatter.format(balance, showMinor = false)
                    }
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (account != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.account_detail_edit))
                        }
                        IconButton(onClick = { onEvent(AccountDetailEvent.SetArchived(!account.isArchived)) }) {
                            if (account.isArchived) {
                                Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.account_detail_unarchive))
                            } else {
                                Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.account_detail_archive))
                            }
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.account_detail_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.missing -> ErrorState(
                title = stringResource(R.string.account_detail_missing),
                body = "",
                modifier = Modifier.padding(innerPadding),
                onRetry = onBack,
                retryLabel = stringResource(R.string.action_back),
            )
            account == null -> Unit
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item("facts") {
                    Card(Modifier.padding(16.dp).fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                account.type.label(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(
                                    R.string.account_detail_opening,
                                    formatter.format(account.openingBalance, showMinor = false),
                                    Dates.short(account.openingDate, today),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.card?.let { card -> CardCycleFacts(card) }
                        }
                    }
                }
                item("txn-title") { SectionTitle(stringResource(R.string.account_detail_transactions)) }
                if (rows.itemCount == 0) {
                    item("empty") {
                        Text(
                            stringResource(R.string.account_detail_no_transactions),
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(rows.itemCount, key = rows.itemKey { it.id.value }) { index ->
                    rows[index]?.let { row -> TransactionRow(row, onClick = { onOpenTransaction(row.id) }) }
                }
            }
        }
    }
    if (confirmDelete && account != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_detail_delete_title, account.name)) },
            text = { Text(stringResource(R.string.account_detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onEvent(AccountDetailEvent.Delete) }) {
                    Text(stringResource(R.string.account_detail_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun CardCycleFacts(card: CardCycleUi) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text(stringResource(R.string.account_detail_cycle_spend), style = MaterialTheme.typography.labelMedium)
            MoneyText(card.cycleSpend.abs(), style = MaterialTheme.typography.titleMedium, showMinor = false)
        }
        Column {
            val statementDays = card.daysUntilStatement.toInt()
            Text(
                pluralStringResource(R.plurals.account_detail_statement_in, statementDays, statementDays),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (card.daysUntilDue == 0L) {
                    stringResource(R.string.account_detail_due_today)
                } else {
                    pluralStringResource(R.plurals.account_detail_due_in, card.daysUntilDue.toInt(), card.daysUntilDue.toInt())
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
