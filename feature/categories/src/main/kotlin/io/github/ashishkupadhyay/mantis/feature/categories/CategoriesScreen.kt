package io.github.ashishkupadhyay.mantis.feature.categories

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.CategoryAvatar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLargeTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.rememberExitUntilCollapsedScrollBehavior
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import io.github.ashishkupadhyay.mantis.core.ui.icons.MantisIcons

/**
 * Manage categories (FR-CAT-1, doc 05 §4.11 Settings → Categories & rules): expense and income groups with their
 * leaves; every row can be edited, hidden, moved; user rows can be deleted. Rules open from the app bar.
 */
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onEvent: (CategoriesEvent) -> Unit,
    onEdit: (CategoryId) -> Unit,
    onAdd: (parentId: CategoryId?) -> Unit,
    onOpenRules: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = rememberExitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MantisLargeTopAppBar(
                title = stringResource(R.string.categories_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenRules) {
                        Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = stringResource(R.string.rules_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.categories_add_group))
            }
        },
    ) { innerPadding ->
        val expenseTitle = stringResource(R.string.categories_expense)
        val incomeTitle = stringResource(R.string.categories_income)
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + FAB_CLEARANCE,
            ),
        ) {
            section(expenseTitle, state.expense, onEvent, onEdit, onAdd)
            section(incomeTitle, state.income, onEvent, onEdit, onAdd)
        }
    }
}

private fun LazyListScope.section(
    title: String,
    groups: List<CategoryGroupUi>,
    onEvent: (CategoriesEvent) -> Unit,
    onEdit: (CategoryId) -> Unit,
    onAdd: (CategoryId?) -> Unit,
) {
    if (groups.isEmpty()) return
    item("title-$title") { SectionTitle(title) }
    groups.forEach { entry ->
        item("group-${entry.group.id.value}") {
            CategoryRow(entry.group, group = entry.group, onEvent = onEvent, onEdit = onEdit, onAdd = onAdd, isGroup = true)
        }
        items(entry.leaves, key = { "leaf-${it.id.value}" }) { leaf ->
            CategoryRow(leaf, group = entry.group, onEvent = onEvent, onEdit = onEdit, onAdd = onAdd, isGroup = false)
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    group: Category,
    onEvent: (CategoriesEvent) -> Unit,
    onEdit: (CategoryId) -> Unit,
    onAdd: (CategoryId?) -> Unit,
    isGroup: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.alpha(if (category.isHidden) HIDDEN_ALPHA else 1f).padding(start = if (isGroup) 0.dp else INDENT),
        leadingContent = {
            CategoryAvatar(
                groupKey = group.key?.let(DefaultTaxonomy::groupKeyOf) ?: "system",
                colorKey = group.key ?: group.id.value,
                icon = MantisIcons.forName(category.icon),
                fallbackLetter = category.name.take(1),
                size = if (isGroup) 40.dp else 32.dp,
                paletteIndex = group.paletteIndex,
            )
        },
        headlineContent = {
            Text(category.name, style = if (isGroup) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
        },
        supportingContent = when {
            category.isHidden -> ({ Text(stringResource(R.string.categories_hidden)) })
            isGroup -> ({ Text(stringResource(R.string.categories_group)) })
            else -> null
        },
        trailingContent = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.categories_actions_for, category.name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val close = { menuOpen = false }
                MenuItem(stringResource(R.string.action_edit), close) { onEdit(category.id) }
                if (isGroup) MenuItem(stringResource(R.string.categories_add_leaf), close) { onAdd(category.id) }
                MenuItem(stringResource(if (category.isHidden) R.string.action_unhide else R.string.action_hide), close) {
                    onEvent(CategoriesEvent.SetHidden(category.id, !category.isHidden))
                }
                MenuItem(stringResource(R.string.action_move_up), close) { onEvent(CategoriesEvent.Move(category.id, up = true)) }
                MenuItem(stringResource(R.string.action_move_down), close) { onEvent(CategoriesEvent.Move(category.id, up = false)) }
                if (!category.isSystem) {
                    MenuItem(stringResource(R.string.action_delete), close) { onEvent(CategoriesEvent.Delete(category.id)) }
                }
            }
        },
    )
}

/** A menu row that closes the menu before running its action. */
@Composable
internal fun MenuItem(label: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            close()
            action()
        },
    )
}

private val FAB_CLEARANCE = 88.dp
private val INDENT = 24.dp
private const val HIDDEN_ALPHA = 0.5f
