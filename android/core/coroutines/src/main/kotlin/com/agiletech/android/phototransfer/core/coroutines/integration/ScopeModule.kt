package com.agiletech.android.phototransfer.core.coroutines.integration

import com.agiletech.android.phototransfer.core.coroutines.scopes.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal class ScopeModule {

    /** Singleton so every injection point shares one cancellation boundary. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())
}
