package io.github.ashishkupadhyay.mantis.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ashishkupadhyay.mantis.core.domain.security.LockState
import io.github.ashishkupadhyay.mantis.core.model.preferences.ColorSourcePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.ThemePreference
import io.github.ashishkupadhyay.mantis.core.model.preferences.UserPreferences
import io.github.ashishkupadhyay.mantis.core.testing.screenshot.MantisScreenshots
import io.github.ashishkupadhyay.mantis.ui.placeholder.PlaceholderEntries
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shell at the two window classes that change its structure: a bottom navigation bar on a phone and a rail on
 * a tablet (doc 02 §4.2). Record with `gradlew :app:recordRoborazziDebug`; CI runs `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MantisAppScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val onboarded = MainUiState(
        preferences = UserPreferences(onboardingCompleted = true, theme = ThemePreference.LIGHT, colorSource = ColorSourcePreference.SEED),
        lockState = LockState.DISABLED,
        loaded = true,
    )

    private fun capture(name: String) {
        composeRule.setContent {
            MantisAppContent(
                state = onboarded,
                installers = persistentListOf(PlaceholderEntries),
                pendingDeepLink = null,
                onDeepLinkConsumed = {},
                onUnlocked = {},
            )
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/shell_$name.png", roborazziOptions = MantisScreenshots.options)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-normal-long-notround-any-420dpi")
    fun shell_compact() = capture("compact")

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h800dp-normal-notlong-notround-any-320dpi")
    fun shell_expanded() = capture("expanded")
}
