package io.github.ashishkupadhyay.mantis.ui.lock

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.ashishkupadhyay.mantis.R
import io.github.ashishkupadhyay.mantis.core.domain.security.LockState
import io.github.ashishkupadhyay.mantis.core.ui.security.SecureWindow

/**
 * The App Lock slot above every screen (doc 02 §9, NFR-20h): while locked, [content] is *not composed* — nothing
 * below the gate exists to be captured, read by an accessibility service or restored early after process death.
 */
@Composable
fun AppLockGate(state: LockState, onUnlocked: () -> Unit, content: @Composable () -> Unit) {
    when (state) {
        LockState.DISABLED, LockState.UNLOCKED -> content()
        LockState.LOCKED -> LockScreen(onUnlocked = onUnlocked)
    }
}

/** Launches the system prompt immediately and offers a button to retry after a cancel or failure (FR-ONB-6). */
@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    SecureWindow(secure = true)
    val activity = LocalActivity.current as? FragmentActivity
    var promptedOnce by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)

    fun prompt() {
        val host = activity ?: return
        error = null
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                error = errString.toString()
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        BiometricPrompt(host, ContextCompat.getMainExecutor(host), callback).authenticate(info)
    }

    LaunchedEffect(activity) {
        if (!promptedOnce && activity != null) {
            promptedOnce = true
            prompt()
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(onClick = ::prompt, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.lock_unlock)) }
            }
        }
    }
}
