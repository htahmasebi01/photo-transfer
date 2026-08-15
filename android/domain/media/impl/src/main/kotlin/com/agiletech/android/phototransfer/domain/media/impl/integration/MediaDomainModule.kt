package com.agiletech.android.phototransfer.domain.media.impl.integration

import com.agiletech.android.phototransfer.domain.media.ResolveSelectedPhotos
import com.agiletech.android.phototransfer.domain.media.impl.internal.DefaultResolveSelectedPhotos
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface MediaDomainModule {

    @Binds
    fun bindResolveSelectedPhotos(impl: DefaultResolveSelectedPhotos): ResolveSelectedPhotos
}
