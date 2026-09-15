package io.github.ashishkupadhyay.mantis.feature.accounts

import androidx.compose.material3.SnackbarHostState
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
import io.github.ashishkupadhyay.mantis.core.model.AccountType
import io.github.ashishkupadhyay.mantis.core.model.BillingCycle
import io.github.ashishkupadhyay.mantis.core.testing.screenshot.MantisScreenshots
import io.github.ashishkupadhyay.mantis.core.ui.transaction.toRowUi
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Accounts list, card detail and editor in light, dark and 200 % font (doc 06 definition of done). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
class AccountsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(dark) then DeviceConfigurationOverride.FontScale(fontScale)) {
                MantisPreviewTheme(content = content)
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/accounts_$name.png", roborazziOptions = MantisScreenshots.options)
    }

    @Test
    fun list_light() = capture("list_light") { AccountsScreen(previewAccounts(), onOpen = {}, onAdd = {}) }

    @Test
    fun list_dark() = capture("list_dark", dark = true) { AccountsScreen(previewAccounts(), onOpen = {}, onAdd = {}) }

    @Test
    fun list_font200() = capture("list_font200", fontScale = 2f) { AccountsScreen(previewAccounts(), onOpen = {}, onAdd = {}) }

    @Composable
    private fun Detail() {
        val card = cardAccount()
        val rows = listOf(
            expense(card.id, 710, TODAY),
            expense(card.id, 3_819, TODAY.minusDays(1)),
        ).map { it.toRowUi(emptyMap(), mapOf(card.id to card)) }
        val paging = remember { flowOf(PagingData.from(rows, sourceLoadStates = SETTLED)) }
        val cycle = BillingCycle.of(statementDay = 18, dueDay = 6, today = TODAY)
        AccountDetailScreen(
            state = AccountDetailUiState(
                account = card,
                balance = Money.ofMajor(-21_400, Currency.INR),
                card = CardCycleUi(cycle, Money.ofMajor(-4_529, Currency.INR), daysUntilDue = 23, daysUntilStatement = 5),
                loaded = true,
            ),
            rows = paging.collectAsLazyPagingItems(),
            snackbarHostState = remember { SnackbarHostState() },
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
    fun editor_light() = capture("editor_light") {
        AccountEditorScreen(
            state = AccountEditorUiState(
                name = "ICICI Card", type = AccountType.CREDIT_CARD, institutionId = "icici", last4 = "8831", loaded = true,
            ),
            today = LocalDate.of(2026, 9, 13),
            onEvent = {},
            onBack = {},
        )
    }

    @Test
    fun editor_font200() = capture("editor_font200", fontScale = 2f) {
        AccountEditorScreen(state = AccountEditorUiState(loaded = true), today = TODAY, onEvent = {}, onBack = {})
    }

    private companion object {
        val SETTLED = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = true),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )
    }
}
