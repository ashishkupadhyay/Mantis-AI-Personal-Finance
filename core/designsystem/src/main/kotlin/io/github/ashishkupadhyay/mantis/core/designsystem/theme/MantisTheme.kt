package io.github.ashishkupadhyay.mantis.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where the colour scheme comes from (FR-SET-1). */
@Immutable
sealed interface ColorSource {
    /** Android 12+ wallpaper colours; falls back to [MantisSeed.MANTIS_GREEN] below API 31. */
    data object Dynamic : ColorSource
    data class Seed(val seed: MantisSeed) : ColorSource
}

/** True when the user (or the OS animator scale) asked for reduced motion (FR-SET-5). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** "Hide amounts" privacy toggle (FR-PRV-6): money renders as a mask everywhere. */
val LocalHideAmounts = staticCompositionLocalOf { false }

/**
 * The Mantis theme: Material 3 Expressive with dynamic or seed colour, expressive motion (or standard when
 * motion is reduced), the Mantis shape scale and typography, plus the semantic [MantisColors] (doc 05 §2.1).
 */
@Composable
fun MantisTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorSource: ColorSource = ColorSource.Dynamic,
    amoledBlack: Boolean = false,
    reducedMotion: Boolean = false,
    hideAmounts: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = remember(colorSource, dark, amoledBlack, context) {
        val base = when {
            colorSource is ColorSource.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            colorSource is ColorSource.Seed -> SeedColorSchemes.fromSeed(colorSource.seed.seed, dark)
            else -> SeedColorSchemes.fromSeed(MantisSeed.MANTIS_GREEN.seed, dark)
        }
        if (dark && amoledBlack) base.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else base
    }
    val mantisColors = remember(scheme, dark) { MantisColorTokens.from(scheme, dark) }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = if (reducedMotion) MotionScheme.standard() else MotionScheme.expressive(),
        shapes = MantisShapes,
        typography = MantisTypography,
    ) {
        CompositionLocalProvider(
            LocalMantisColors provides mantisColors,
            LocalReducedMotion provides reducedMotion,
            LocalHideAmounts provides hideAmounts,
            content = content,
        )
    }
}

/** Access point mirroring `MaterialTheme`: `MantisTheme.colors.paceOver`. */
object MantisThemeTokens {
    val colors: MantisColors
        @Composable @ReadOnlyComposable get() = LocalMantisColors.current
    val material: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
}
