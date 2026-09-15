package io.github.ashishkupadhyay.mantis.core.ui.money

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.isSpecified
import io.github.ashishkupadhyay.mantis.core.common.money.Money
import io.github.ashishkupadhyay.mantis.core.common.money.MoneyFormatter
import io.github.ashishkupadhyay.mantis.core.common.money.MoneyParts
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalHideAmounts
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.LocalMantisColors
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.TABULAR_FIGURES

/** App-wide number formatting (FR-SET-2: grouping style, symbol style); provided once at the root from preferences. */
val LocalMoneyFormatter = staticCompositionLocalOf { MoneyFormatter() }

/** How an amount's sign is communicated (doc 05 §2.2: income green, expenses neutral). */
enum class MoneyTone {
    /** Inherits the surrounding text colour. */
    NEUTRAL,

    /** Positive amounts in the income colour; negative and zero amounts inherit. */
    SIGNED,
}

/**
 * The one way money is rendered (doc 05 §2.3): tabular figures, the currency symbol at 0.75× size, minor units at
 * 0.8× and regular weight, formatting from [LocalMoneyFormatter], and the "hide amounts" mask from
 * [LocalHideAmounts]. Never wraps — a truncated amount is preferable to a misread one.
 */
@Composable
fun MoneyText(
    money: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    tone: MoneyTone = MoneyTone.NEUTRAL,
    showMinor: Boolean = true,
    compact: Boolean = false,
    formatter: MoneyFormatter = LocalMoneyFormatter.current,
    hidden: Boolean = LocalHideAmounts.current,
) {
    val incomeColor = LocalMantisColors.current.income
    val resolvedColor = when {
        color.isSpecified -> color
        tone == MoneyTone.SIGNED && money.minor > 0 -> incomeColor
        else -> Color.Unspecified
    }
    val rendered = remember(money, style.fontSize, showMinor, compact, hidden, formatter) {
        when {
            hidden -> RenderedMoney(AnnotatedString(MoneyFormatter.HIDDEN_MASK), HIDDEN_DESCRIPTION)
            compact -> formatter.formatCompact(money).let { RenderedMoney(AnnotatedString(it), spoken(it)) }
            else -> formatter.parts(money, showMinor).let { RenderedMoney(styled(it, style), spoken(it.toString())) }
        }
    }
    Text(
        text = rendered.text,
        modifier = modifier.semantics { contentDescription = rendered.spoken },
        color = resolvedColor,
        style = style.copy(fontFeatureSettings = TABULAR_FIGURES),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

private class RenderedMoney(val text: AnnotatedString, val spoken: String)

private fun styled(parts: MoneyParts, base: TextStyle): AnnotatedString = buildAnnotatedString {
    append(parts.sign)
    if (parts.symbol.isNotEmpty()) {
        withStyle(scaled(base, SYMBOL_SCALE)) { append(parts.symbol) }
    }
    append(parts.integer)
    if (parts.fraction.isNotEmpty()) {
        withStyle(scaled(base, MINOR_SCALE).copy(fontWeight = FontWeight.Normal)) { append(parts.fraction) }
    }
    append(parts.code)
}

private fun scaled(base: TextStyle, factor: Float): SpanStyle =
    if (base.fontSize.isSpecified) SpanStyle(fontSize = base.fontSize * factor) else SpanStyle()

/** Screen readers skip or misread U+2212; "minus" is unambiguous. */
private fun spoken(text: String): String = text.replace("−", "minus ")

private const val SYMBOL_SCALE = 0.75f
private const val MINOR_SCALE = 0.8f
private const val HIDDEN_DESCRIPTION = "Amount hidden"
