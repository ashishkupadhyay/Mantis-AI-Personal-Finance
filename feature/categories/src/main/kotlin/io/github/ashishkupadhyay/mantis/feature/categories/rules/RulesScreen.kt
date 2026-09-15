package io.github.ashishkupadhyay.mantis.feature.categories.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.EmptyState
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLargeTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.rememberExitUntilCollapsedScrollBehavior
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.RuleField
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.github.ashishkupadhyay.mantis.core.ui.category.CategoryPickerSheet
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import io.github.ashishkupadhyay.mantis.feature.categories.MenuItem
import io.github.ashishkupadhyay.mantis.feature.categories.R

/** Rules list (FR-CAT-6): evaluated top to bottom; each row edits, applies to existing rows, moves or deletes. */
@Composable
fun RulesScreen(
    state: RulesUiState,
    onEvent: (RulesEvent) -> Unit,
    onEdit: (RuleId) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = rememberExitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MantisLargeTopAppBar(
                title = stringResource(R.string.rules_title),
                subtitle = stringResource(R.string.rules_subtitle),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.rules_add)) }
        },
    ) { innerPadding ->
        if (state.loaded && state.rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Rule,
                    title = stringResource(R.string.rules_empty_title),
                    body = stringResource(R.string.rules_empty_body),
                    primaryAction = stringResource(R.string.rules_add) to onAdd,
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
            items(state.rules, key = { it.rule.id.value }) { entry -> RuleRow(entry, onEvent, onEdit) }
        }
    }
}

@Composable
private fun RuleRow(entry: RuleUi, onEvent: (RulesEvent) -> Unit, onEdit: (RuleId) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val rule = entry.rule
    ListItem(
        headlineContent = {
            Text(
                rule.pattern,
                fontFamily = if (rule.matchType == RuleMatchType.REGEX) FontFamily.Monospace else null,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.rules_row_summary,
                    stringResource(rule.matchType.labelRes()),
                    stringResource(rule.field.labelRes()),
                    entry.category?.name ?: stringResource(R.string.rules_missing_category),
                ),
            )
        },
        trailingContent = {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.rules_actions_for, rule.pattern))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val close = { menuOpen = false }
                MenuItem(stringResource(R.string.action_edit), close) { onEdit(rule.id) }
                MenuItem(stringResource(R.string.rules_apply_existing), close) { onEvent(RulesEvent.ApplyToExisting(rule.id)) }
                MenuItem(stringResource(R.string.action_move_up), close) { onEvent(RulesEvent.Move(rule.id, up = true)) }
                MenuItem(stringResource(R.string.action_move_down), close) { onEvent(RulesEvent.Move(rule.id, up = false)) }
                MenuItem(stringResource(R.string.action_delete), close) { onEvent(RulesEvent.Delete(rule.id)) }
            }
        },
    )
}

/** Rule form in a bottom sheet (FR-CAT-6, NFR-20b): live validation and a preview of matching narrations. */
@Composable
fun RuleEditorSheet(
    state: RuleEditorUiState,
    onEvent: (RuleEditorEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val isRegex = state.matchType == RuleMatchType.REGEX
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (state.isNew) R.string.rule_new_title else R.string.rule_edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
        }
        SectionTitle(stringResource(R.string.rule_when))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleMatchType.entries.forEach { type ->
                FilterChip(
                    selected = state.matchType == type,
                    onClick = { onEvent(RuleEditorEvent.MatchTypeChanged(type)) },
                    label = { Text(stringResource(type.labelRes())) },
                )
            }
        }
        OutlinedTextField(
            value = state.pattern,
            onValueChange = { onEvent(RuleEditorEvent.PatternChanged(it)) },
            label = { Text(stringResource(if (isRegex) R.string.rule_pattern_regex else R.string.rule_pattern)) },
            isError = state.patternError != null,
            supportingText = {
                Text(
                    state.patternError ?: stringResource(if (isRegex) R.string.rule_pattern_hint_regex else R.string.rule_pattern_hint),
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.matchType != RuleMatchType.MERCHANT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleField.entries.forEach { field ->
                    FilterChip(
                        selected = state.field == field,
                        onClick = { onEvent(RuleEditorEvent.FieldChanged(field)) },
                        label = { Text(stringResource(field.labelRes())) },
                    )
                }
            }
        }
        if (state.preview.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.rule_preview), style = MaterialTheme.typography.labelMedium)
                state.preview.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SectionTitle(stringResource(R.string.rule_then))
        AssistChip(
            onClick = { picking = true },
            label = { Text(state.selectedCategory?.name ?: stringResource(R.string.rule_pick_category)) },
            colors = if (state.categoryError) {
                AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
            } else {
                AssistChipDefaults.assistChipColors()
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.applyToExisting, onCheckedChange = { onEvent(RuleEditorEvent.ApplyToExistingChanged(it)) })
            Text(stringResource(R.string.rule_apply_existing), style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { onEvent(RuleEditorEvent.Save) },
                enabled = state.loaded && !state.saving,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
    if (picking) {
        CategoryPickerSheet(
            categories = state.categories,
            kinds = setOf(CategoryKind.EXPENSE, CategoryKind.INCOME),
            selected = state.categoryId,
            onPick = { onEvent(RuleEditorEvent.CategoryChanged(it)); picking = false },
            onDismiss = { picking = false },
            allowNone = false,
        )
    }
}

internal fun RuleMatchType.labelRes(): Int = when (this) {
    RuleMatchType.CONTAINS -> R.string.match_contains
    RuleMatchType.STARTS_WITH -> R.string.match_starts_with
    RuleMatchType.REGEX -> R.string.match_regex
    RuleMatchType.MERCHANT -> R.string.match_merchant
}

internal fun RuleField.labelRes(): Int = when (this) {
    RuleField.DESCRIPTION -> R.string.field_description
    RuleField.MERCHANT -> R.string.field_merchant
}

private val FAB_CLEARANCE = 88.dp
