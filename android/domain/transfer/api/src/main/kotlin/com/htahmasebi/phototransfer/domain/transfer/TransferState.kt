package com.htahmasebi.phototransfer.domain.transfer

import com.htahmasebi.phototransfer.core.model.ReceiverDevice

sealed interface TransferState {

    data object Idle : TransferState

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
