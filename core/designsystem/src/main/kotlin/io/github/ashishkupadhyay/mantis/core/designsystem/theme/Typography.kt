package io.github.ashishkupadhyay.mantis.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.ashishkupadhyay.mantis.core.designsystem.R

/**
 * Bundled variable fonts (OFL, see FONTS-LICENSE.txt): Manrope for display/headline, Inter for body/label
 * (doc 05 §2.3). Bundling avoids the Google Fonts provider, so typography is identical on non-GMS devices and
 * in screenshot tests.
 */
private fun variableFamily(resId: Int, weights: List<FontWeight>): FontFamily = FontFamily(
    weights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

private val displayWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)
private val bodyWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

val ManropeFamily: FontFamily = variableFamily(R.font.manrope_variable, displayWeights)
val InterFamily: FontFamily = variableFamily(R.font.inter_variable, bodyWeights)

/** Tabular figures so digits line up in lists and tickers (money, dates). */
const val TABULAR_FIGURES = "tnum"

private fun TextStyle.display() = copy(fontFamily = ManropeFamily)
private fun TextStyle.body() = copy(fontFamily = InterFamily)

/** Material 3 defaults (including the Expressive *Emphasized* styles) with Mantis families applied. */
val MantisTypography: Typography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.display(),
        displayMedium = base.displayMedium.display(),
        displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.display(),
        titleMedium = base.titleMedium.body(),
        titleSmall = base.titleSmall.body(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(),
        labelMedium = base.labelMedium.body(),
        labelSmall = base.labelSmall.body(),
        displayLargeEmphasized = base.displayLargeEmphasized.display(),
        displayMediumEmphasized = base.displayMediumEmphasized.display(),
        displaySmallEmphasized = base.displaySmallEmphasized.display(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.display(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.display(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.display(),
        titleLargeEmphasized = base.titleLargeEmphasized.display(),
        titleMediumEmphasized = base.titleMediumEmphasized.body(),
        titleSmallEmphasized = base.titleSmallEmphasized.body(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.body(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.body(),
        bodySmallEmphasized = base.bodySmallEmphasized.body(),
        labelLargeEmphasized = base.labelLargeEmphasized.body(),
        labelMediumEmphasized = base.labelMediumEmphasized.body(),
        labelSmallEmphasized = base.labelSmallEmphasized.body(),
    )
}
