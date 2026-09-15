package io.github.ashishkupadhyay.mantis.core.designsystem

import androidx.compose.ui.test.DarkMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.then
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ashishkupadhyay.mantis.core.designsystem.catalogue.ComponentCatalogue
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.designsystem.theme.MantisSeed
import io.github.ashishkupadhyay.mantis.core.testing.screenshot.MantisScreenshots
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Design-system visual regression (NFR-33, doc 05 §10): the whole catalogue in light, dark, a second seed and
 * 200 % font scale. Record with `gradlew :core:designsystem:recordRoborazziDebug`; CI runs `verifyRoborazziDebug`.
 * Dark mode and font scale are simulated with [DeviceConfigurationOverride] so the theme's `SYSTEM` branch is
 * what gets exercised; a tall virtual display captures the full scrolling catalogue in one image.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h3200dp-normal-long-notround-any-420dpi")
class ComponentCatalogueScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean, seed: MantisSeed = MantisSeed.MANTIS_GREEN, fontScale: Float = 1f) {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.DarkMode(dark) then DeviceConfigurationOverride.FontScale(fontScale)) {
                MantisPreviewTheme(seed = seed) { ComponentCatalogue(animate = false) }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/catalogue_$name.png", roborazziOptions = MantisScreenshots.options)
    }

    @Test
    fun catalogue_light() = capture("light", dark = false)

    @Test
    fun catalogue_dark() = capture("dark", dark = true)

    @Test
    fun catalogue_indigo_light() = capture("indigo_light", dark = false, seed = MantisSeed.INDIGO)

    @Test
    fun catalogue_light_font200() = capture("light_font200", dark = false, fontScale = 2f)
}
