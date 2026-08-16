package com.agiletech.android.phototransfer.domain.transfer.impl.internal

import com.agiletech.android.phototransfer.core.coroutines.scopes.ApplicationScope
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.data.transfer.NotPairedException
import com.agiletech.android.phototransfer.data.transfer.ReceiverNotVerifiedException
import com.agiletech.android.phototransfer.data.transfer.TransferGateway
import com.agiletech.android.phototransfer.data.transfer.TransferHandle
import com.agiletech.android.phototransfer.domain.pairing.IsReceiverPaired
import com.agiletech.android.phototransfer.domain.transfer.TransferCoordinator
import com.agiletech.android.phototransfer.domain.transfer.TransferState
import java.io.IOException
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
    private val isReceiverPaired: IsReceiverPaired,
    @ApplicationScope private val scope: CoroutineScope,
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
            val identified = identify(receiver)
            if (!isReceiverPaired(identified)) {
                _state.value = TransferState.PairingRequired(identified)
                return
            }

            // Before any bytes, including the manifest: a receiverId is public, so being
            // paired with one proves nothing about whatever is answering on this address.
            gateway.verifyReceiver(identified)

            _state.value = TransferState.Transferring(
                receiver = identified,
                completedBytes = 0,
                totalBytes = totalBytes,
                currentFileName = files.first().displayName,
                completedFiles = 0,
                totalFiles = files.size,
            )

            val handle = gateway.createTransfer(identified, files)
            uploadAll(identified, handle, totalBytes)
            gateway.completeTransfer(identified, handle)

            _state.value = TransferState.Completed(transferredFiles = handle.uploads.size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unverified: ReceiverNotVerifiedException) {
            _state.value = TransferState.ReceiverUnverified(receiver)
        } catch (notPaired: NotPairedException) {
            _state.value = receiver.receiverId
                ?.let { TransferState.PairingRequired(receiver) }
                ?: TransferState.Failed(
                    reason = notPaired.message ?: "Not paired with this receiver",
                    retryable = false,
                )
        } catch (failure: IOException) {
            _state.value = TransferState.Failed(
                reason = failure.message ?: "Transfer failed",
                retryable = true,
            )
        }
    }

    /** A manually entered address has no receiver id yet, so ask the receiver for it. */
    private suspend fun identify(receiver: ReceiverDevice): ReceiverDevice =
        if (receiver.receiverId != null) {
            receiver
        } else {
            receiver.copy(receiverId = gateway.fetchReceiverInfo(receiver).receiverId)
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
