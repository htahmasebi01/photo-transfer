package com.htahmasebi.phototransfer.domain.transfer.impl.integration

import com.htahmasebi.phototransfer.core.coroutines.DispatcherProvider
import com.htahmasebi.phototransfer.domain.transfer.TransferCoordinator
import com.htahmasebi.phototransfer.domain.transfer.impl.internal.DefaultTransferCoordinator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal interface TransferDomainModule {

    @Binds
    @Singleton
    fun bindTransferCoordinator(impl: DefaultTransferCoordinator): TransferCoordinator
}

@Module
@InstallIn(SingletonComponent::class)
internal object TransferSessionScopeModule {

    /**
     * Process-lifetime scope: a transfer must outlive the screen that started it.
     * Not qualified because this module is the only binding for [CoroutineScope].
     */
    @Provides
    @Singleton
    fun provideTransferScope(dispatchers: DispatcherProvider): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.default)
}
