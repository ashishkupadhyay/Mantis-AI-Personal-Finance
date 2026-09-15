package io.github.ashishkupadhyay.mantis.feature.transactions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.domain.fakes.FakeCategoryRepository
import io.github.ashishkupadhyay.mantis.core.domain.fakes.testMeta
import io.github.ashishkupadhyay.mantis.core.model.CategorySource
import io.github.ashishkupadhyay.mantis.core.model.Tag
import io.github.ashishkupadhyay.mantis.core.model.TagId
import io.github.ashishkupadhyay.mantis.core.model.TransactionId
import io.github.ashishkupadhyay.mantis.core.testing.screenshot.MantisScreenshots
import io.github.ashishkupadhyay.mantis.core.ui.transaction.toRowUi
import io.github.ashishkupadhyay.mantis.feature.transactions.add.AddTransactionSheet
import io.github.ashishkupadhyay.mantis.feature.transactions.add.AddTransactionUiState
import io.github.ashishkupadhyay.mantis.feature.transactions.add.AmountEntry
import io.github.ashishkupadhyay.mantis.feature.transactions.add.KeypadKey
import io.github.ashishkupadhyay.mantis.feature.transactions.detail.TransactionDetailScreen
import io.github.ashishkupadhyay.mantis.feature.transactions.detail.TransactionDetailUiState
import io.github.ashishkupadhyay.mantis.feature.transactions.list.TransactionListItem
import io.github.ashishkupadhyay.mantis.feature.transactions.list.TransactionsScreen
import io.github.ashishkupadhyay.mantis.feature.transactions.list.TransactionsUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

/**
 * List, detail and add sheet in light, dark and 200 % font (doc 05 §10, doc 06 definition of done). Record with
 * `gradlew :feature:transactions:recordRoborazziDebug`; CI verifies.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class TransactionsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val categories = runBlocking { FakeCategoryRepository.withDefaults().categories() }
    private val delivery = categories.first { it.key == "food.delivery" }
    private val groceries = categories.first { it.key == "food.groceries" }
    private val salary = categories.first { it.key == "income.salary" }
    private val accounts = listOf(BANK, CARD)
    private val rows = listOf(
        transaction("ZOMATO ORDER", -710, TODAY, CARD, categoryId = delivery.id, id = "t1").copy(merchantNormalized = "zomato"),
        transaction("BLINKIT", -206, TODAY, BANK, categoryId = groceries.id, id = "t2").copy(categorySource = CategorySource.USER),
        transaction("SALARY SEPTEMBER", 80_000, TODAY.minusDays(1), BANK, categoryId = salary.id, id = "t3"),
        transaction("UNKNOWN UPI 8831", -1_250, TODAY.minusDays(1), CARD, id = "t4"),
    )

    private fun items(): List<TransactionListItem> {
        val cats = categories.associateBy { it.id }
        val accs = accounts.associateBy { it.id }
        val inr = { major: Long -> Money.ofMajor(major, Currency.INR) }
        return listOf(
            TransactionListItem.MonthHeader(YearMonth.from(TODAY), inr(80_000 - 710 - 206 - 1_250)),
            TransactionListItem.DayHeader(TODAY, inr(-916), 2),
            TransactionListItem.Row(rows[0].toRowUi(cats, accs)),
            TransactionListItem.Row(rows[1].toRowUi(cats, accs)),
            TransactionListItem.DayHeader(TODAY.minusDays(1), inr(80_000 - 1_250), 2),
            TransactionListItem.Row(rows[2].toRowUi(cats, accs)),
            TransactionListItem.Row(rows[3].toRowUi(cats, accs)),
        )
    }

    private fun capture(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(dark) then DeviceConfigurationOverride.FontScale(fontScale)) {
                MantisPreviewTheme(content = content)
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/transactions_$name.png", roborazziOptions = MantisScreenshots.options)
    }

    @Composable
    private fun List(selection: Set<TransactionId> = emptySet()) {
        val paging = remember { flowOf(PagingData.from(items(), sourceLoadStates = SETTLED)) }
        TransactionsScreen(
            state = TransactionsUiState(
                selection = persistentSetOf(*selection.toTypedArray()),
                accounts = accounts.toImmutableList(),
                categories = categories.toImmutableList(),
                loaded = true,
            ),
            rows = paging.collectAsLazyPagingItems(),
            today = TODAY,
            onEvent = {},
            onOpen = {},
            onAdd = {},
            onImport = {},
        )
    }

    @Test
    fun list_light() = capture("list_light") { List() }

    @Test
    fun list_dark_selecting() = capture("list_dark_selecting", dark = true) {
        List(selection = setOf(TransactionId("t1"), TransactionId("t2")))
    }

    @Test
    fun list_font200() = capture("list_font200", fontScale = 2f) { List() }

    @Composable
    private fun Detail() {
        val goa = Tag(TagId("goa"), "Goa Trip", meta = testMeta())
        TransactionDetailScreen(
            state = TransactionDetailUiState(
                transaction = rows[0].copy(notes = "Team dinner", tags = setOf(goa.id)),
                category = delivery,
                categoryGroup = categories.first { it.id == delivery.parentId },
                account = CARD,
                tags = persistentListOf(goa),
                categories = categories.toImmutableList(),
                loaded = true,
            ),
            today = TODAY,
            onEvent = {},
            onEdit = {},
            onOpenTransaction = {},
            onBack = {},
        )
    }

    @Test
    fun detail_light() = capture("detail_light") { Detail() }

    @Test
    fun detail_dark() = capture("detail_dark", dark = true) { Detail() }

    @Test
    fun detail_font200() = capture("detail_font200", fontScale = 2f) { Detail() }

    @Composable
    private fun Sheet() {
        AddTransactionSheet(
            state = AddTransactionUiState(
                amount = AmountEntry(operand = "349").press(KeypadKey.Plus).press(KeypadKey.Digit('5')).press(KeypadKey.Digit('1')),
                accountId = BANK.id,
                categoryId = delivery.id,
                date = TODAY,
                accounts = accounts.toImmutableList(),
                categories = categories.toImmutableList(),
                likely = categories.filter { !it.isGroup && it.kind == delivery.kind }.take(LIKELY).toImmutableList(),
                loaded = true,
            ),
            today = TODAY,
            onEvent = {},
            onClose = {},
        )
    }

    @Test
    fun sheet_light() = capture("sheet_light") { Sheet() }

    @Test
    fun sheet_dark() = capture("sheet_dark", dark = true) { Sheet() }

    private companion object {
        const val LIKELY = 8
        val SETTLED = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
    }
}
