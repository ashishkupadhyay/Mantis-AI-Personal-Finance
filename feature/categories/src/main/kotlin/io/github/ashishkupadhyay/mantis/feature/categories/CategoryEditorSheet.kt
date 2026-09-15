package io.github.ashishkupadhyay.mantis.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.component.CategoryAvatar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisToggleGroup
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import io.github.ashishkupadhyay.mantis.core.ui.icons.MantisIcons
import kotlinx.collections.immutable.persistentListOf

/** Category form in a bottom sheet (FR-CAT-1): name, kind, group, icon and colour. */
@Composable
fun CategoryEditorSheet(
    state: CategoryEditorUiState,
    onEvent: (CategoryEditorEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val parent = state.groups.firstOrNull { it.id == state.parentId }
            CategoryAvatar(
                groupKey = parent?.key?.let(DefaultTaxonomy::groupKeyOf) ?: "system",
                colorKey = parent?.key ?: parent?.id?.value ?: state.name,
                icon = MantisIcons.forName(state.icon),
                fallbackLetter = state.name.take(1).ifEmpty { "?" },
                size = 56.dp,
                paletteIndex = state.paletteIndex,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(CategoryEditorEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.editor_name)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.editor_name_error)) }) else null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.isNew || !state.isSystem) {
            SectionTitle(stringResource(R.string.editor_kind))
            MantisToggleGroup(
                options = persistentListOf(stringResource(R.string.categories_expense), stringResource(R.string.categories_income)),
                selectedIndex = if (state.kind == CategoryKind.INCOME) 1 else 0,
                onSelected = { onEvent(CategoryEditorEvent.KindChanged(if (it == 1) CategoryKind.INCOME else CategoryKind.EXPENSE)) },
                enabled = state.isNew,
            )
        }
        if (!state.isSystem) {
            SectionTitle(stringResource(R.string.editor_group))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.isGroup,
                    onClick = { onEvent(CategoryEditorEvent.ParentChanged(null)) },
                    label = { Text(stringResource(R.string.editor_is_group)) },
                )
                state.groupsOfKind.forEach { group ->
                    FilterChip(
                        selected = state.parentId == group.id,
                        onClick = { onEvent(CategoryEditorEvent.ParentChanged(group.id)) },
                        label = { Text(group.name) },
                    )
                }
            }
        }
        SectionTitle(stringResource(R.string.editor_icon))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MantisIcons.names.forEach { name ->
                val vector = MantisIcons.forName(name) ?: return@forEach
                FilterChip(
                    selected = state.icon == name,
                    onClick = { onEvent(CategoryEditorEvent.IconChanged(name)) },
                    label = { Icon(vector, contentDescription = name) },
                )
            }
        }
        if (state.isGroup) {
            SectionTitle(stringResource(R.string.editor_colour))
            val palette = LocalMantisColors.current.categoryPalette
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.paletteIndex == null,
                    onClick = { onEvent(CategoryEditorEvent.ColorChanged(null)) },
                    label = { Text(stringResource(R.string.editor_colour_auto)) },
                )
                palette.forEachIndexed { index, color ->
                    FilterChip(
                        selected = state.paletteIndex == index,
                        onClick = { onEvent(CategoryEditorEvent.ColorChanged(index)) },
                        label = { Box(Modifier.size(20.dp).background(color, CircleShape)) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { onEvent(CategoryEditorEvent.Save) },
                enabled = state.loaded && !state.saving,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
