package io.github.ashishkupadhyay.mantis.feature.importwizard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeStatementImporter
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.domain.imports.ColumnMapping
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportPreview
import io.github.ashishkupadhyay.mantis.core.domain.imports.ImportResult
import io.github.ashishkupadhyay.mantis.core.domain.imports.PreviewRow
import io.github.ashishkupadhyay.mantis.core.domain.imports.RowDisposition
import io.github.ashishkupadhyay.mantis.core.domain.imports.SniffResult
import io.github.ashishkupadhyay.mantis.core.model.Account
import io.github.ashishkupadhyay.mantis.core.model.AccountId
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.ImportBatchId
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.testing.screenshot.MantisScreenshots
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Every wizard page in light / dark / 200 % font (doc 06 definition of done). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class ImportWizardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 13)
    private val importer = FakeStatementImporter()
    private val accounts = persistentListOf(
        account("bank", "HDFC Savings", AccountType.BANK),
        account("card", "Axis Credit Card", AccountType.CREDIT_CARD),
    )
    private val rows = FakeStatementImporter.SAMPLE_ROWS.toImmutableList()

    private fun account(id: String, name: String, type: AccountType) =
        Account(AccountId(id), name, type, Currency.INR, Money.zero(Currency.INR), LocalDate.of(2026, 1, 1), meta = testMeta())

    private fun sniff(name: String): SniffResult = runBlocking { importer.sniff(FakeStatementImporter.source(name)).getOrThrow() }

    private fun capture(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(dark) then DeviceConfigurationOverride.FontScale(fontScale)) {
                MantisPreviewTheme(content = content)
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/import_$name.png", roborazziOptions = MantisScreenshots.options)
    }

    @Composable
    private fun Wizard(state: ImportUiState) =
        ImportWizardScreen(state, today, onEvent = {}, onPickFile = {}, onOpenReview = {}, onClose = {})

    private val source = ImportUiState(presets = importer.presets().toImmutableList(), accounts = accounts, accountId = accounts[0].id)

    private val map = source.copy(
        step = ImportStep.MAP,
        source = FakeStatementImporter.source("mystery.csv"),
        sniff = sniff("mystery.csv"),
        mapping = ColumnMapping(emptyMap(), "dd/MM/yyyy"),
        error = "Map the date, description and amount columns first",
    )

    private val account = source.copy(
        step = ImportStep.ACCOUNT,
        source = FakeStatementImporter.source("hdfc-sep.csv"),
        sniff = sniff("hdfc-sep.csv"),
        mapping = importer.preset.mapping,
    )

    private val preview = account.copy(
        step = ImportStep.PREVIEW,
        rows = rows,
        preview = ImportPreview(
            rows = listOf(
                PreviewRow(rows[0], RowDisposition.IMPORT),
                PreviewRow(rows[1], RowDisposition.DUPLICATE, TransactionId("t-existing")),
                PreviewRow(rows[2], RowDisposition.IMPORT),
                PreviewRow(rows[3], RowDisposition.ERROR),
            ),
            total = 4,
            valid = 3,
            duplicates = 1,
            errors = 1,
            willImport = 2,
        ),
    )

    private val summary = preview.copy(
        step = ImportStep.IMPORT,
        result = ImportResult(ImportBatchId("batch"), imported = 2, duplicates = 1, errors = 1, categorized = 1, needsReview = 1),
    )

    @Test
    fun source_light() = capture("source_light") { Wizard(source) }

    @Test
    fun source_dark() = capture("source_dark", dark = true) { Wizard(source) }

    @Test
    fun source_font200() = capture("source_font200", fontScale = 2f) { Wizard(source) }

    @Test
    fun map_light() = capture("map_light") { Wizard(map) }

    @Test
    fun account_light() = capture("account_light") { Wizard(account) }

    @Test
    fun preview_light() = capture("preview_light") { Wizard(preview) }

    @Test
    fun preview_dark() = capture("preview_dark", dark = true) { Wizard(preview) }

    @Test
    fun preview_font200() = capture("preview_font200", fontScale = 2f) { Wizard(preview) }

    @Test
    fun saving_light() = capture("saving_light") { Wizard(preview.copy(step = ImportStep.IMPORT, busy = true, phase = ImportPhase.SAVING)) }

    @Test
    fun summary_light() = capture("summary_light") { Wizard(summary) }

    @Test
    fun summary_dark() = capture("summary_dark", dark = true) { Wizard(summary) }

    @Test
    fun summary_undone_light() = capture("summary_undone_light") { Wizard(summary.copy(undone = true)) }
}
