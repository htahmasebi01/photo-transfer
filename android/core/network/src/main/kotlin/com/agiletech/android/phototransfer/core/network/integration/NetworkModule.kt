package com.agiletech.android.phototransfer.core.network.integration

import com.agiletech.android.phototransfer.core.network.internal.LocalReceiverHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * One HTTP client and JSON format for every receiver call, so connection pools and
 * timeouts are configured in a single place.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = LocalReceiverHttpClient.create()

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }
}
