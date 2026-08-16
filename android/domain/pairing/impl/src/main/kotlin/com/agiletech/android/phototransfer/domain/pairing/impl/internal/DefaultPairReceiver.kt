package com.agiletech.android.phototransfer.domain.pairing.impl.internal

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.data.pairing.PairedReceiverStore
import com.agiletech.android.phototransfer.data.pairing.PairingGateway
import com.agiletech.android.phototransfer.data.pairing.PairingOutcome
import com.agiletech.android.phototransfer.domain.pairing.ForgetPairing
import com.agiletech.android.phototransfer.domain.pairing.IsReceiverPaired
import com.agiletech.android.phototransfer.domain.pairing.PairReceiver
import com.agiletech.android.phototransfer.domain.pairing.PairingResult
import javax.inject.Inject

internal class DefaultPairReceiver @Inject constructor(
    private val gateway: PairingGateway,
) : PairReceiver {

    override suspend fun invoke(
        receiver: ReceiverDevice,
        code: String,
        replaceExisting: Boolean,
    ): PairingResult =
        when (val outcome = gateway.pair(receiver, code, replaceExisting)) {
            is PairingOutcome.Paired -> PairingResult.Paired(outcome.receiverName)
            PairingOutcome.InvalidCode -> PairingResult.WrongCode
            PairingOutcome.Declined -> PairingResult.Declined
            PairingOutcome.TimedOut -> PairingResult.TimedOut
            PairingOutcome.Throttled -> PairingResult.Throttled
            is PairingOutcome.AlreadyPaired ->
                PairingResult.ReplacesExistingPairing(outcome.receiverName)
            is PairingOutcome.IdentityMismatch -> PairingResult.WrongReceiver
            is PairingOutcome.Failed -> PairingResult.Failed(outcome.reason)
        }
}

internal class DefaultIsReceiverPaired @Inject constructor(
    private val store: PairedReceiverStore,
) : IsReceiverPaired {

    override suspend fun invoke(receiver: ReceiverDevice): Boolean {
        val receiverId = receiver.receiverId ?: return false
        return store.isPaired(receiverId)
    }
}

internal class DefaultForgetPairing @Inject constructor(
    private val store: PairedReceiverStore,
) : ForgetPairing {

    override suspend fun invoke(receiver: ReceiverDevice) {
        receiver.receiverId?.let { store.forget(it) }
    }
}
