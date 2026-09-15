package io.github.ashishkupadhyay.mantis.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.ColorSource
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.MantisSeed
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.MantisTheme

/**
 * The preview set every component carries (doc 06 WP-0.3): light, dark, and the two font scales that break
 * layouts first — 1.3× is the common "Large" setting, 2.0× the accessibility maximum (NFR-25).
 */
@Preview(name = "Light", group = "theme")
@Preview(name = "Dark", group = "theme", uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Font 1.3x", group = "font scale", fontScale = 1.3f)
@Preview(name = "Font 2.0x", group = "font scale", fontScale = 2f)
annotation class PreviewMantis

/**
 * Theme wrapper for previews and screenshot tests: a bundled seed (the preview renderer has no wallpaper to derive
 * dynamic colour from) on the theme's background surface. Motion is irrelevant in a still render.
 */
@Composable
fun MantisPreviewTheme(
    modifier: Modifier = Modifier,
    seed: MantisSeed = MantisSeed.MANTIS_GREEN,
    content: @Composable () -> Unit,
) {
    MantisTheme(colorSource = ColorSource.Seed(seed), reducedMotion = true) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.background, content = content)
    }
}
