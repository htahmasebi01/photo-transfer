package com.agiletech.android.phototransfer.data.transfer.impl.integration

import com.agiletech.android.phototransfer.data.transfer.TransferGateway
import com.agiletech.android.phototransfer.data.transfer.impl.internal.HttpTransferGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TransferDataModule {

    @Binds
    @Singleton
    fun bindTransferGateway(impl: HttpTransferGateway): TransferGateway
}
