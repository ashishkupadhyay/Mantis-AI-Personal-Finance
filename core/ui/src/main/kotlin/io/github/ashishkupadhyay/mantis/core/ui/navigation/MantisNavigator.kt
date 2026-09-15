package io.github.ashishkupadhyay.mantis.core.ui.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.SharedFlow

/**
 * What a feature can do with navigation. Features receive it through their [EntryProviderInstaller] and never
 * touch back stacks directly; the shell's [MantisNavigationState] implements it.
 */
@Stable
interface MantisNavigator {

    /** Pushes [key] onto the current tab's stack, or switches tabs when [key] is a top-level key. */
    fun navigate(key: MantisKey)

    /** Pops the current tab; at a tab's root returns to Home ("exit through home"); at Home's root does nothing. */
    fun goBack()

    /** Switches to [destination] and replaces its stack with `destination.key + stack` (deep links, notifications, assistant). */
    fun openStack(destination: TopLevelDestination, stack: List<MantisKey> = emptyList())

    /** Emits the top-level key when its tab is tapped while already selected; screens scroll to top on it. */
    val reselectEvents: SharedFlow<MantisKey>
}

/**
 * A feature's contribution of navigation entries. Each feature binds one into a Hilt `Set<EntryProviderInstaller>`
 * (`@IntoSet`) and the app shell assembles the `entryProvider` from the set, so `app` never references feature
 * screens (doc 02 §3).
 */
fun interface EntryProviderInstaller {
    fun EntryProviderScope<NavKey>.install(navigator: MantisNavigator)
}
