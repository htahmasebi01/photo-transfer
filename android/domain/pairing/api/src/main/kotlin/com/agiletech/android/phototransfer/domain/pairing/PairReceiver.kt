package com.agiletech.android.phototransfer.domain.pairing

import com.agiletech.android.phototransfer.core.model.ReceiverDevice

/** Exchanges the code shown on the receiver for permission to send it photos. */
interface PairReceiver {

    /**
     * [replaceExisting] may only be true in response to an explicit user decision, because
     * replacing a pairing hands a new device everything the old one could do.
     */
    suspend operator fun invoke(
        receiver: ReceiverDevice,
        code: String,
        replaceExisting: Boolean = false,
    ): PairingResult
}

sealed interface PairingResult {

    data class Paired(val receiverName: String) : PairingResult

    data object WrongCode : PairingResult

    data object Declined : PairingResult

    data object TimedOut : PairingResult

    /** Too many attempts reached the receiver recently. The displayed code still works. */
    data object Throttled : PairingResult

    /** Needs confirmation before the stored pairing for [receiverName] is replaced. */
    data class ReplacesExistingPairing(val receiverName: String) : PairingResult

    /** The responder tried to issue credentials for a receiver other than the one asked. */
    data object WrongReceiver : PairingResult

    data class Failed(val reason: String) : PairingResult
}
