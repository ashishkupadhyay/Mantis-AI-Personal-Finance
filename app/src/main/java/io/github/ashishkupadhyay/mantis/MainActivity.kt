package io.github.ashishkupadhyay.mantis

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.ashishkupadhyay.mantis.benchmark.BenchmarkHooks
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.deeplink.DeepLink
import io.github.ashishkupadhyay.mantis.deeplink.DeepLinkParser
import io.github.ashishkupadhyay.mantis.deeplink.ShareIntentParser
import io.github.ashishkupadhyay.mantis.ui.MantisApp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single activity. Edge-to-edge is enabled before content is set and the navigation-bar contrast scrim is
 * disabled so the navigation suite's own surface reaches the bottom edge (edge-to-edge guidance). Deep links and
 * shared statements arrive through [onNewIntent] because the activity is `singleTask` (notification, assistant
 * and share-sheet taps reuse it).
 * It is a [FragmentActivity] only because `BiometricPrompt` requires one for the App Lock screen.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var installers: Set<@JvmSuppressWildcards EntryProviderInstaller>

    @Inject
    lateinit var deepLinkParser: DeepLinkParser

    @Inject
    lateinit var shareIntentParser: ShareIntentParser

    @Inject
    lateinit var benchmarkHooks: BenchmarkHooks

    private var pendingDeepLink: DeepLink? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // A restored activity keeps its saved back stacks; only a fresh launch applies the launching intent.
        if (savedInstanceState == null) {
            pendingDeepLink = linkFrom(intent)
            benchmarkHooks.requestedMonths(intent)?.let { months -> lifecycleScope.launch { benchmarkHooks.prepare(months) } }
        }

        val entryInstallers = installers.toImmutableList()
        setContent {
            MantisApp(
                installers = entryInstallers,
                pendingDeepLink = pendingDeepLink,
                onDeepLinkConsumed = { pendingDeepLink = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = linkFrom(intent)
    }

    /** A shared CSV opens the import wizard; anything else is treated as a (possibly absent) `mantis://` link. */
    private fun linkFrom(intent: Intent?): DeepLink? = shareIntentParser.parse(intent) ?: deepLinkParser.parse(intent?.dataString)
}
