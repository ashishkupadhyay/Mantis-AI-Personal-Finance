package io.github.ashishkupadhyay.mantis.feature.importwizard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisLoadingIndicator
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisTopAppBar
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisWavyProgress
import io.github.ashishkupadhyay.mantis.core.designsystem.component.MantisWavyProgressIndeterminate
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnRole
import io.github.ashishkupadhyay.mantis.core.domain.imports.PreviewRow
import io.github.ashishkupadhyay.mantis.core.domain.imports.RowDisposition
import io.github.ashishkupadhyay.mantis.core.ui.component.SectionTitle
import io.github.ashishkupadhyay.mantis.core.ui.format.Dates
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyText
import io.github.ashishkupadhyay.mantis.core.ui.money.MoneyTone
import java.time.LocalDate

/**
 * CSV import wizard (doc 05 §4.5): Source → Map (only when needed) → Account → Preview → Import/summary, with a
 * wavy stepper on top and Back / Next at the bottom. Pure; the route owns the file picker and navigation.
 */
@Composable
fun ImportWizardScreen(
    state: ImportUiState,
    today: LocalDate,
    onEvent: (ImportEvent) -> Unit,
    onPickFile: () -> Unit,
    onOpenReview: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MantisTopAppBar(
                title = stringResource(R.string.import_title),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
        bottomBar = { WizardButtons(state, onEvent, onClose, onOpenReview) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Stepper(state.step)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            when (state.step) {
                ImportStep.SOURCE -> SourceStep(state, onPickFile)
                ImportStep.MAP -> MapStep(state, onEvent)
                ImportStep.ACCOUNT -> AccountStep(state, onEvent)
                ImportStep.PREVIEW -> PreviewStep(state, today, onEvent)
                ImportStep.IMPORT -> ImportStepView(state)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Stepper(step: ImportStep) {
    val labels = listOf(R.string.step_source, R.string.step_map, R.string.step_account, R.string.step_preview, R.string.step_import)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MantisWavyProgress(progress = { (step.ordinal + 1) / ImportStep.entries.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.step_counter, step.ordinal + 1, ImportStep.entries.size, stringResource(labels[step.ordinal])),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceStep(state: ImportUiState, onPickFile: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            Icons.Default.UploadFile,
            contentDescription = null,
            modifier = Modifier.width(64.dp).height(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(stringResource(R.string.source_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.source_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.busy) {
            MantisLoadingIndicator()
        } else {
            Button(onClick = onPickFile, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.source_pick)) }
        }
        Text(
            stringResource(R.string.source_presets, state.presets.filter { !it.id.startsWith("generic") }.joinToString { it.name }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetectedBanner(state: ImportUiState) {
    val sniff = state.sniff ?: return
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // A tick only when a preset explains the file; an unknown layout gets a question mark, not false reassurance.
            val known = state.detectedPreset != null
            Icon(
                if (known) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = if (known) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                Text(
                    state.source?.name.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.detectedPreset?.let { stringResource(R.string.detected_preset, it.name) }
                        ?: stringResource(R.string.detected_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.detected_details, sniff.encoding, delimiterName(sniff.delimiter), sniff.headerRow + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MapStep(state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    val sniff = state.sniff ?: return
    val mapping = state.mapping ?: return
    DetectedBanner(state)
    SectionTitle(stringResource(R.string.map_columns))
    PresetPicker(state, onEvent)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sniff.headers.forEachIndexed { index, header ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(header.ifBlank { stringResource(R.string.map_column_n, index + 1) }, style = MaterialTheme.typography.bodyMedium)
                    sniff.sampleRows.firstOrNull()?.getOrNull(index)?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                RoleChip(mapping.roles[index] ?: ColumnRole.IGNORE) { onEvent(ImportEvent.RoleChanged(index, it)) }
            }
        }
    }
    SectionTitle(stringResource(R.string.map_date_format))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (sniff.suggestedDateFormats + mapping.dateFormat).distinct().forEach { format ->
            FilterChip(
                selected = mapping.dateFormat == format,
                onClick = { onEvent(ImportEvent.DateFormatChanged(format)) },
                label = { Text(format) },
            )
        }
    }
    var custom by remember(mapping.dateFormat) { mutableStateOf(mapping.dateFormat) }
    OutlinedTextField(
        value = custom,
        onValueChange = { custom = it; onEvent(ImportEvent.DateFormatChanged(it)) },
        label = { Text(stringResource(R.string.map_date_custom)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PresetPicker(state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(state.detectedPreset?.name ?: stringResource(R.string.map_use_preset)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.presets.forEach { preset ->
                DropdownMenuItem(text = { Text(preset.name) }, onClick = { open = false; onEvent(ImportEvent.PresetChosen(preset.id)) })
            }
        }
    }
}

@Composable
private fun RoleChip(role: ColumnRole, onPick: (ColumnRole) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = role != ColumnRole.IGNORE,
            onClick = { open = true },
            label = { Text(stringResource(role.labelRes())) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ColumnRole.entries.forEach { candidate ->
                DropdownMenuItem(text = { Text(stringResource(candidate.labelRes())) }, onClick = { open = false; onPick(candidate) })
            }
        }
    }
}

@Composable
private fun AccountStep(state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    DetectedBanner(state)
    SectionTitle(stringResource(R.string.account_title))
    Text(
        stringResource(R.string.account_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.accounts.forEach { account ->
            FilterChip(
                selected = state.accountId == account.id,
                onClick = { onEvent(ImportEvent.AccountChosen(account.id)) },
                label = { Text(account.name) },
            )
        }
    }
    if (state.accounts.isEmpty()) Text(stringResource(R.string.account_none), color = MaterialTheme.colorScheme.error)
    if (state.busy) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            MantisWavyProgressIndeterminate(Modifier.fillMaxWidth())
            Text(stringResource(R.string.phase_parsing), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PreviewStep(state: ImportUiState, today: LocalDate, onEvent: (ImportEvent) -> Unit) {
    val preview = state.preview ?: return
    val currency = state.accounts.firstOrNull { it.id == state.accountId }?.currency ?: Currency.INR
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stringResource(R.string.preview_total), preview.total, Modifier.weight(1f))
        StatTile(stringResource(R.string.preview_new), preview.willImport, Modifier.weight(1f))
        StatTile(stringResource(R.string.preview_duplicates), preview.duplicates, Modifier.weight(1f))
        StatTile(stringResource(R.string.preview_errors), preview.errors, Modifier.weight(1f))
    }
    if (preview.duplicates > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.preview_import_duplicates),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.importDuplicates, onCheckedChange = { onEvent(ImportEvent.ImportDuplicatesChanged(it)) })
        }
    }
    val shown = PREVIEW_ROWS.coerceAtMost(preview.rows.size)
    SectionTitle(pluralStringResource(R.plurals.preview_rows, shown, shown))
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        preview.rows.take(PREVIEW_ROWS).forEach { entry -> PreviewRowLine(entry, currency, today) }
    }
}

@Composable
private fun PreviewRowLine(entry: PreviewRow, currency: Currency, today: LocalDate) {
    val row = entry.row
    val tint = when (entry.disposition) {
        RowDisposition.IMPORT -> MaterialTheme.colorScheme.onSurface
        RowDisposition.DUPLICATE -> MaterialTheme.colorScheme.onSurfaceVariant
        RowDisposition.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(
        Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.date?.let { Dates.short(it, today) } ?: "—",
            Modifier.widthIn(min = 64.dp),
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
        Text(
            row.description.ifEmpty { row.raw.joinToString(" | ") },
            Modifier.widthIn(min = 160.dp, max = 220.dp),
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        row.amountMinor?.let { MoneyText(Money(it, currency), style = MaterialTheme.typography.bodySmall, tone = MoneyTone.SIGNED) }
        Text(
            when (entry.disposition) {
                RowDisposition.IMPORT -> stringResource(R.string.row_new)
                RowDisposition.DUPLICATE -> stringResource(R.string.row_duplicate)
                RowDisposition.ERROR -> row.error ?: stringResource(R.string.row_error)
            },
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontFamily = if (entry.disposition == RowDisposition.ERROR) FontFamily.Default else null,
        )
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ImportStepView(state: ImportUiState) {
    val result = state.result
    if (result == null) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            MantisWavyProgressIndeterminate(Modifier.fillMaxWidth())
            Text(
                when (state.phase) {
                    ImportPhase.PARSING -> stringResource(R.string.phase_parsing)
                    ImportPhase.CATEGORIZING -> stringResource(R.string.phase_categorizing)
                    ImportPhase.SAVING, null -> stringResource(R.string.phase_saving)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                if (state.undone) {
                    stringResource(R.string.summary_undone)
                } else {
                    pluralStringResource(R.plurals.summary_imported, result.imported, result.imported)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (state.undone) {
                    pluralStringResource(R.plurals.summary_undone_detail, result.imported, result.imported)
                } else {
                    listOf(
                        pluralStringResource(R.plurals.summary_categorized, result.categorized, result.categorized),
                        pluralStringResource(R.plurals.summary_review, result.needsReview, result.needsReview),
                        pluralStringResource(R.plurals.summary_duplicates, result.duplicates, result.duplicates),
                        pluralStringResource(R.plurals.summary_errors, result.errors, result.errors),
                    ).joinToString(stringResource(R.string.summary_separator))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WizardButtons(state: ImportUiState, onEvent: (ImportEvent) -> Unit, onClose: () -> Unit, onOpenReview: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.step) {
            ImportStep.SOURCE -> Unit
            ImportStep.MAP, ImportStep.ACCOUNT -> {
                TextButton(onClick = { onEvent(ImportEvent.Back) }, enabled = !state.busy) { Text(stringResource(R.string.action_back)) }
                Button(onClick = { onEvent(ImportEvent.Next) }, enabled = !state.busy, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_next))
                }
            }
            ImportStep.PREVIEW -> {
                TextButton(onClick = { onEvent(ImportEvent.Back) }, enabled = !state.busy) { Text(stringResource(R.string.action_back)) }
                val count = state.preview?.willImport ?: 0
                Button(onClick = { onEvent(ImportEvent.Import) }, enabled = !state.busy && count > 0, shapes = ButtonDefaults.shapes()) {
                    Text(pluralStringResource(R.plurals.action_import_n, count, count))
                }
            }
            ImportStep.IMPORT -> {
                val result = state.result
                if (result != null && !state.undone) {
                    TextButton(onClick = { onEvent(ImportEvent.Undo) }) { Text(stringResource(R.string.action_undo_import)) }
                }
                if (result != null && result.needsReview > 0 && !state.undone) {
                    FilledTonalButton(onClick = onOpenReview) {
                        Text(pluralStringResource(R.plurals.action_review_n, result.needsReview, result.needsReview))
                    }
                }
                Button(onClick = onClose, enabled = result != null, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}

@Composable
private fun delimiterName(delimiter: Char): String = when (delimiter) {
    ',' -> stringResource(R.string.delimiter_comma)
    ';' -> stringResource(R.string.delimiter_semicolon)
    '\t' -> stringResource(R.string.delimiter_tab)
    else -> delimiter.toString()
}

internal fun ColumnRole.labelRes(): Int = when (this) {
    ColumnRole.DATE -> R.string.role_date
    ColumnRole.DESCRIPTION -> R.string.role_description
    ColumnRole.DEBIT -> R.string.role_debit
    ColumnRole.CREDIT -> R.string.role_credit
    ColumnRole.AMOUNT -> R.string.role_amount
    ColumnRole.DR_CR -> R.string.role_dr_cr
    ColumnRole.BALANCE -> R.string.role_balance
    ColumnRole.REFERENCE -> R.string.role_reference
    ColumnRole.CATEGORY -> R.string.role_category
    ColumnRole.IGNORE -> R.string.role_ignore
}

private const val PREVIEW_ROWS = 20
