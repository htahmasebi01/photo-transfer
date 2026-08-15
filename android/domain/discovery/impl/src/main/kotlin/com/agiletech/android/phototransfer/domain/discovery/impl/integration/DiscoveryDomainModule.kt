package com.agiletech.android.phototransfer.domain.discovery.impl.integration

import com.agiletech.android.phototransfer.domain.discovery.ObserveReceivers
import com.agiletech.android.phototransfer.domain.discovery.impl.internal.DefaultObserveReceivers
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DiscoveryDomainModule {

    @Binds
    fun bindObserveReceivers(impl: DefaultObserveReceivers): ObserveReceivers
}
