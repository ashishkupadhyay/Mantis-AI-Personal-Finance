package io.github.ashishkupadhyay.mantis.ui.shell

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * `enableEdgeToEdge()` decides the system-bar icon colours from the *system* dark-mode setting; when the user
 * overrides the theme in-app (FR-SET-1) the icons must follow the app instead. Re-applies the styles whenever the
 * resolved theme changes. Scrims match the defaults `enableEdgeToEdge` uses on API < 29 three-button navigation.
 */
@Composable
fun SystemBarsFollowTheme(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
    }
}

private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
