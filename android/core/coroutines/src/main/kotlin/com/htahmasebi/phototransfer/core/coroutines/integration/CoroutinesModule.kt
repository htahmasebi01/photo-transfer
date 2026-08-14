package com.htahmasebi.phototransfer.core.coroutines.integration

import com.htahmasebi.phototransfer.core.coroutines.DispatcherProvider
import com.htahmasebi.phototransfer.core.coroutines.internal.DefaultDispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface CoroutinesModule {

    @Binds
    @Singleton
    fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}
