package io.github.ashishkupadhyay.mantis.feature.categories

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.ashishkupadhyay.mantis.core.ui.navigation.BottomSheetSceneStrategy
import io.github.ashishkupadhyay.mantis.core.ui.navigation.Categories
import io.github.ashishkupadhyay.mantis.core.ui.navigation.CategoryEditor
import io.github.ashishkupadhyay.mantis.core.ui.navigation.CategoryRules
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.core.ui.navigation.MantisNavigator
import io.github.ashishkupadhyay.mantis.core.ui.navigation.RuleEditor
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RuleEditorEffect
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RuleEditorSheet
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RuleEditorViewModel
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RulesScreen
import io.github.ashishkupadhyay.mantis.feature.categories.rules.RulesViewModel

/** Categories, the category editor sheet, rules and the rule editor sheet (FR-CAT-1/6, FR-SET-4). */
object CategoriesEntries : EntryProviderInstaller {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun EntryProviderScope<NavKey>.install(navigator: MantisNavigator) {
        entry<Categories> {
            val viewModel = hiltViewModel<CategoriesViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            CategoriesScreen(
                state = state,
                onEvent = viewModel::onEvent,
                onEdit = { navigator.navigate(CategoryEditor(id = it.value)) },
                onAdd = { parent -> navigator.navigate(CategoryEditor(parentId = parent?.value)) },
                onOpenRules = { navigator.navigate(CategoryRules) },
                onBack = navigator::goBack,
            )
        }
        entry<CategoryEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) { key ->
            CategoryEditorRoute(key, onDone = navigator::goBack)
        }
        entry<CategoryRules> {
            val viewModel = hiltViewModel<RulesViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            RulesScreen(
                state = state,
                onEvent = viewModel::onEvent,
                onEdit = { navigator.navigate(RuleEditor(id = it.value)) },
                onAdd = { navigator.navigate(RuleEditor()) },
                onBack = navigator::goBack,
            )
        }
        entry<RuleEditor>(metadata = BottomSheetSceneStrategy.bottomSheet()) { key ->
            RuleEditorRoute(key, onDone = navigator::goBack)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CategoriesEntriesModule {
    @Provides
    @IntoSet
    fun categoriesEntries(): EntryProviderInstaller = CategoriesEntries
}

@Composable
private fun CategoryEditorRoute(key: CategoryEditor, onDone: () -> Unit) {
    val viewModel = hiltViewModel<CategoryEditorViewModel, CategoryEditorViewModel.Factory>(
        creationCallback = { it.create(key.id, key.parentId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CategoryEditorEffect.Saved -> onDone()
            }
        }
    }
    CategoryEditorSheet(state = state, onEvent = viewModel::onEvent, onClose = onDone)
}

@Composable
private fun RuleEditorRoute(key: RuleEditor, onDone: () -> Unit) {
    val viewModel = hiltViewModel<RuleEditorViewModel, RuleEditorViewModel.Factory>(creationCallback = { it.create(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RuleEditorEffect.Saved -> onDone()
            }
        }
    }
    RuleEditorSheet(state = state, onEvent = viewModel::onEvent, onClose = onDone)
}
