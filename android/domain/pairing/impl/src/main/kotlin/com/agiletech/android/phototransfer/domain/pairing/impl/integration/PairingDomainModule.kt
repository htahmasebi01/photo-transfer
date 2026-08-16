package com.agiletech.android.phototransfer.domain.pairing.impl.integration

import com.agiletech.android.phototransfer.domain.pairing.ForgetPairing
import com.agiletech.android.phototransfer.domain.pairing.IsReceiverPaired
import com.agiletech.android.phototransfer.domain.pairing.PairReceiver
import com.agiletech.android.phototransfer.domain.pairing.impl.internal.DefaultForgetPairing
import com.agiletech.android.phototransfer.domain.pairing.impl.internal.DefaultIsReceiverPaired
import com.agiletech.android.phototransfer.domain.pairing.impl.internal.DefaultPairReceiver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface PairingDomainModule {

    @Binds
    fun bindPairReceiver(impl: DefaultPairReceiver): PairReceiver

    @Binds
    fun bindIsReceiverPaired(impl: DefaultIsReceiverPaired): IsReceiverPaired

    @Binds
    fun bindForgetPairing(impl: DefaultForgetPairing): ForgetPairing
}
