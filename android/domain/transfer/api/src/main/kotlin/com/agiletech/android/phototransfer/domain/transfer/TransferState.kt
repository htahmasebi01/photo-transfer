package com.agiletech.android.phototransfer.domain.transfer

import com.agiletech.android.phototransfer.core.model.ReceiverDevice

sealed interface TransferState {

    data object Idle : TransferState

    /**
     * The receiver will not accept photos until it has been paired.
     *
     * [receiver] always carries a resolved `receiverId`, so pairing can proceed from here.
     */
    data class PairingRequired(val receiver: ReceiverDevice) : TransferState

    /**
     * Something answered for a receiver this device is paired with, but could not prove it
     * holds the pairing secret. No photos were sent.
     *
     * Distinct from [Failed] because retrying cannot help and the likely cause is another
     * device on the network impersonating the receiver.
     */
    data class ReceiverUnverified(val receiver: ReceiverDevice) : TransferState

    data class Transferring(
        val receiver: ReceiverDevice,
        val completedBytes: Long,
        val totalBytes: Long,
        val currentFileName: String,
        val completedFiles: Int,
        val totalFiles: Int,
    ) : TransferState

    data class Completed(
        val transferredFiles: Int,
    ) : TransferState

    data class Failed(
        val reason: String,
        val retryable: Boolean,
    ) : TransferState
}
