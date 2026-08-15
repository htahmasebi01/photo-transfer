package com.agiletech.android.phototransfer.core.coroutines.integration

import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal class DispatcherModule {

    @Provides
    fun provideDispatchers() = Dispatchers(
        main = kotlinx.coroutines.Dispatchers.Main,
        io = kotlinx.coroutines.Dispatchers.IO,
        default = kotlinx.coroutines.Dispatchers.Default,
    )
}
