package io.github.ashishkupadhyay.mantis.feature.categories.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.domain.categorization.RuleMatcher
import io.github.ashishkupadhyay.mantis.core.domain.categorization.RuleValidator
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.repository.TransactionRepository
import io.github.ashishkupadhyay.mantis.core.domain.usecase.ApplyRuleUseCase
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.RuleField
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import io.github.ashishkupadhyay.mantis.core.model.RuleMatchType
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import io.github.ashishkupadhyay.mantis.core.model.TransactionFilter
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.model.TransactionType
import io.github.ashishkupadhyay.mantis.core.ui.navigation.RuleEditor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The rule form (FR-CAT-6): match type, pattern with live validation, field, category, "apply to existing". */
data class RuleEditorUiState(
    val isNew: Boolean = true,
    val matchType: RuleMatchType = RuleMatchType.CONTAINS,
    val pattern: String = "",
    val field: RuleField = RuleField.DESCRIPTION,
    val categoryId: CategoryId? = null,
    val applyToExisting: Boolean = true,
    val categories: ImmutableList<Category> = persistentListOf(),
    /** Validation message for the pattern, or null when it is fine (NFR-20b regex checks run here). */
    val patternError: String? = null,
    val categoryError: Boolean = false,
    /** Sample narrations from recent transactions that the current pattern would match — a live preview. */
    val preview: ImmutableList<String> = persistentListOf(),
    val saving: Boolean = false,
    val loaded: Boolean = false,
) {
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == categoryId }
}

sealed interface RuleEditorEvent {
    data class MatchTypeChanged(val type: RuleMatchType) : RuleEditorEvent
    data class PatternChanged(val value: String) : RuleEditorEvent
    data class FieldChanged(val field: RuleField) : RuleEditorEvent
    data class CategoryChanged(val id: CategoryId?) : RuleEditorEvent
    data class ApplyToExistingChanged(val apply: Boolean) : RuleEditorEvent
    data object Save : RuleEditorEvent
}

sealed interface RuleEditorEffect {
    data class Saved(val id: RuleId) : RuleEditorEffect
}

@OptIn(FlowPreview::class)
@HiltViewModel(assistedFactory = RuleEditorViewModel.Factory::class)
class RuleEditorViewModel @AssistedInject constructor(
    @Assisted private val key: RuleEditor,
    private val categories: CategoryRepository,
    private val transactions: TransactionRepository,
    private val applyRule: ApplyRuleUseCase,
    private val messages: UserMessages,
    private val ids: UuidV7,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: RuleEditor): RuleEditorViewModel
    }

    private val _state = MutableStateFlow(RuleEditorUiState())
    val state: StateFlow<RuleEditorUiState> = _state.asStateFlow()

    private val _effects = Channel<RuleEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var existing: CategoryRule? = null

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            _state.map { Triple(it.matchType, it.pattern, it.field) }.distinctUntilChanged().debounce(PREVIEW_DEBOUNCE_MS)
                .collect { (type, pattern, field) -> _state.update { it.copy(preview = preview(type, pattern, field)) } }
        }
    }

    private suspend fun load() {
        val cats = categories.observeCategories().first().filter { !it.meta.isDeleted && !it.isHidden }.toImmutableList()
        val rule = key.id?.let { id -> categories.rules().firstOrNull { it.id.value == id } }
        existing = rule
        _state.value = if (rule != null) {
            RuleEditorUiState(
                isNew = false,
                matchType = rule.matchType,
                pattern = rule.pattern,
                field = rule.field,
                categoryId = rule.categoryId,
                applyToExisting = false,
                categories = cats,
                loaded = true,
            )
        } else {
            RuleEditorUiState(
                pattern = key.pattern.orEmpty(),
                categoryId = key.categoryId?.let(::CategoryId),
                categories = cats,
                loaded = true,
            )
        }
    }

    fun onEvent(event: RuleEditorEvent) {
        when (event) {
            is RuleEditorEvent.MatchTypeChanged -> _state.update {
                val field = if (event.type == RuleMatchType.MERCHANT) RuleField.MERCHANT else it.field
                it.copy(matchType = event.type, field = field, patternError = liveError(event.type, it.pattern))
            }
            is RuleEditorEvent.PatternChanged -> _state.update {
                val pattern = event.value.take(CategoryRule.MAX_PATTERN_LENGTH)
                it.copy(pattern = pattern, patternError = liveError(it.matchType, pattern))
            }
            is RuleEditorEvent.FieldChanged -> _state.update { it.copy(field = event.field) }
            is RuleEditorEvent.CategoryChanged -> _state.update { it.copy(categoryId = event.id, categoryError = false) }
            is RuleEditorEvent.ApplyToExistingChanged -> _state.update { it.copy(applyToExisting = event.apply) }
            RuleEditorEvent.Save -> save()
        }
    }

    private fun save() {
        val s = _state.value
        if (s.saving) return
        val patternError = RuleValidator.validate(s.matchType, s.pattern)?.message
        val categoryId = s.categoryId
        if (patternError != null || categoryId == null) {
            _state.update { it.copy(patternError = patternError, categoryError = categoryId == null) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val current = existing
            val rule = CategoryRule(
                id = current?.id ?: RuleId(ids.nextString()),
                matchType = s.matchType,
                pattern = s.pattern.trim(),
                field = s.field,
                categoryId = categoryId,
                priority = current?.priority ?: nextPriority(),
                createdFromTransactionId = current?.createdFromTransactionId ?: key.fromTransactionId?.let(::TransactionId),
                hitCount = current?.hitCount ?: 0,
                meta = current?.meta ?: SyncMeta.unstamped(),
            )
            categories.saveRule(rule)
            if (s.applyToExisting) {
                val changed = applyRule(rule)
                val noun = if (changed == 1) "transaction" else "transactions"
                messages.show("Rule saved · $changed $noun recategorized")
            } else {
                messages.show("Rule saved")
            }
            _effects.send(RuleEditorEffect.Saved(rule.id))
        }
    }

    /** While typing, an empty pattern is not yet an error; everything else is validated as it changes. */
    private fun liveError(type: RuleMatchType, pattern: String): String? =
        if (pattern.isBlank()) null else RuleValidator.validate(type, pattern)?.message

    /** New rules go on top: they are what the user is thinking about right now. */
    private suspend fun nextPriority(): Int = (categories.rules().maxOfOrNull { it.priority } ?: 0) + 1

    private suspend fun preview(type: RuleMatchType, pattern: String, field: RuleField): ImmutableList<String> {
        if (pattern.isBlank() || RuleValidator.validate(type, pattern) != null) return persistentListOf()
        val probe = CategoryRule(RuleId("preview"), type, pattern.trim(), field, CategoryId("preview"), meta = SyncMeta.unstamped())
        val matcher = RuleMatcher(listOf(probe))
        return transactions.list(TransactionFilter(types = setOf(TransactionType.EXPENSE, TransactionType.INCOME)), PREVIEW_SCAN)
            .filter { matcher.match(it.descriptionRaw, it.merchantNormalized) != null }
            .map { it.descriptionRaw }
            .distinct()
            .take(PREVIEW_LIMIT)
            .toImmutableList()
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 250L
        const val PREVIEW_SCAN = 2_000
        const val PREVIEW_LIMIT = 3
    }
}
