package com.htahmasebi.phototransfer.domain.transfer.impl.internal

import com.htahmasebi.phototransfer.core.model.ReceiverDevice
import com.htahmasebi.phototransfer.core.model.SelectedFile
import com.htahmasebi.phototransfer.data.transfer.TransferGateway
import com.htahmasebi.phototransfer.data.transfer.TransferHandle
import com.htahmasebi.phototransfer.domain.transfer.TransferCoordinator
import com.htahmasebi.phototransfer.domain.transfer.TransferState
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single-job cancel and replace: only one transfer runs at a time, so [state]
 * has exactly one writer.
 */
internal class DefaultTransferCoordinator @Inject constructor(
    private val gateway: TransferGateway,
    private val scope: CoroutineScope,
) : TransferCoordinator {

    private val _state = MutableStateFlow<TransferState>(TransferState.Idle)
    override val state: StateFlow<TransferState> = _state.asStateFlow()

    private var transferJob: Job? = null

    override fun start(receiver: ReceiverDevice, files: List<SelectedFile>) {
        if (files.isEmpty()) return
        transferJob?.cancel()
        transferJob = scope.launch { runTransfer(receiver, files) }
    }

    override fun reset() {
        transferJob?.cancel()
        transferJob = null
        _state.value = TransferState.Idle
    }

    private suspend fun runTransfer(receiver: ReceiverDevice, files: List<SelectedFile>) {
        val totalBytes = files.sumOf { it.size ?: 0L }
        try {
            _state.value = TransferState.Transferring(
                receiver = receiver,
                completedBytes = 0,
                totalBytes = totalBytes,
                currentFileName = files.first().displayName,
                completedFiles = 0,
                totalFiles = files.size,
            )

            val handle = gateway.createTransfer(receiver, files)
            uploadAll(receiver, handle, totalBytes)
            gateway.completeTransfer(receiver, handle)

            _state.value = TransferState.Completed(transferredFiles = handle.uploads.size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            _state.value = TransferState.Failed(
                reason = error.message ?: "Transfer failed",
                retryable = true,
            )
        }
    }

    private suspend fun uploadAll(
        receiver: ReceiverDevice,
        handle: TransferHandle,
        totalBytes: Long,
    ) {
        var completedBytes = 0L
        handle.uploads.forEachIndexed { index, upload ->
            val bytesBeforeThisFile = completedBytes
            publishProgress(receiver, handle, index, bytesBeforeThisFile, totalBytes)

            gateway.uploadFile(receiver, handle, upload) { bytesSent ->
                publishProgress(receiver, handle, index, bytesBeforeThisFile + bytesSent, totalBytes)
            }
            completedBytes += upload.file.size ?: 0L
        }
    }

    private fun publishProgress(
        receiver: ReceiverDevice,
        handle: TransferHandle,
        fileIndex: Int,
        completedBytes: Long,
        totalBytes: Long,
    ) {
        _state.value = TransferState.Transferring(
            receiver = receiver,
            completedBytes = completedBytes.coerceAtMost(totalBytes),
            totalBytes = totalBytes,
            currentFileName = handle.uploads[fileIndex].file.displayName,
            completedFiles = fileIndex,
            totalFiles = handle.uploads.size,
        )
    }
}
