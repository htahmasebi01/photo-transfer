package com.agiletech.android.phototransfer.data.media.impl.integration

import android.content.ContentResolver
import android.content.Context
import com.agiletech.android.phototransfer.data.media.MediaByteSource
import com.agiletech.android.phototransfer.data.media.MediaMetadataSource
import com.agiletech.android.phototransfer.data.media.impl.internal.ContentResolverMediaSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface MediaDataModule {

    @Binds
    @Singleton
    fun bindMediaMetadataSource(impl: ContentResolverMediaSource): MediaMetadataSource

    @Binds
    @Singleton
    fun bindMediaByteSource(impl: ContentResolverMediaSource): MediaByteSource
}

@Module
@InstallIn(SingletonComponent::class)
internal object ContentResolverModule {

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver
}
