package com.agiletech.android.phototransfer.core.network.integration

import com.agiletech.android.phototransfer.core.network.internal.LocalNetworkOnlyDns
import com.agiletech.android.phototransfer.core.network.internal.LocalNetworkOnlyInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
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
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Uploads are as slow as the photo is large; no write deadline.
        .writeTimeout(0, TimeUnit.SECONDS)
        // Clients derived with newBuilder() inherit both, so the local-only rule cannot be
        // sidestepped by adjusting timeouts for a particular call.
        .dns(LocalNetworkOnlyDns())
        .addInterceptor(LocalNetworkOnlyInterceptor())
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
}
