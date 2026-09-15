package io.github.ashishkupadhyay.mantis.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.domain.repository.CategoryRepository
import io.github.ashishkupadhyay.mantis.core.model.Category
import io.github.ashishkupadhyay.mantis.core.model.CategoryId
import io.github.ashishkupadhyay.mantis.core.model.CategoryKind
import io.github.ashishkupadhyay.mantis.core.model.SyncMeta
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Create / edit form for a group or a leaf (FR-CAT-1): name, kind, parent group, icon, colour. */
data class CategoryEditorUiState(
    val isNew: Boolean = true,
    val isSystem: Boolean = false,
    val name: String = "",
    val kind: CategoryKind = CategoryKind.EXPENSE,
    /** `null` = this is a group. */
    val parentId: CategoryId? = null,
    val icon: String = "category",
    val paletteIndex: Int? = null,
    val groups: ImmutableList<Category> = persistentListOf(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
    val loaded: Boolean = false,
) {
    val isGroup: Boolean get() = parentId == null
    val groupsOfKind: List<Category> get() = groups.filter { it.kind == kind }
}

sealed interface CategoryEditorEvent {
    data class NameChanged(val value: String) : CategoryEditorEvent
    data class KindChanged(val kind: CategoryKind) : CategoryEditorEvent
    /** `null` turns the row into a group. */
    data class ParentChanged(val parentId: CategoryId?) : CategoryEditorEvent
    data class IconChanged(val icon: String) : CategoryEditorEvent
    data class ColorChanged(val paletteIndex: Int?) : CategoryEditorEvent
    data object Save : CategoryEditorEvent
}

sealed interface CategoryEditorEffect {
    data class Saved(val id: CategoryId) : CategoryEditorEffect
}

@HiltViewModel(assistedFactory = CategoryEditorViewModel.Factory::class)
class CategoryEditorViewModel @AssistedInject constructor(
    @Assisted("id") private val categoryId: String?,
    @Assisted("parent") private val presetParentId: String?,
    private val categories: CategoryRepository,
    private val ids: UuidV7,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("id") categoryId: String?, @Assisted("parent") presetParentId: String?): CategoryEditorViewModel
    }

    private val _state = MutableStateFlow(CategoryEditorUiState())
    val state: StateFlow<CategoryEditorUiState> = _state.asStateFlow()

    private val _effects = Channel<CategoryEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var existing: Category? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val all = categories.observeCategories().first().filter { !it.meta.isDeleted }
        val groups = all.filter { it.isGroup && it.isManaged }.sortedWith(compareBy({ it.kind }, { it.sortOrder })).toImmutableList()
        val category = categoryId?.let { id -> all.firstOrNull { it.id.value == id } }
        existing = category
        _state.value = if (category != null) {
            CategoryEditorUiState(
                isNew = false,
                isSystem = category.isSystem,
                name = category.name,
                kind = category.kind,
                parentId = category.parentId,
                icon = category.icon,
                paletteIndex = category.paletteIndex,
                groups = groups,
                loaded = true,
            )
        } else {
            val parent = presetParentId?.let { id -> groups.firstOrNull { it.id.value == id } }
            CategoryEditorUiState(
                kind = parent?.kind ?: CategoryKind.EXPENSE,
                parentId = parent?.id,
                groups = groups,
                loaded = true,
            )
        }
    }

    fun onEvent(event: CategoryEditorEvent) {
        when (event) {
            is CategoryEditorEvent.NameChanged -> _state.update { it.copy(name = event.value.take(MAX_NAME), nameError = false) }
            is CategoryEditorEvent.KindChanged -> _state.update { it.copy(kind = event.kind, parentId = null) }
            is CategoryEditorEvent.ParentChanged -> _state.update { s ->
                val parent = s.groups.firstOrNull { it.id == event.parentId }
                s.copy(parentId = parent?.id, kind = parent?.kind ?: s.kind)
            }
            is CategoryEditorEvent.IconChanged -> _state.update { it.copy(icon = event.icon) }
            is CategoryEditorEvent.ColorChanged -> _state.update { it.copy(paletteIndex = event.paletteIndex) }
            CategoryEditorEvent.Save -> save()
        }
    }

    private fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val current = existing
            val siblings = categories.categories().filter { it.parentId == s.parentId && it.kind == s.kind && !it.meta.isDeleted }
            val category = Category(
                id = current?.id ?: CategoryId(ids.nextString()),
                name = s.name.trim(),
                kind = s.kind,
                parentId = s.parentId,
                key = current?.key,
                icon = s.icon,
                colorSeed = Category.colorSeedFor(s.paletteIndex),
                isSystem = current?.isSystem ?: false,
                isHidden = current?.isHidden ?: false,
                sortOrder = current?.sortOrder ?: (siblings.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0),
                meta = current?.meta ?: SyncMeta.unstamped(),
            )
            categories.save(category)
            _effects.send(CategoryEditorEffect.Saved(category.id))
        }
    }

    private companion object {
        const val MAX_NAME = 40
    }
}
