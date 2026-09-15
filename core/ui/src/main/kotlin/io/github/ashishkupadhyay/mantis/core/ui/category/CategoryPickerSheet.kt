package io.github.ashishkupadhyay.mantis.core.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.CategoryAvatar
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.icons.MantisIcons
import io.github.ashishkupadhyay.mantis.core.ui.R

/**
 * Category chooser shared by the add sheet, the detail screen, swipe-to-recategorize and bulk actions (doc 05 §4.3
 * "Browse all"): groups with their leaves as chips, a filter field, and "Uncategorized" to clear. [kinds] limits
 * what is offered — an expense never gets an income category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    kinds: Set<CategoryKind>,
    selected: CategoryId?,
    onPick: (CategoryId?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(categories, kinds) {
        categories.filter { !it.isHidden && it.kind in kinds && !it.meta.isDeleted && it.key != DefaultTaxonomy.KEY_UNCATEGORIZED }
    }
    val groups = remember(visible, query) {
        val leaves = visible.filter { !it.isGroup && it.name.contains(query.trim(), ignoreCase = true) }.groupBy { it.parentId }
        visible.filter { it.isGroup }.sortedBy { it.sortOrder }.mapNotNull { group ->
            leaves[group.id]?.sortedBy { it.sortOrder }?.let { group to it }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.picker_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            if (allowNone && query.isBlank()) {
                item("none") {
                    FilterChip(
                        selected = selected == null,
                        onClick = { onPick(null) },
                        label = { Text(stringResource(R.string.picker_uncategorized)) },
                    )
                }
            }
            items(groups, key = { it.first.id.value }) { (group, leaves) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryAvatar(
                            groupKey = group.key?.let(DefaultTaxonomy::groupKeyOf) ?: "system",
                            colorKey = group.key ?: group.id.value,
                            icon = MantisIcons.forName(group.icon),
                            fallbackLetter = group.name.take(1),
                            size = 28.dp,
                        )
                        Text(group.name, style = MaterialTheme.typography.titleSmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        leaves.forEach { leaf ->
                            FilterChip(
                                selected = leaf.id == selected,
                                onClick = { onPick(leaf.id) },
                                label = { Text(leaf.name) },
                                leadingIcon = MantisIcons.forName(leaf.icon)?.let { icon ->
                                    { Icon(icon, contentDescription = null, modifier = Modifier.height(18.dp)) }
                                },
                            )
                        }
                    }
                }
            }
            if (groups.isEmpty()) {
                item("empty") {
                    Text(
                        stringResource(R.string.picker_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item("spacer") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** Category kinds a transaction of this type may carry. */
fun kindsFor(type: TransactionType): Set<CategoryKind> = when (type) {
    TransactionType.EXPENSE -> setOf(CategoryKind.EXPENSE)
    TransactionType.INCOME -> setOf(CategoryKind.INCOME)
    TransactionType.TRANSFER -> setOf(CategoryKind.TRANSFER, CategoryKind.SYSTEM)
}
