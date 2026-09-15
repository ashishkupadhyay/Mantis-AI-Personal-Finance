package io.github.ashishkupadhyay.mantis.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.ashishkupadhyay.mantis.core.ui.navigation.EntryProviderInstaller
import io.github.ashishkupadhyay.mantis.ui.placeholder.PlaceholderEntries

/** Shell-owned navigation entries. Features add their own installers to the same set from their modules. */
@Module
@InstallIn(SingletonComponent::class)
object ShellModule {

    @Provides
    @IntoSet
    fun placeholderEntries(): EntryProviderInstaller = PlaceholderEntries
}
