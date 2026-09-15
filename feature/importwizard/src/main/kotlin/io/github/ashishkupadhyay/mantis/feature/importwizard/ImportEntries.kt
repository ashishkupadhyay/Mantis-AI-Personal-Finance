package io.github.ashishkupadhyay.mantis.feature.importwizard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.ashishkupadhyay.mantis.core.domain.imports.ByteSource
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportSource
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.core.ui.navigation.ImportWizard
import io.github.ashishkupadhyay.mantis.core.ui.navigation.MantisNavigator
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TransactionsFiltered
import java.io.IOException
import java.time.LocalDate

/** The import wizard entry (FR-IMP-1): opened from the FAB menu, a share-sheet CSV, or `mantis://import`. */
object ImportEntries : EntryProviderInstaller {

    /** MIME types accepted from the picker and the share sheet (FR-IMP-1, NFR-20f). */
    val MIME_TYPES: Array<String> = arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "text/plain")

    override fun EntryProviderScope<NavKey>.install(navigator: MantisNavigator) {
        entry<ImportWizard> { key ->
            ImportRoute(
                uri = key.uri,
                onOpenReview = { navigator.navigate(TransactionsFiltered("uncategorized=1")) },
                onClose = navigator::goBack,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ImportEntriesModule {
    @Provides
    @IntoSet
    fun importEntries(): EntryProviderInstaller = ImportEntries
}

@Composable
private fun ImportRoute(uri: String?, onOpenReview: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val initial = remember(uri) { uri?.let { context.sourceFor(it.toUri()) } }
    val viewModel = hiltViewModel<ImportViewModel, ImportViewModel.Factory>(creationCallback = { it.create(initial) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        picked?.let { context.sourceFor(it) }?.let { viewModel.onEvent(ImportEvent.SourceChosen(it)) }
    }
    ImportWizardScreen(
        state = state,
        today = LocalDate.now(),
        onEvent = viewModel::onEvent,
        onPickFile = { picker.launch(ImportEntries.MIME_TYPES) },
        onOpenReview = onOpenReview,
        onClose = onClose,
    )
}

/**
 * Wraps a content URI as an [ImportSource]: name and size from the provider, bytes streamed on demand. A URI the
 * provider refuses (revoked grant, deleted file) fails as an [IOException] when opened, which the importer reports
 * as "Could not read the file" rather than pretending the statement was empty.
 */
private fun Context.sourceFor(uri: Uri): ImportSource {
    var name = uri.lastPathSegment ?: "statement.csv"
    var size = -1L
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = cursor.getString(it) ?: name }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { if (!cursor.isNull(it)) size = cursor.getLong(it) }
            }
        }
    }
    return ImportSource(
        name = name,
        sizeBytes = size,
        bytes = ByteSource {
            try {
                contentResolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
            } catch (e: SecurityException) {
                throw IOException("No permission to read $uri", e)
            }
        },
    )
}
