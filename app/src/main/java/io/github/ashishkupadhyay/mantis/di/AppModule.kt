package io.github.ashishkupadhyay.mantis.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ashishkupadhyay.mantis.core.common.coroutines.DefaultDispatcherProvider
import io.github.ashishkupadhyay.mantis.core.common.coroutines.DispatcherProvider
import io.github.ashishkupadhyay.mantis.core.common.id.UuidV7
import io.github.ashishkupadhyay.mantis.core.common.log.Logger
import io.github.ashishkupadhyay.mantis.core.common.log.NoOpLogger
import io.github.ashishkupadhyay.mantis.core.common.log.PrintLogger
import io.github.ashishkupadhyay.mantis.core.common.time.Clock
import io.github.ashishkupadhyay.mantis.core.common.time.SystemClock
import io.github.ashishkupadhyay.mantis.core.domain.repository.DevelopmentGate
import io.github.ashishkupadhyay.mantis.R
import javax.inject.Singleton

/** App-wide primitives from `core:common` (which has no Hilt dependency of its own). */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun clock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun ids(): UuidV7 = UuidV7()

    @Provides
    @Singleton
    fun dispatchers(): DispatcherProvider = DefaultDispatcherProvider()

    /** Debuggable builds print to stdout (which Android routes to logcat); release discards everything (NFR-17). */
    @Provides
    @Singleton
    fun logger(@ApplicationContext context: Context): Logger = if (context.isDebuggable) PrintLogger() else NoOpLogger

    /** Developer tools: debuggable builds, and the `benchmark` build type (its resources flip the flag) — never release. */
    @Provides
    @Singleton
    fun developmentGate(@ApplicationContext context: Context): DevelopmentGate = object : DevelopmentGate {
        override val toolsEnabled: Boolean = context.isDebuggable || context.resources.getBoolean(R.bool.mantis_benchmark_hooks)
    }

    private val Context.isDebuggable: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
