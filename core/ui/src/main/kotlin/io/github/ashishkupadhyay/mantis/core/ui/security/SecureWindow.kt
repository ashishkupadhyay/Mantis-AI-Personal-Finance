package io.github.ashishkupadhyay.mantis.core.ui.security

import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.Role

/**
 * Sets `FLAG_SECURE` on the activity window while [secure] is true (FR-ONB-7): no screenshots, no screen
 * recording, and a blank thumbnail in the recents switcher. Cleared when the composable leaves composition.
 */
@Composable
fun SecureWindow(secure: Boolean) {
    val window = LocalActivity.current?.window
    DisposableEffect(window, secure) {
        if (window != null && secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * `clickable` that ignores touches delivered while another window overlays this one — the Compose equivalent of
 * `filterTouchesWhenObscured` (NFR-20g tapjacking). Use on key entry, destructive confirmations and proposal
 * confirmations.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.secureClickable(enabled: Boolean = true, role: Role? = Role.Button, onClick: () -> Unit): Modifier =
    pointerInteropFilter { event -> event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0 }
        .clickable(enabled = enabled, role = role, onClick = onClick)
