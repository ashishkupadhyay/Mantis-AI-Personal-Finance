package io.github.ashishkupadhyay.mantis.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ashishkupadhyay.mantis.core.domain.repository.PreferencesRepository
import io.github.ashishkupadhyay.mantis.core.domain.security.AppLockManager
import io.github.ashishkupadhyay.mantis.core.domain.security.DeviceLockAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds the process-scoped [AppLockManager] (doc 02 §9): App Lock preferences as they change, and foreground /
 * background transitions from [ProcessLifecycleOwner]. Started once from the Application.
 */
@Singleton
class AppLockCoordinator @Inject constructor(
    private val appLock: AppLockManager,
    private val preferences: PreferencesRepository,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            preferences.preferences.map { it.appLock }.distinctUntilChanged().collect(appLock::applyPreferences)
        }
    }

    override fun onStart(owner: LifecycleOwner) = appLock.onForeground()

    override fun onStop(owner: LifecycleOwner) = appLock.onBackground()
}

/** BIOMETRIC_STRONG or device credential must be enrolled for App Lock to be offered (FR-ONB-6). */
@Singleton
class BiometricLockAvailability @Inject constructor(@ApplicationContext private val context: Context) : DeviceLockAvailability {
    override fun canAuthenticate(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds abstract fun lockAvailability(impl: BiometricLockAvailability): DeviceLockAvailability
}
