package io.github.ashishkupadhyay.mantis.deeplink

import android.content.Intent
import android.net.Uri
import io.github.ashishkupadhyay.mantis.core.ui.navigation.ImportWizard
import io.github.ashishkupadhyay.mantis.core.ui.navigation.TopLevelDestination
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Share-sheet CSVs are the second import entry point (FR-IMP-1); only content URIs of an allowed type get through (NFR-20f). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareIntentParserTest {

    private val parser = ShareIntentParser()
    private val file: Uri = Uri.parse("content://com.android.providers.downloads.documents/document/42")

    private fun send(type: String?, stream: Uri? = file, action: String = Intent.ACTION_SEND) = Intent(action).apply {
        this.type = type
        stream?.let { putExtra(Intent.EXTRA_STREAM, it) }
    }

    @Test
    fun aSharedCsvOpensTheWizardOnThatFile() {
        val expected = DeepLink(TopLevelDestination.HOME, listOf(ImportWizard(file.toString())))
        parser.parse(send("text/csv")) shouldBe expected
        parser.parse(send("text/comma-separated-values")) shouldBe expected
        parser.parse(send("Text/CSV; charset=utf-8")) shouldBe expected
    }

    @Test
    fun rejectsOtherActionsTypesAndSchemes() {
        parser.parse(null).shouldBeNull()
        parser.parse(send("text/csv", action = Intent.ACTION_VIEW)).shouldBeNull()
        parser.parse(send("image/png")).shouldBeNull()
        parser.parse(send(null)).shouldBeNull()
        parser.parse(send("text/csv", stream = null)).shouldBeNull()
        parser.parse(send("text/csv", stream = Uri.parse("file:///sdcard/statement.csv"))).shouldBeNull()
        parser.parse(send("text/csv", stream = Uri.parse("https://example.com/statement.csv"))).shouldBeNull()
    }
}
