package io.github.ashishkupadhyay.mantis.core.ui.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.ashishkupadhyay.mantis.core.common.money.Currency
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.money.MoneyFormatter
import io.github.ashishkupadhyay.mantis.core.common.money.NumberStyle
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalHideAmounts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Behaviour of the money rendering contract (doc 05 §2.3, FR-PRV-6) — the visuals are covered by screenshots. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MoneyTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val lakh = Money(-12_345_600, Currency.INR)

    private fun show(hidden: Boolean = false, formatter: MoneyFormatter = MoneyFormatter(), content: @Composable () -> Unit) {
        composeRule.setContent {
            MantisPreviewTheme {
                CompositionLocalProvider(LocalHideAmounts provides hidden, LocalMoneyFormatter provides formatter, content = content)
            }
        }
    }

    @Test
    fun rendersFormattedAmountAndSpeaksTheSign() {
        show { MoneyText(lakh, Modifier.testTag(TAG)) }

        composeRule.onNodeWithTag(TAG)
            .assertTextEquals("−₹1,23,456.00")
            .assertContentDescriptionEquals("minus ₹1,23,456.00")
    }

    @Test
    fun honoursTheHideAmountsToggle() {
        show(hidden = true) { MoneyText(lakh, Modifier.testTag(TAG)) }

        composeRule.onNodeWithTag(TAG)
            .assertTextEquals(MoneyFormatter.HIDDEN_MASK)
            .assertContentDescriptionEquals("Amount hidden")
    }

    @Test
    fun usesTheProvidedFormatterAndCompactForm() {
        show(formatter = MoneyFormatter(NumberStyle.INTERNATIONAL)) {
            MoneyText(Money(12_345_600, Currency.INR), Modifier.testTag(TAG))
            MoneyText(Money(12_345_600, Currency.INR), Modifier.testTag(COMPACT_TAG), compact = true)
        }

        composeRule.onNodeWithTag(TAG).assertTextEquals("₹123,456.00")
        composeRule.onNodeWithTag(COMPACT_TAG).assertTextEquals("₹123.5K")
    }

    private companion object {
        const val TAG = "money"
        const val COMPACT_TAG = "compact"
    }
}
