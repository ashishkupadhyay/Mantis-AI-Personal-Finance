package io.github.ashishkupadhyay.mantis.ui.lock

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.ashishkupadhyay.mantis.core.designsystem.preview.MantisPreviewTheme
import io.github.ashishkupadhyay.mantis.core.domain.security.LockState
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** NFR-20h: while locked, the content lambda is never composed — not hidden, not composed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLockGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun NFR_20h_contentIsNotComposedWhileLocked() {
        var lockState by mutableStateOf(LockState.LOCKED)
        var compositions = 0
        composeRule.setContent {
            MantisPreviewTheme {
                AppLockGate(state = lockState, onUnlocked = {}) {
                    compositions++
                    Text("Secret balance")
                }
            }
        }

        composeRule.onNodeWithText("Mantis is locked").assertIsDisplayed()
        composeRule.onNodeWithText("Secret balance").assertDoesNotExist()
        compositions shouldBe 0

        lockState = LockState.UNLOCKED
        composeRule.onNodeWithText("Secret balance").assertIsDisplayed()
        composeRule.onNodeWithText("Mantis is locked").assertDoesNotExist()

        lockState = LockState.LOCKED
        composeRule.onNodeWithText("Secret balance").assertDoesNotExist()
    }

    @Test
    fun disabledLockShowsContentDirectly() {
        composeRule.setContent {
            MantisPreviewTheme { AppLockGate(state = LockState.DISABLED, onUnlocked = {}) { Text("Content") } }
        }

        composeRule.onNodeWithText("Content").assertIsDisplayed()
    }
}
