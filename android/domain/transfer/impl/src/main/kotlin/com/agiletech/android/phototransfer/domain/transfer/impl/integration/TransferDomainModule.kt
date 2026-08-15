package com.agiletech.android.phototransfer.domain.transfer.impl.integration

import com.agiletech.android.phototransfer.domain.transfer.TransferCoordinator
import com.agiletech.android.phototransfer.domain.transfer.impl.internal.DefaultTransferCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TransferDomainModule {

    @Binds
    @Singleton
    fun bindTransferCoordinator(impl: DefaultTransferCoordinator): TransferCoordinator
}
