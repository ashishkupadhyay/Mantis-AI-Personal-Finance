package io.github.ashishkupadhyay.mantis.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.result.Outcome
import io.github.ashishkupadhyay.mantis.core.domain.message.UserMessages
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.DefaultTaxonomy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A group with its leaves, in display order. Hidden rows stay in the list, dimmed, so they can be unhidden. */
data class CategoryGroupUi(val group: Category, val leaves: ImmutableList<Category>)

data class CategoriesUiState(
    val expense: ImmutableList<CategoryGroupUi> = persistentListOf(),
    val income: ImmutableList<CategoryGroupUi> = persistentListOf(),
    val loaded: Boolean = false,
)

sealed interface CategoriesEvent {
    data class SetHidden(val id: CategoryId, val hidden: Boolean) : CategoriesEvent
    data class Delete(val id: CategoryId) : CategoriesEvent
    /** Swap with the previous (`up`) or next sibling — reorder without drag handles (FR-CAT-1). */
    data class Move(val id: CategoryId, val up: Boolean) : CategoriesEvent
}

/** Manage categories (FR-CAT-1, FR-SET-4): two levels, hide/unhide, reorder, delete user rows. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categories: CategoryRepository,
    private val messages: UserMessages,
) : ViewModel() {

    val state: StateFlow<CategoriesUiState> = categories.observeCategories().map { all ->
        val live = all.filter { !it.meta.isDeleted && it.isManaged }
        val byParent = live.filter { !it.isGroup }.groupBy { it.parentId }
        fun groups(kind: CategoryKind) = live.filter { it.isGroup && it.kind == kind }
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
            .map { group ->
                val leaves = byParent[group.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.name }))
                CategoryGroupUi(group, leaves.toImmutableList())
            }
            .toImmutableList()
        CategoriesUiState(expense = groups(CategoryKind.EXPENSE), income = groups(CategoryKind.INCOME), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CategoriesUiState())

    fun onEvent(event: CategoriesEvent) {
        when (event) {
            is CategoriesEvent.SetHidden -> viewModelScope.launch { categories.setHidden(event.id, event.hidden) }
            is CategoriesEvent.Delete -> viewModelScope.launch {
                when (val result = categories.delete(event.id)) {
                    is Outcome.Success -> messages.show("Category deleted")
                    is Outcome.Failure -> messages.show(result.error.message)
                }
            }
            is CategoriesEvent.Move -> viewModelScope.launch { move(event.id, event.up) }
        }
    }

    private suspend fun move(id: CategoryId, up: Boolean) {
        val all = categories.categories()
        val target = all.firstOrNull { it.id == id } ?: return
        val siblings = all
            .filter { !it.meta.isDeleted && it.parentId == target.parentId && it.kind == target.kind && it.isManaged }
            .sortedWith(compareBy({ it.sortOrder }, { it.name }))
        val index = siblings.indexOfFirst { it.id == id }
        val other = index + if (up) -1 else 1
        if (index < 0 || other !in siblings.indices) return
        // Re-number the whole sibling list so ties (fresh installs share sortOrder 0) resolve deterministically.
        val reordered = siblings.toMutableList()
        reordered[index] = siblings[other]
        reordered[other] = siblings[index]
        categories.saveAll(reordered.mapIndexed { position, category -> category.copy(sortOrder = position) })
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** System buckets (uncategorized, transfer) are not user-managed categories. */
internal val Category.isManaged: Boolean
    get() = key != DefaultTaxonomy.KEY_UNCATEGORIZED && key != DefaultTaxonomy.KEY_TRANSFER && kind != CategoryKind.SYSTEM
