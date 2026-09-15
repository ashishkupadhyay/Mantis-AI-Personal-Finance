package io.github.ashishkupadhyay.mantis.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

/** Bundled seed colours for devices without dynamic colour, or by user choice (FR-SET-1). */
enum class MantisSeed(val label: String, val seed: Color) {
    MANTIS_GREEN("Mantis Green", Color(0xFF2E7D4F)),
    INDIGO("Indigo", Color(0xFF3F51B5)),
    TEAL("Teal", Color(0xFF00897B)),
    AMBER("Amber", Color(0xFFFFA000)),
    ROSE("Rose", Color(0xFFD81B60)),
    SLATE("Slate", Color(0xFF546E7A)),
}

/**
 * Builds a full Material 3 [ColorScheme] from a seed with the *tonal spot* variant of material-color-utilities —
 * the same variant Android's dynamic colour uses — so bundled seeds keep their hue and share the tonal
 * relationships of wallpaper-derived schemes. (`SchemeExpressive` is a different variant that rotates the
 * primary hue by 240°, which would turn "Mantis Green" brown; it is unrelated to the M3 Expressive design system.)
 */
object SeedColorSchemes {

    private val roles = MaterialDynamicColors()

    fun fromSeed(seed: Color, dark: Boolean, contrastLevel: Double = 0.0): ColorScheme {
        val scheme = SchemeTonalSpot(Hct.fromInt(seed.toArgb()), dark, contrastLevel)
        fun c(argb: Int) = Color(argb)
        return ColorScheme(
            primary = c(roles.primary().getArgb(scheme)),
            onPrimary = c(roles.onPrimary().getArgb(scheme)),
            primaryContainer = c(roles.primaryContainer().getArgb(scheme)),
            onPrimaryContainer = c(roles.onPrimaryContainer().getArgb(scheme)),
            inversePrimary = c(roles.inversePrimary().getArgb(scheme)),
            secondary = c(roles.secondary().getArgb(scheme)),
            onSecondary = c(roles.onSecondary().getArgb(scheme)),
            secondaryContainer = c(roles.secondaryContainer().getArgb(scheme)),
            onSecondaryContainer = c(roles.onSecondaryContainer().getArgb(scheme)),
            tertiary = c(roles.tertiary().getArgb(scheme)),
            onTertiary = c(roles.onTertiary().getArgb(scheme)),
            tertiaryContainer = c(roles.tertiaryContainer().getArgb(scheme)),
            onTertiaryContainer = c(roles.onTertiaryContainer().getArgb(scheme)),
            background = c(roles.background().getArgb(scheme)),
            onBackground = c(roles.onBackground().getArgb(scheme)),
            surface = c(roles.surface().getArgb(scheme)),
            onSurface = c(roles.onSurface().getArgb(scheme)),
            surfaceVariant = c(roles.surfaceVariant().getArgb(scheme)),
            onSurfaceVariant = c(roles.onSurfaceVariant().getArgb(scheme)),
            surfaceTint = c(roles.surfaceTint().getArgb(scheme)),
            inverseSurface = c(roles.inverseSurface().getArgb(scheme)),
            inverseOnSurface = c(roles.inverseOnSurface().getArgb(scheme)),
            error = c(roles.error().getArgb(scheme)),
            onError = c(roles.onError().getArgb(scheme)),
            errorContainer = c(roles.errorContainer().getArgb(scheme)),
            onErrorContainer = c(roles.onErrorContainer().getArgb(scheme)),
            outline = c(roles.outline().getArgb(scheme)),
            outlineVariant = c(roles.outlineVariant().getArgb(scheme)),
            scrim = c(roles.scrim().getArgb(scheme)),
            surfaceBright = c(roles.surfaceBright().getArgb(scheme)),
            surfaceDim = c(roles.surfaceDim().getArgb(scheme)),
            surfaceContainer = c(roles.surfaceContainer().getArgb(scheme)),
            surfaceContainerHigh = c(roles.surfaceContainerHigh().getArgb(scheme)),
            surfaceContainerHighest = c(roles.surfaceContainerHighest().getArgb(scheme)),
            surfaceContainerLow = c(roles.surfaceContainerLow().getArgb(scheme)),
            surfaceContainerLowest = c(roles.surfaceContainerLowest().getArgb(scheme)),
            primaryFixed = c(roles.primaryFixed().getArgb(scheme)),
            primaryFixedDim = c(roles.primaryFixedDim().getArgb(scheme)),
            onPrimaryFixed = c(roles.onPrimaryFixed().getArgb(scheme)),
            onPrimaryFixedVariant = c(roles.onPrimaryFixedVariant().getArgb(scheme)),
            secondaryFixed = c(roles.secondaryFixed().getArgb(scheme)),
            secondaryFixedDim = c(roles.secondaryFixedDim().getArgb(scheme)),
            onSecondaryFixed = c(roles.onSecondaryFixed().getArgb(scheme)),
            onSecondaryFixedVariant = c(roles.onSecondaryFixedVariant().getArgb(scheme)),
            tertiaryFixed = c(roles.tertiaryFixed().getArgb(scheme)),
            tertiaryFixedDim = c(roles.tertiaryFixedDim().getArgb(scheme)),
            onTertiaryFixed = c(roles.onTertiaryFixed().getArgb(scheme)),
            onTertiaryFixedVariant = c(roles.onTertiaryFixedVariant().getArgb(scheme)),
        )
    }
}
