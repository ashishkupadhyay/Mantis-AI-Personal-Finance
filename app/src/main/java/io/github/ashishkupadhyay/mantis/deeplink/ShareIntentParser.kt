package io.github.ashishkupadhyay.mantis.deeplink

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import io.github.ashishkupadhyay.mantis.core.ui.navigation.ImportWizard
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TopLevelDestination
import io.github.ashishkupadhyay.mantis.feature.importwizard.ImportEntries
import javax.inject.Inject

/**
 * Turns a share-sheet intent (`ACTION_SEND` carrying one `EXTRA_STREAM`) into the import wizard (FR-IMP-1). The
 * manifest filter already narrows the MIME types; this re-checks them and accepts only `content://` URIs, so a
 * hostile sender cannot point the importer at an arbitrary file path (NFR-20f). Anything else yields `null` and
 * the app opens normally. The importer applies its own size cap when it reads the stream.
 */
class ShareIntentParser @Inject constructor() {

    fun parse(intent: Intent?): DeepLink? {
        if (intent?.action != Intent.ACTION_SEND || !accepted(intent.type)) return null
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT } ?: return null
        return DeepLink(TopLevelDestination.HOME, listOf(ImportWizard(uri.toString())))
    }

    /** Case-insensitive on the media type alone; a `; charset=` parameter does not change what the file is. */
    private fun accepted(type: String?): Boolean = type?.substringBefore(';')?.trim()?.lowercase() in ImportEntries.MIME_TYPES
}
