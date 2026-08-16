package com.agiletech.android.phototransfer.data.transfer

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile

/**
 * Moves photos to a receiver. The wire format (endpoints, manifest shape, file
 * ids) is an implementation detail and never crosses this boundary.
 */
interface TransferGateway {

    suspend fun fetchReceiverInfo(receiver: ReceiverDevice): ReceiverInfo

    /**
     * Checks that [receiver] holds the secret this device paired with, before anything
     * is sent to it.
     *
     * @throws ReceiverNotVerifiedException when it cannot prove that, which means the
     * address is answering for a `receiverId` it does not own.
     */
    suspend fun verifyReceiver(receiver: ReceiverDevice)

    /** Registers [files] with the receiver and returns the uploads to perform. */
    suspend fun createTransfer(receiver: ReceiverDevice, files: List<SelectedFile>): TransferHandle

    suspend fun uploadFile(
        receiver: ReceiverDevice,
        handle: TransferHandle,
        upload: PendingUpload,
        onBytesSent: (Long) -> Unit,
    )

    /** @return the number of files the receiver confirmed. */
    suspend fun completeTransfer(receiver: ReceiverDevice, handle: TransferHandle): Int
}

data class ReceiverInfo(
    val protocolVersion: Int,
    val receiverId: String,
    val name: String,
)

/** Raised when the receiver refuses a request because this device is not paired with it. */
class NotPairedException(message: String) : Exception(message)

/**
 * Raised when whatever answered could not prove it is the receiver this device paired with.
 *
 * Treat this as an impersonation attempt rather than a transient error: a `receiverId` is
 * broadcast in cleartext, so anything on the network can claim one it does not own.
 */
class ReceiverNotVerifiedException(message: String) : Exception(message)

/** An accepted transfer session, with one [PendingUpload] per registered file. */
data class TransferHandle(
    val transferId: String,
    val uploads: List<PendingUpload>,
)

data class PendingUpload(
    val fileId: String,
    val file: SelectedFile,
)
