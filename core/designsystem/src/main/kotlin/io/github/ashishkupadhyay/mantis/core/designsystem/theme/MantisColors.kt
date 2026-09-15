package io.github.ashishkupadhyay.mantis.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.blend.Blend

/** Semantic colour roles beyond Material's (doc 05 §2.2). Restrained on purpose: expenses are neutral, not red. */
@Immutable
data class MantisColors(
    val paceOnTrack: Color,
    val onPaceOnTrack: Color,
    val paceWatch: Color,
    val onPaceWatch: Color,
    val paceOver: Color,
    val onPaceOver: Color,
    val income: Color,
    val expense: Color,
    val confidenceHigh: Color,
    val confidenceMedium: Color,
    val confidenceLow: Color,
    /** 12 category hues harmonised to the current scheme so charts never clash with the wallpaper. */
    val categoryPalette: List<Color>,
) {
    /** Stable colour for a category/merchant/tag key: same key → same slot, across screens and sessions. */
    fun forKey(key: String): Color = categoryPalette[(key.hashCode() and Int.MAX_VALUE) % categoryPalette.size]
}

val LocalMantisColors = staticCompositionLocalOf<MantisColors> { error("MantisTheme not applied") }

internal object MantisColorTokens {
    private val baseHues = listOf(
        0xFF1B6E3A, 0xFF1565C0, 0xFF6A1B9A, 0xFFEF6C00, 0xFF00838F, 0xFFC62828,
        0xFF5D4037, 0xFF2E7D32, 0xFF283593, 0xFFAD1457, 0xFF00695C, 0xFF9E9D24,
    )
    private const val WATCH_LIGHT = 0xFFB26A00
    private const val WATCH_DARK = 0xFFFFB95C
    private const val INCOME_LIGHT = 0xFF1B6E3A
    private const val INCOME_DARK = 0xFF6FD08C
    private const val WATCH_ON_LIGHT = 0xFFFFFFFF
    private const val WATCH_ON_DARK = 0xFF3A2600

    fun from(scheme: ColorScheme, dark: Boolean): MantisColors {
        val source = scheme.primary.toArgb()
        fun harmonized(argb: Long) = Color(Blend.harmonize(argb.toInt(), source))
        return MantisColors(
            paceOnTrack = scheme.tertiaryContainer,
            onPaceOnTrack = scheme.onTertiaryContainer,
            paceWatch = harmonized(if (dark) WATCH_DARK else WATCH_LIGHT),
            onPaceWatch = Color(if (dark) WATCH_ON_DARK else WATCH_ON_LIGHT),
            paceOver = scheme.error,
            onPaceOver = scheme.onError,
            income = harmonized(if (dark) INCOME_DARK else INCOME_LIGHT),
            expense = scheme.onSurface,
            confidenceHigh = scheme.primary,
            confidenceMedium = scheme.outline,
            confidenceLow = scheme.outlineVariant,
            categoryPalette = baseHues.map { harmonized(it) },
        )
    }
}
