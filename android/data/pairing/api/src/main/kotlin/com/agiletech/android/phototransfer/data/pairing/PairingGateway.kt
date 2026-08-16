package com.agiletech.android.phototransfer.data.pairing

import com.agiletech.android.phototransfer.core.model.ReceiverDevice

/** Exchanges a receiver-displayed code for credentials this device can sign requests with. */
interface PairingGateway {

    /**
     * Pairs with [receiver], storing nothing unless the exchange is accepted.
     *
     * [replaceExisting] must be set by a caller acting on an explicit user decision. A
     * stored pairing is the only thing standing between this device and an impostor, so
     * replacing one silently would undo every later check.
     */
    suspend fun pair(
        receiver: ReceiverDevice,
        pairingCode: String,
        replaceExisting: Boolean = false,
    ): PairingOutcome
}

sealed interface PairingOutcome {

    data class Paired(val receiverId: String, val receiverName: String) : PairingOutcome

    data object InvalidCode : PairingOutcome

    data object Declined : PairingOutcome

    data object TimedOut : PairingOutcome

    /** The receiver is rate limiting pairing attempts. The code on screen is still good. */
    data object Throttled : PairingOutcome

    /**
     * This device already holds a pairing for that receiver, and nothing was written.
     *
     * Retry with `replaceExisting = true` once the user has confirmed.
     */
    data class AlreadyPaired(val receiverId: String, val receiverName: String) : PairingOutcome

    /**
     * The responder issued credentials under a different `receiverId` than the one being
     * paired with, so it was trying to overwrite a pairing that is not its own.
     */
    data class IdentityMismatch(val expected: String, val claimed: String) : PairingOutcome

    data class Failed(val reason: String) : PairingOutcome
}
