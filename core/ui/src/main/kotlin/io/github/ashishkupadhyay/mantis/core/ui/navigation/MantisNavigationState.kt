package io.github.ashishkupadhyay.mantis.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Navigation state that survives configuration changes and process death (doc 02 §4.2): one back stack per
 * [TopLevelDestination] plus the selected tab, all saved through `rememberSerializable`/`rememberNavBackStack`.
 */
@Composable
fun rememberMantisNavigationState(): MantisNavigationState {
    val selected = rememberSerializable(serializer = MutableStateSerializer(NavKeySerializer())) {
        mutableStateOf<NavKey>(TopLevelDestination.start.key)
    }
    val stacks = TopLevelDestination.entries.associate { destination ->
        destination to rememberNavBackStack(destination.key)
    }
    return remember { MantisNavigationState(selected, stacks) }
}

/**
 * Holds the tab stacks and applies the multi-stack rules from the Navigation 3 "multiple back stacks" recipe:
 * the start tab's entries are always present ("exit through home"), and at most one other tab's entries sit on top
 * of them. Tabs not in use keep their state.
 */
class MantisNavigationState internal constructor(
    selectedKey: MutableState<NavKey>,
    private val stacks: Map<TopLevelDestination, NavBackStack<NavKey>>,
) : MantisNavigator {

    private var selectedKey: NavKey by selectedKey
    private val reselect = MutableSharedFlow<MantisKey>(extraBufferCapacity = 1)

    val selected: TopLevelDestination
        get() = TopLevelDestination.of(selectedKey as MantisKey) ?: TopLevelDestination.start

    override val reselectEvents: SharedFlow<MantisKey> = reselect.asSharedFlow()

    override fun navigate(key: MantisKey) {
        val destination = TopLevelDestination.of(key)
        if (destination != null) {
            selectedKey = destination.key
        } else {
            currentStack().add(key)
        }
    }

    override fun goBack() {
        val stack = currentStack()
        if (stack.size <= 1) {
            if (selected != TopLevelDestination.start) selectedKey = TopLevelDestination.start.key
        } else {
            stack.removeLastOrNull()
        }
    }

    override fun openStack(destination: TopLevelDestination, stack: List<MantisKey>) {
        val target = stacks.getValue(destination)
        while (target.size > 1) target.removeLastOrNull()
        target.addAll(stack)
        selectedKey = destination.key
    }

    /** Called by the navigation suite when the selected tab is tapped again. */
    fun reselect(destination: TopLevelDestination) {
        reselect.tryEmit(destination.key)
    }

    /**
     * The entries [androidx.navigation3.ui.NavDisplay] shows: each tab's stack decorated with its own saveable-state
     * holder and ViewModel store, flattened in "start tab first" order.
     */
    @Composable
    fun rememberEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decorated = stacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )
        }
        val inUse = if (selected == TopLevelDestination.start) listOf(selected) else listOf(TopLevelDestination.start, selected)
        return inUse.flatMap { decorated[it].orEmpty() }
    }

    private fun currentStack(): NavBackStack<NavKey> = stacks.getValue(selected)
}
