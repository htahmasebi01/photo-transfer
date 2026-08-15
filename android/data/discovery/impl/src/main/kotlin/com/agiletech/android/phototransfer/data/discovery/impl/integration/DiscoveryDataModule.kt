package com.agiletech.android.phototransfer.data.discovery.impl.integration

import android.content.Context
import android.net.nsd.NsdManager
import com.agiletech.android.phototransfer.data.discovery.ReceiverDiscoveryDataSource
import com.agiletech.android.phototransfer.data.discovery.impl.internal.NsdReceiverDiscoveryDataSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DiscoveryDataModule {

    @Binds
    @Singleton
    fun bindReceiverDiscoveryDataSource(
        impl: NsdReceiverDiscoveryDataSource,
    ): ReceiverDiscoveryDataSource
}

@Module
@InstallIn(SingletonComponent::class)
internal object NsdManagerModule {

    @Provides
    fun provideNsdManager(@ApplicationContext context: Context): NsdManager =
        context.getSystemService(NsdManager::class.java)
}
