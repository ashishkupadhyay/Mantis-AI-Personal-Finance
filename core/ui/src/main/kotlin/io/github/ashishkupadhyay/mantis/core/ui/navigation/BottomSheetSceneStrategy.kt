package io.github.ashishkupadhyay.mantis.core.ui.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Renders an entry inside a [ModalBottomSheet] — the Navigation 3 "bottom sheet" recipe. Used for the add
 * transaction sheet on compact widths (doc 02 §4.2, doc 05 §4.4): the sheet is a real back-stack entry, so back
 * and swipe-down both pop it, and it owns its ViewModel like any other destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal data class BottomSheetScene<T : Any>(
    override val key: T,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val properties: ModalBottomSheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val lifecycleOwner = rememberLifecycleOwner()
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            properties = properties,
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                entry.Content()
            }
        }
    }
}

/** Add this strategy *before* non-overlay strategies (list-detail) in `NavDisplay.sceneStrategies`. */
@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val properties = lastEntry.metadata[BottomSheetKey] ?: return null
        @Suppress("UNCHECKED_CAST")
        return BottomSheetScene(
            key = lastEntry.contentKey as T,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            properties = properties,
            onBack = onBack,
        )
    }

    companion object {
        /** Metadata marking an entry as a bottom sheet; pass it to `entry(metadata = …)`. */
        fun bottomSheet(properties: ModalBottomSheetProperties = ModalBottomSheetProperties()) =
            metadata { put(BottomSheetKey, properties) }

        object BottomSheetKey : NavMetadataKey<ModalBottomSheetProperties>
    }
}
