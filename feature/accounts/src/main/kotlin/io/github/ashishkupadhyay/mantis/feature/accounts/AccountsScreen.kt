package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.designsystem.component.EmptyState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisCarousel
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLargeTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.revealFraction
import io.github.ashishkupadhyay.mantis.core.designsystem.component.rememberExitUntilCollapsedScrollBehavior
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.PreviewMantis
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountBalance
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountTotals
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.DeviceId
import io.github.ashishkupadhyay.mantis.core.model.Institutions
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.ui.icons.MantisIcons
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyText
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyTone
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate

/** Accounts overview (doc 05 §4.11): net position, carousel of cards, list incl. archived; FAB adds one. */
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onOpen: (AccountId) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val scrollBehavior = rememberExitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MantisLargeTopAppBar(
                title = stringResource(R.string.accounts_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { if (onBack != null) BackButton(onBack) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.accounts_add)) }
        },
    ) { innerPadding ->
        if (state.loaded && state.active.isEmpty() && state.archived.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                EmptyState(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = stringResource(R.string.accounts_empty_title),
                    body = stringResource(R.string.accounts_empty_body),
                    primaryAction = stringResource(R.string.accounts_add) to onAdd,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + FAB_CLEARANCE,
            ),
        ) {
            state.totals?.let { totals -> item("totals") { TotalsCard(totals, Modifier.padding(horizontal = 16.dp)) } }
            if (state.active.isNotEmpty()) {
                item("carousel") {
                    MantisCarousel(itemCount = state.active.size, modifier = Modifier.padding(vertical = 16.dp)) { index ->
                        val balance = state.active[index]
                        AccountCard(
                            balance,
                            onClick = { onOpen(balance.account.id) },
                            modifier = Modifier.maskClip(CardDefaults.shape),
                            reveal = { revealFraction() },
                        )
                    }
                }
            }
            items(state.active, key = { it.account.id.value }) { balance -> AccountRow(balance, onClick = { onOpen(balance.account.id) }) }
            if (state.archived.isNotEmpty()) {
                item("archived-title") { SectionTitle(stringResource(R.string.accounts_archived)) }
                items(state.archived, key = { it.account.id.value }) { balance ->
                    AccountRow(balance, onClick = { onOpen(balance.account.id) })
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(totals: AccountTotals, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.accounts_net),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyText(totals.net, style = MaterialTheme.typography.headlineMediumEmphasized, showMinor = false)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(stringResource(R.string.accounts_assets), style = MaterialTheme.typography.labelMedium)
                    MoneyText(totals.assets, style = MaterialTheme.typography.titleMedium, showMinor = false)
                }
                Column {
                    Text(stringResource(R.string.accounts_liabilities), style = MaterialTheme.typography.labelMedium)
                    MoneyText(totals.liabilities, style = MaterialTheme.typography.titleMedium, showMinor = false)
                }
            }
        }
    }
}

/** Institution-coloured card: name, masked number, balance (owed for liabilities). [reveal] fades text while peeking. */
@Composable
fun AccountCard(balance: AccountBalance, onClick: () -> Unit, modifier: Modifier = Modifier, reveal: () -> Float = { 1f }) {
    val account = balance.account
    val brand = brandColor(account)
    Card(
        modifier = modifier.fillMaxSize().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = brand, contentColor = Color.White),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MantisIcons.forName(account.icon) ?: Icons.Default.AccountBalanceWallet, contentDescription = null)
                Text(
                    account.name,
                    Modifier.graphicsLayer { alpha = reveal() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            }
            Column(Modifier.graphicsLayer { alpha = reveal() }) {
                account.last4?.let {
                    Text(stringResource(R.string.accounts_masked_number, it), style = MaterialTheme.typography.labelMedium)
                }
                val owed = balance.owed
                if (owed != null) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MoneyText(owed, style = MaterialTheme.typography.headlineSmallEmphasized, color = Color.White, showMinor = false)
                        Text(stringResource(R.string.accounts_owed), style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    MoneyText(
                        balance.balance,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = Color.White,
                        showMinor = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(balance: AccountBalance, onClick: () -> Unit) {
    val account = balance.account
    val brand = brandColor(account)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).background(brand, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
            Icon(MantisIcons.forName(account.icon) ?: Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(account.type.label(), account.last4?.let { stringResource(R.string.accounts_masked_number, it) })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoneyText(
            balance.owed ?: balance.balance,
            style = MaterialTheme.typography.titleMedium,
            tone = MoneyTone.NEUTRAL,
            showMinor = false,
        )
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

@Composable
internal fun AccountType.label(): String = stringResource(
    when (this) {
        AccountType.BANK -> R.string.account_type_bank
        AccountType.CREDIT_CARD -> R.string.account_type_credit_card
        AccountType.CASH -> R.string.account_type_cash
        AccountType.WALLET -> R.string.account_type_wallet
        AccountType.INVESTMENT -> R.string.account_type_investment
        AccountType.LOAN -> R.string.account_type_loan
    },
)

@Composable
private fun brandColor(account: Account): Color =
    Institutions.byId(account.institutionId)?.brandColor?.let { Color(it) } ?: LocalMantisColors.current.forKey(account.id.value)

private val FAB_CLEARANCE = 88.dp

@Suppress("MagicNumber")
internal fun previewAccounts(): AccountsUiState {
    val meta = SyncMeta.local(1L, DeviceId("p"))
    val bank = Account(
        AccountId("b"), "HDFC Savings", AccountType.BANK, Currency.INR, Money.ofMajor(1000, Currency.INR), LocalDate.of(2026, 1, 1),
        institutionId = "hdfc", last4 = "4412", meta = meta,
    )
    val card = Account(
        AccountId("c"), "ICICI Card", AccountType.CREDIT_CARD, Currency.INR, Money.zero(Currency.INR), LocalDate.of(2026, 1, 1),
        institutionId = "icici", last4 = "8831", icon = "credit_card", statementDay = 18, dueDay = 6, meta = meta,
    )
    val balances = listOf(
        AccountBalance(bank, Money.ofMajor(84_250, Currency.INR)),
        AccountBalance(card, Money.ofMajor(-21_400, Currency.INR)),
    )
    return AccountsUiState(
        active = balances.toImmutableList(),
        totals = AccountTotals.of(balances, Currency.INR),
        loaded = true,
    )
}

@PreviewMantis
@Composable
private fun AccountsPreview() = MantisPreviewTheme { AccountsScreen(previewAccounts(), onOpen = {}, onAdd = {}) }

@PreviewMantis
@Composable
private fun AccountsEmptyPreview() = MantisPreviewTheme { AccountsScreen(AccountsUiState(loaded = true), onOpen = {}, onAdd = {}) }
