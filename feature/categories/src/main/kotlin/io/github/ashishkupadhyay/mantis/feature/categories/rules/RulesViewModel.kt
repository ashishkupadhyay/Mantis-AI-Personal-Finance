package io.github.ashishkupadhyay.mantis.feature.categories.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.usecase.ApplyRuleUseCase
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryRule
import io.github.ashishkupadhyay.mantis.core.model.RuleId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A rule with its target category resolved for display. */
data class RuleUi(val rule: CategoryRule, val category: Category?)

data class RulesUiState(
    val rules: ImmutableList<RuleUi> = persistentListOf(),
    val loaded: Boolean = false,
)

sealed interface RulesEvent {
    data class Delete(val id: RuleId) : RulesEvent
    /** Swap priority with the neighbour above (`up`) or below. Higher in the list = evaluated first. */
    data class Move(val id: RuleId, val up: Boolean) : RulesEvent
    data class ApplyToExisting(val id: RuleId) : RulesEvent
}

/** Rules list (FR-CAT-6): priority order, delete, reorder, "apply to existing". */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val categories: CategoryRepository,
    private val applyRule: ApplyRuleUseCase,
    private val messages: UserMessages,
) : ViewModel() {

    val state: StateFlow<RulesUiState> = combine(categories.observeRules(), categories.observeCategories()) { rules, cats ->
        val byId = cats.associateBy { it.id }
        RulesUiState(
            rules = rules.filter { !it.meta.isDeleted }
                .sortedWith(compareByDescending<CategoryRule> { it.priority }.thenBy { it.meta.updatedAt })
                .map { RuleUi(it, byId[it.categoryId]) }
                .toImmutableList(),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RulesUiState())

    fun onEvent(event: RulesEvent) {
        when (event) {
            is RulesEvent.Delete -> viewModelScope.launch {
                categories.deleteRule(event.id)
                messages.show("Rule deleted")
            }
            is RulesEvent.Move -> viewModelScope.launch { move(event.id, event.up) }
            is RulesEvent.ApplyToExisting -> viewModelScope.launch {
                val rule = categories.rules().firstOrNull { it.id == event.id } ?: return@launch
                val changed = applyRule(rule)
                messages.show(if (changed == 1) "Recategorized 1 transaction" else "Recategorized $changed transactions")
            }
        }
    }

    private suspend fun move(id: RuleId, up: Boolean) {
        val ordered = categories.rules().filter { !it.meta.isDeleted }
            .sortedWith(compareByDescending<CategoryRule> { it.priority }.thenBy { it.meta.updatedAt })
        val index = ordered.indexOfFirst { it.id == id }
        val other = index + if (up) -1 else 1
        if (index < 0 || other !in ordered.indices) return
        val reordered = ordered.toMutableList()
        reordered[index] = ordered[other]
        reordered[other] = ordered[index]
        // Priorities are re-numbered top-down so every rule has a distinct, meaningful value.
        reordered.forEachIndexed { position, rule ->
            val priority = reordered.size - position
            if (rule.priority != priority) categories.saveRule(rule.copy(priority = priority))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
