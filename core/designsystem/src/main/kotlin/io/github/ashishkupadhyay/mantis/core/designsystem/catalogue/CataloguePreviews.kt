package io.github.ashishkupadhyay.mantis.core.designsystem.catalogue

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.PreviewMantis

// One preview per catalogue section (light / dark / 1.3x / 2.0x font), plus the whole catalogue across screen sizes.
// Previews are private by convention (Compose lint `PreviewPublic`) and render only in Android Studio.

@PreviewMantis
@Composable
private fun ColourRolesPreview() = MantisPreviewTheme { Padded { CatalogueColourRoles() } }

@PreviewMantis
@Composable
private fun TypographyPreview() = MantisPreviewTheme { Padded { CatalogueTypography() } }

@PreviewMantis
@Composable
private fun AppBarsPreview() = MantisPreviewTheme { Padded { CatalogueAppBars() } }

@PreviewMantis
@Composable
private fun ButtonsPreview() = MantisPreviewTheme { Padded { CatalogueButtons() } }

@PreviewMantis
@Composable
private fun BadgesPreview() = MantisPreviewTheme { Padded { CatalogueBadges() } }

@PreviewMantis
@Composable
private fun LoadingPreview() = MantisPreviewTheme { Padded { CatalogueLoading() } }

@PreviewMantis
@Composable
private fun ToolbarsPreview() = MantisPreviewTheme { Padded { CatalogueToolbars() } }

@PreviewMantis
@Composable
private fun ChartsPreview() = MantisPreviewTheme { Padded { CatalogueCharts() } }

@PreviewMantis
@Composable
private fun StatesPreview() = MantisPreviewTheme { Padded { CatalogueStates() } }

@PreviewScreenSizes
@PreviewLightDark
@Composable
private fun ComponentCataloguePreview() = MantisPreviewTheme { ComponentCatalogue(animate = false) }

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(16.dp)) { content() }
}
