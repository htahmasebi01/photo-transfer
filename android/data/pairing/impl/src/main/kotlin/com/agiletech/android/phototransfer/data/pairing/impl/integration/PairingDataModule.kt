package com.agiletech.android.phototransfer.data.pairing.impl.integration

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.agiletech.android.phototransfer.data.pairing.PairedReceiverStore
import com.agiletech.android.phototransfer.data.pairing.PairingGateway
import com.agiletech.android.phototransfer.data.pairing.RequestSigner
import com.agiletech.android.phototransfer.data.pairing.impl.internal.AndroidPairingLocalStore
import com.agiletech.android.phototransfer.data.pairing.impl.internal.HmacRequestSigner
import com.agiletech.android.phototransfer.data.pairing.impl.internal.HttpPairingGateway
import com.agiletech.android.phototransfer.data.pairing.impl.internal.LocalPairedReceiverStore
import com.agiletech.android.phototransfer.data.pairing.impl.internal.PairingDeviceName
import com.agiletech.android.phototransfer.data.pairing.impl.internal.PairingLocalStore
import com.agiletech.android.phototransfer.data.pairing.impl.internal.SigningClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface PairingDataModule {

    @Binds
    @Singleton
    fun bindPairingLocalStore(impl: AndroidPairingLocalStore): PairingLocalStore

    @Binds
    @Singleton
    fun bindPairingGateway(impl: HttpPairingGateway): PairingGateway

    @Binds
    @Singleton
    fun bindRequestSigner(impl: HmacRequestSigner): RequestSigner

    @Binds
    @Singleton
    fun bindPairedReceiverStore(impl: LocalPairedReceiverStore): PairedReceiverStore
}

@Module
@InstallIn(SingletonComponent::class)
internal object PairingPlatformModule {

    @Provides
    @Singleton
    fun providePairingPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("pairing", Context.MODE_PRIVATE)

    @Provides
    fun providePairingDeviceName(): PairingDeviceName =
        PairingDeviceName("${Build.MANUFACTURER} ${Build.MODEL}".trim())

    @Provides
    fun provideSigningClock(): SigningClock = SigningClock { System.currentTimeMillis() / 1000 }
}
