package io.github.ashishkupadhyay.mantis.core.ui.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.designsystem.component.CategoryAvatar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.SourceBadge
import io.github.ashishkupadhyay.mantis.core.designsystem.component.SourceBadgeKind
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.Transaction
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.icons.MantisIcons
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyText
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyTone
import java.time.LocalDate

/** Everything a list row shows, pre-resolved so the row itself does no lookups (doc 05 §4.2). */
@Immutable
data class TransactionRowUi(
    val id: TransactionId,
    val title: String,
    val subtitle: String,
    val amount: Money,
    val type: TransactionType,
    val date: LocalDate,
    val categoryGroupKey: String,
    val categoryColorKey: String,
    /** The user's chosen palette slot for the category's group, or null for the automatic colour. */
    val categoryPaletteIndex: Int? = null,
    val categoryIcon: String?,
    val categoryInitial: String,
    val badge: SourceBadgeKind?,
    val confidencePercent: Int?,
    val isExcluded: Boolean,
    val needsReview: Boolean,
)

/** Resolves category and account names for a row; [categories]/[accounts] are the screen's lookup maps. */
fun Transaction.toRowUi(categories: Map<CategoryId, Category>, accounts: Map<AccountId, Account>): TransactionRowUi {
    val category = categoryId?.let(categories::get)
    val group = category?.parentId?.let(categories::get) ?: category
    val account = accounts[accountId]
    val categoryName = category?.name ?: "Uncategorized"
    return TransactionRowUi(
        id = id,
        title = displayTitle(),
        subtitle = listOfNotNull(categoryName, account?.name).joinToString(" · "),
        amount = amount,
        type = type,
        date = postedLocalDate,
        categoryGroupKey = group?.key?.let(DefaultTaxonomy::groupKeyOf) ?: "system",
        categoryColorKey = group?.key ?: category?.id?.value ?: "system",
        categoryPaletteIndex = group?.paletteIndex,
        categoryIcon = category?.icon,
        categoryInitial = categoryName.take(1),
        badge = badgeKind(),
        confidencePercent = categoryConfidence?.let { (it * PERCENT).toInt() },
        isExcluded = isExcluded,
        needsReview = needsReview,
    )
}

/** The merchant when known (title-cased canonical name), otherwise the raw narration. */
fun Transaction.displayTitle(): String = merchantNormalized?.replaceFirstChar { it.titlecase() } ?: descriptionRaw

/** Provenance badge for a row (doc 05 §4.2): ? uncategorized, ✓ user/rule, ✦ AI, ~ suggested, ◐ model/dictionary, ⚡ import. */
fun Transaction.badgeKind(): SourceBadgeKind? = when {
    categoryId == null -> SourceBadgeKind.UNCATEGORIZED
    categorySource == CategorySource.USER || categorySource == CategorySource.USER_RULE -> SourceBadgeKind.USER
    categorySource == CategorySource.LLM -> SourceBadgeKind.AI
    needsReview -> SourceBadgeKind.SUGGESTED
    categorySource == CategorySource.ON_DEVICE_MODEL || categorySource == CategorySource.MERCHANT_DB -> SourceBadgeKind.MODEL_HIGH
    categorySource == CategorySource.IMPORT -> SourceBadgeKind.IMPORT
    else -> null
}

/**
 * One transaction in a list: avatar, title/subtitle, signed amount, provenance badge (doc 05 §4.2). Long-press starts
 * multi-select; while [selected] the avatar morphs into a check and the row is tinted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    row: TransactionRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    // Opaque on purpose: swipe-to-dismiss backgrounds sit behind the row and must not show through.
    val tint = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tint)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { if (onLongClick != null) this.selected = selected },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryAvatar(
            groupKey = row.categoryGroupKey,
            colorKey = row.categoryColorKey,
            icon = MantisIcons.forName(row.categoryIcon),
            fallbackLetter = row.categoryInitial,
            selected = selected,
            paletteIndex = row.categoryPaletteIndex,
        )
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (row.isExcluded) TextDecoration.LineThrough else null,
            )
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MoneyText(
            money = row.amount,
            style = MaterialTheme.typography.titleMedium,
            tone = if (row.type == TransactionType.TRANSFER) MoneyTone.NEUTRAL else MoneyTone.SIGNED,
            showMinor = false,
        )
        row.badge?.let { SourceBadge(it, confidencePercent = row.confidencePercent) }
    }
}

private const val PERCENT = 100f
