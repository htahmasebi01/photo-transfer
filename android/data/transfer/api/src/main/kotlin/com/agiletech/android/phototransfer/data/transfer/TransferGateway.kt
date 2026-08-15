package com.agiletech.android.phototransfer.data.transfer

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile

/**
 * Moves photos to a receiver. The wire format (endpoints, manifest shape, file
 * ids) is an implementation detail and never crosses this boundary.
 */
interface TransferGateway {

    suspend fun fetchReceiverInfo(receiver: ReceiverDevice): ReceiverInfo

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
    val name: String,
)

/** An accepted transfer session, with one [PendingUpload] per registered file. */
data class TransferHandle(
    val transferId: String,
    val uploads: List<PendingUpload>,
)

data class PendingUpload(
    val fileId: String,
    val file: SelectedFile,
)
