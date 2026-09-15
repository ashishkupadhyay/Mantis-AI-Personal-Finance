package io.github.ashishkupadhyay.mantis

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.ashishkupadhyay.mantis.security.AppLockCoordinator
import javax.inject.Inject

@HiltAndroidApp
class MantisApplication : Application() {

    @Inject
    lateinit var appLockCoordinator: AppLockCoordinator

    override fun onCreate() {
        super.onCreate()
        appLockCoordinator.start()
    }
}
