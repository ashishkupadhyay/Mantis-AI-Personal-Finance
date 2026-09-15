package io.github.ashishkupadhyay.mantis.feature.transactions.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.CategoryAvatar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.ErrorState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisMediumTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.SourceBadge
import io.github.ashishkupadhyay.mantis.core.designsystem.component.rememberExitUntilCollapsedScrollBehavior
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.format.Dates
import io.github.ashishkupadhyay.mantis.core.ui.icons.MantisIcons
import io.github.ashishkupadhyay.mantis.core.ui.money.LocalMoneyFormatter
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyText
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyTone
import io.github.ashishkupadhyay.mantis.core.ui.transaction.badgeKind
import io.github.ashishkupadhyay.mantis.core.ui.transaction.displayTitle
import io.github.ashishkupadhyay.mantis.feature.transactions.R
import io.github.ashishkupadhyay.mantis.core.ui.category.CategoryPickerSheet
import io.github.ashishkupadhyay.mantis.core.ui.category.kindsFor
import io.github.ashishkupadhyay.mantis.feature.transactions.list.labelRes
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Transaction detail (doc 05 §4.3): the amount as the title, then Category (with provenance and the one-tap
 * correction — FR-TXN-12), Account, Date & time, Merchant with raw narration, Notes, Tags, Split and Transfer
 * sections. Edit opens the sheet; delete lives in the overflow with undo.
 */
@Composable
fun TransactionDetailScreen(
    state: TransactionDetailUiState,
    today: LocalDate,
    onEvent: (TransactionDetailEvent) -> Unit,
    onEdit: () -> Unit,
    onOpenTransaction: (TransactionId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onCreateRule: (RuleOffer) -> Unit = {},
) {
    val transaction = state.transaction
    val scrollBehavior = rememberExitUntilCollapsedScrollBehavior()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    val formatter = LocalMoneyFormatter.current
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MantisMediumTopAppBar(
                title = {
                    transaction?.let {
                        MoneyText(
                            it.amount,
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            tone = if (it.type == TransactionType.TRANSFER) MoneyTone.NEUTRAL else MoneyTone.SIGNED,
                        )
                    }
                },
                subtitle = transaction?.let { { Text(it.displayTitle(), maxLines = 1) } },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                actions = {
                    if (transaction != null) {
                        DetailActions(
                            excluded = transaction.isExcluded,
                            onEdit = onEdit,
                            onToggleExcluded = { onEvent(TransactionDetailEvent.SetExcluded(!transaction.isExcluded)) },
                            onDelete = { confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.missing -> ErrorState(
                title = stringResource(R.string.detail_missing),
                body = "",
                modifier = Modifier.padding(innerPadding),
                onRetry = onBack,
                retryLabel = stringResource(R.string.action_back),
            )
            transaction == null -> Unit
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                CategorySection(state, onPick = { pickingCategory = true })
                state.ruleOffer?.let { offer ->
                    RuleOfferRow(
                        offer,
                        onCreate = { onCreateRule(offer) },
                        onDismiss = { onEvent(TransactionDetailEvent.DismissRuleOffer) },
                    )
                }
                HorizontalDivider()
                Facts(state, today, onOpenTransaction)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onEvent(TransactionDetailEvent.Delete) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (pickingCategory && transaction != null) {
        CategoryPickerSheet(
            categories = state.categories,
            kinds = kindsFor(transaction.type),
            selected = transaction.categoryId,
            onPick = { onEvent(TransactionDetailEvent.SetCategory(it)); pickingCategory = false },
            onDismiss = { pickingCategory = false },
        )
    }
}

/** "Always categorize X as Y?" — one tap to the rule editor, pre-filled (FR-CAT-6). */
@Composable
private fun RuleOfferRow(offer: RuleOffer, onCreate: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.detail_rule_offer, offer.pattern, offer.categoryName),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_rule_offer_no)) }
        TextButton(onClick = onCreate) { Text(stringResource(R.string.detail_rule_offer_yes)) }
    }
}

@Composable
private fun DetailActions(excluded: Boolean, onEdit: () -> Unit, onToggleExcluded: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit)) }
    IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(if (excluded) R.string.action_include else R.string.action_exclude)) },
            onClick = {
                menuOpen = false
                onToggleExcluded()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            onClick = {
                menuOpen = false
                onDelete()
            },
        )
    }
}

/** Account, date, merchant, notes, tags, splits, transfer link and the excluded note. */
@Composable
private fun Facts(state: TransactionDetailUiState, today: LocalDate, onOpenTransaction: (TransactionId) -> Unit) {
    val transaction = state.transaction ?: return
    val formatter = LocalMoneyFormatter.current
    Column {
        ListItem(
            headlineContent = { Text(state.account?.name ?: stringResource(R.string.detail_unknown_account)) },
            overlineContent = { Text(stringResource(R.string.detail_account)) },
            supportingContent = { Text(stringResource(transaction.type.labelRes())) },
        )
        ListItem(
            headlineContent = { Text(Dates.short(transaction.postedLocalDate, today)) },
            overlineContent = { Text(stringResource(R.string.detail_date)) },
            supportingContent = {
                val time = transaction.postedAt.atZone(ZoneId.systemDefault()).format(TIME)
                Text(time)
            },
        )
        ListItem(
            headlineContent = { Text(transaction.displayTitle()) },
            overlineContent = { Text(stringResource(R.string.detail_merchant)) },
            supportingContent = transaction.merchantNormalized?.let { { Text(transaction.descriptionRaw) } },
        )
        transaction.notes?.let { notes ->
            ListItem(headlineContent = { Text(notes) }, overlineContent = { Text(stringResource(R.string.detail_notes)) })
        }
        if (state.tags.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.detail_tags), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tags.forEach { tag -> InputChip(selected = false, onClick = {}, label = { Text("#${tag.name}") }) }
                }
            }
        }
        if (transaction.splits.isNotEmpty()) {
            Text(
                stringResource(R.string.detail_split),
                Modifier.padding(start = 16.dp, top = 12.dp),
                style = MaterialTheme.typography.labelMedium,
            )
            transaction.splits.forEachIndexed { index, split ->
                ListItem(
                    headlineContent = { Text(state.splitCategories.getOrNull(index)?.name ?: split.categoryId.value) },
                    supportingContent = split.note?.let { { Text(it) } },
                    trailingContent = { Text(formatter.format(split.amount)) },
                )
            }
        }
        transaction.transferPairId?.let { pairId ->
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (transaction.amount.minor < 0) R.string.detail_transfer_to else R.string.detail_transfer_from,
                            state.pairAccount?.name ?: stringResource(R.string.detail_unknown_account),
                        ),
                    )
                },
                overlineContent = { Text(stringResource(R.string.type_transfer)) },
                modifier = Modifier.fillMaxWidth(),
                trailingContent = {
                    TextButton(onClick = { onOpenTransaction(pairId) }) { Text(stringResource(R.string.detail_open_pair)) }
                },
            )
        }
        if (transaction.isExcluded) {
            Text(
                stringResource(R.string.detail_excluded_note),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategorySection(state: TransactionDetailUiState, onPick: () -> Unit) {
    val transaction = state.transaction ?: return
    val category = state.category
    val group = state.categoryGroup ?: category
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryAvatar(
                groupKey = group?.key?.let(DefaultTaxonomy::groupKeyOf) ?: "system",
                colorKey = group?.key ?: category?.id?.value ?: "system",
                icon = MantisIcons.forName(category?.icon),
                fallbackLetter = category?.name?.take(1) ?: "?",
                size = 48.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(category?.name ?: stringResource(R.string.picker_uncategorized), style = MaterialTheme.typography.titleMedium)
                state.categoryGroup?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val percent = transaction.categoryConfidence?.let { c -> (c * PERCENT).toInt() }
                    transaction.badgeKind()?.let { SourceBadge(it, confidencePercent = percent) }
                    Text(
                        provenanceLabel(transaction.categorySource, transaction.categoryConfidence, category != null),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AssistChip(
            onClick = onPick,
            label = {
                Text(stringResource(if (category == null) R.string.detail_choose_category else R.string.detail_wrong_category))
            },
        )
    }
}

@Composable
private fun provenanceLabel(source: CategorySource, confidence: Float?, categorized: Boolean): String = when {
    !categorized -> stringResource(R.string.provenance_none)
    source == CategorySource.USER -> stringResource(R.string.provenance_user)
    source == CategorySource.USER_RULE -> stringResource(R.string.provenance_rule)
    source == CategorySource.LLM -> stringResource(R.string.provenance_ai)
    source == CategorySource.IMPORT -> stringResource(R.string.provenance_import)
    confidence != null -> stringResource(R.string.provenance_model, (confidence * PERCENT).toInt())
    else -> stringResource(R.string.provenance_model_plain)
}

private const val PERCENT = 100f
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
