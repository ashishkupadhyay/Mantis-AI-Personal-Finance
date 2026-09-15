package io.github.ashishkupadhyay.mantis.feature.importwizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnMapping
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnRole
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportPreset
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportPreview
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportRequest
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportResult
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ParsedRow
import io.github.ashishkupadhyay.mantis.core.domain.imports.SniffResult
import io.github.ashishkupadhyay.mantis.core.domain.imports.StatementImporter
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.repository.AccountRepository
import io.github.ashishkupadhyay.mantis.core.domain.usecase.ImportStatementUseCase
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The five wizard pages (doc 05 §4.5). MAP is skipped when a preset already explains the file. */
enum class ImportStep { SOURCE, MAP, ACCOUNT, PREVIEW, IMPORT }

/** What the progress bar says while the batch is being written. */
enum class ImportPhase { PARSING, CATEGORIZING, SAVING }

data class ImportUiState(
    val step: ImportStep = ImportStep.SOURCE,
    val source: ImportSource? = null,
    val sniff: SniffResult? = null,
    val mapping: ColumnMapping? = null,
    val presets: ImmutableList<ImportPreset> = persistentListOf(),
    val accounts: ImmutableList<Account> = persistentListOf(),
    val accountId: AccountId? = null,
    val rows: ImmutableList<ParsedRow> = persistentListOf(),
    val preview: ImportPreview? = null,
    val importDuplicates: Boolean = false,
    val phase: ImportPhase? = null,
    val result: ImportResult? = null,
    val undone: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false,
) {
    val detectedPreset: ImportPreset? get() = sniff?.preset
    val mappingComplete: Boolean get() = mapping?.isComplete == true

    /** A recognised layout skips the mapping page; Back from Account then returns to Source. */
    val skippedMapping: Boolean get() = detectedPreset != null && sniff?.suggestedMapping?.isComplete == true
}

sealed interface ImportEvent {
    /** The user picked or shared a file; the source opens the stream on demand. */
    data class SourceChosen(val source: ImportSource) : ImportEvent
    data class RoleChanged(val column: Int, val role: ColumnRole) : ImportEvent
    data class DateFormatChanged(val format: String) : ImportEvent
    data class PresetChosen(val id: String) : ImportEvent
    data class AccountChosen(val id: AccountId) : ImportEvent
    data class ImportDuplicatesChanged(val import: Boolean) : ImportEvent
    data object Next : ImportEvent
    data object Back : ImportEvent
    data object Import : ImportEvent
    data object Undo : ImportEvent
}

/**
 * Drives the wizard: source → (map) → account → preview → import → summary (FR-IMP-1..8). Parsing runs once when
 * the preview opens; the commit re-checks duplicates so the counts the user saw are the counts that happen.
 */
@HiltViewModel(assistedFactory = ImportViewModel.Factory::class)
class ImportViewModel @AssistedInject constructor(
    @Assisted private val initialSource: ImportSource?,
    private val importer: StatementImporter,
    private val importStatement: ImportStatementUseCase,
    private val accounts: AccountRepository,
    private val messages: UserMessages,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(initialSource: ImportSource?): ImportViewModel
    }

    private val _state = MutableStateFlow(ImportUiState(presets = importer.presets().toImmutableList()))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val all = accounts.observeAccounts(includeArchived = false).first()
            _state.update { it.copy(accounts = all.toImmutableList(), accountId = it.accountId ?: all.firstOrNull()?.id) }
            initialSource?.let { load(it) }
        }
    }

    fun onEvent(event: ImportEvent) {
        when (event) {
            is ImportEvent.SourceChosen -> viewModelScope.launch { load(event.source) }
            is ImportEvent.RoleChanged -> _state.update { s -> s.roleChanged(event.column, event.role) }
            is ImportEvent.DateFormatChanged -> _state.update { s -> s.copy(mapping = s.mapping?.copy(dateFormat = event.format)) }
            is ImportEvent.PresetChosen -> _state.update { s ->
                val preset = s.presets.firstOrNull { it.id == event.id } ?: return@update s
                s.copy(sniff = s.sniff?.copy(preset = preset), mapping = preset.mapping, error = null)
            }
            is ImportEvent.AccountChosen -> _state.update { it.copy(accountId = event.id) }
            is ImportEvent.ImportDuplicatesChanged -> {
                _state.update { it.copy(importDuplicates = event.import) }
                viewModelScope.launch { refreshPreview() }
            }
            ImportEvent.Next -> viewModelScope.launch { next() }
            ImportEvent.Back -> back()
            ImportEvent.Import -> viewModelScope.launch { commit() }
            ImportEvent.Undo -> viewModelScope.launch { undo() }
        }
    }

    /**
     * A role lives on one column at a time; assigning it elsewhere clears the previous holder. Mapping the date
     * column re-infers the pattern from that column's sample values so the user rarely has to type one.
     */
    private fun ImportUiState.roleChanged(column: Int, role: ColumnRole): ImportUiState {
        val mapping = mapping ?: return this
        val roles = mapping.roles.filterValues { it != role || role == ColumnRole.IGNORE }.toMutableMap()
        if (role == ColumnRole.IGNORE) roles.remove(column) else roles[column] = role
        if (role != ColumnRole.DATE) return copy(mapping = mapping.copy(roles = roles), error = null)
        val samples = sniff?.sampleRows.orEmpty().mapNotNull { it.getOrNull(column)?.takeIf(String::isNotBlank) }
        val formats = importer.dateFormatsFor(samples)
        return copy(
            mapping = mapping.copy(roles = roles, dateFormat = formats.firstOrNull() ?: mapping.dateFormat),
            sniff = sniff?.copy(suggestedDateFormats = formats),
            error = null,
        )
    }

    private suspend fun load(source: ImportSource) {
        _state.update { it.copy(busy = true, error = null, source = source) }
        when (val sniffed = importer.sniff(source)) {
            is Outcome.Success -> {
                val sniff = sniffed.value
                val needsMapping = sniff.preset == null || !sniff.suggestedMapping.isComplete
                _state.update {
                    it.copy(
                        busy = false,
                        sniff = sniff,
                        mapping = sniff.suggestedMapping,
                        rows = persistentListOf(),
                        preview = null,
                        result = null,
                        step = if (needsMapping) ImportStep.MAP else ImportStep.ACCOUNT,
                    )
                }
            }
            is Outcome.Failure -> _state.update { it.copy(busy = false, source = null, error = sniffed.error.message) }
        }
    }

    private suspend fun next() {
        val s = _state.value
        when (s.step) {
            ImportStep.SOURCE -> Unit
            ImportStep.MAP -> if (s.mappingComplete) _state.update { it.copy(step = ImportStep.ACCOUNT) } else {
                _state.update { it.copy(error = "Map the date, description and amount columns first") }
            }
            ImportStep.ACCOUNT -> if (s.accountId != null) parseAndPreview() else _state.update { it.copy(error = "Choose an account") }
            ImportStep.PREVIEW -> commit()
            ImportStep.IMPORT -> Unit
        }
    }

    private fun back() {
        _state.update { s ->
            val previous = when (s.step) {
                ImportStep.SOURCE -> ImportStep.SOURCE
                ImportStep.MAP -> ImportStep.SOURCE
                ImportStep.ACCOUNT -> if (s.skippedMapping) ImportStep.SOURCE else ImportStep.MAP
                ImportStep.PREVIEW -> ImportStep.ACCOUNT
                ImportStep.IMPORT -> ImportStep.PREVIEW
            }
            s.copy(step = previous, error = null)
        }
    }

    private suspend fun parseAndPreview() {
        val s = _state.value
        val source = s.source ?: return
        val sniff = s.sniff ?: return
        val mapping = s.mapping ?: return
        _state.update { it.copy(busy = true, phase = ImportPhase.PARSING, error = null) }
        when (val parsed = importer.parse(source, sniff, mapping)) {
            is Outcome.Success -> {
                _state.update { it.copy(rows = parsed.value.toImmutableList()) }
                refreshPreview()
                _state.update { it.copy(busy = false, phase = null, step = ImportStep.PREVIEW) }
            }
            is Outcome.Failure -> _state.update { it.copy(busy = false, phase = null, error = parsed.error.message) }
        }
    }

    private suspend fun refreshPreview() {
        val s = _state.value
        val accountId = s.accountId ?: return
        if (s.rows.isEmpty()) return
        val preview = importStatement.preview(accountId, s.rows, s.importDuplicates)
        _state.update { it.copy(preview = preview) }
    }

    private suspend fun commit() {
        val s = _state.value
        val source = s.source ?: return
        val accountId = s.accountId ?: return
        _state.update { it.copy(step = ImportStep.IMPORT, busy = true, phase = ImportPhase.CATEGORIZING, error = null) }
        val request = ImportRequest(source, accountId, s.detectedPreset?.id, s.rows, s.importDuplicates)
        _state.update { it.copy(phase = ImportPhase.SAVING) }
        when (val result = importStatement.commit(request)) {
            is Outcome.Success -> _state.update { it.copy(busy = false, phase = null, result = result.value) }
            is Outcome.Failure -> _state.update {
                it.copy(busy = false, phase = null, step = ImportStep.PREVIEW, error = result.error.message)
            }
        }
    }

    private suspend fun undo() {
        val result = _state.value.result ?: return
        val removed = importStatement.undo(result.batchId)
        _state.update { it.copy(undone = true) }
        messages.show("Import undone · $removed transactions removed")
    }
}
