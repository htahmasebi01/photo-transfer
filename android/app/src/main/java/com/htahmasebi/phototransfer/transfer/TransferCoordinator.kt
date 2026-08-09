package com.htahmasebi.phototransfer.transfer

import com.htahmasebi.phototransfer.model.ReceiverDevice
import com.htahmasebi.phototransfer.model.SelectedFile
import com.htahmasebi.phototransfer.model.TransferState
import com.htahmasebi.phototransfer.protocol.ManifestFile
import com.htahmasebi.phototransfer.protocol.PROTOCOL_VERSION
import com.htahmasebi.phototransfer.protocol.TransferManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns one transfer session at a time. Starting a new transfer cancels the
 * previous one (single-job cancel+replace, so state has one writer).
 */
class TransferCoordinator(
    private val client: TransferClient,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<TransferState>(TransferState.Idle)
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private var transferJob: Job? = null

    fun start(receiver: ReceiverDevice, files: List<SelectedFile>) {
        if (files.isEmpty()) return
        transferJob?.cancel()
        transferJob = scope.launch { runTransfer(receiver, files) }
    }

    fun reset() {
        transferJob?.cancel()
        transferJob = null
        _state.value = TransferState.Idle
    }

    private suspend fun runTransfer(receiver: ReceiverDevice, files: List<SelectedFile>) {
        val manifest = TransferManifest(
            protocolVersion = PROTOCOL_VERSION,
            files = files.mapIndexed { index, file ->
                ManifestFile(
                    id = "file-${index + 1}",
                    name = file.displayName,
                    mediaType = file.mediaType,
                    size = file.size,
                )
            },
        )
        val totalBytes = files.sumOf { it.size ?: 0L }

        try {
            _state.value = transferring(receiver, manifest, totalBytes, completedBytes = 0, fileIndex = 0)
            val transferId = client.createTransfer(receiver, manifest)

            var completedBytes = 0L
            manifest.files.forEachIndexed { index, manifestFile ->
                _state.value = transferring(receiver, manifest, totalBytes, completedBytes, index)
                val baseBytes = completedBytes
                client.uploadFile(receiver, transferId, manifestFile, files[index]) { bytesSent ->
                    _state.value = transferring(receiver, manifest, totalBytes, baseBytes + bytesSent, index)
                }
                completedBytes += manifestFile.size ?: 0L
            }

            client.completeTransfer(receiver, transferId)
            _state.value = TransferState.Completed(transferredFiles = files.size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            _state.value = TransferState.Failed(
                reason = error.message ?: "Transfer failed",
                retryable = true,
            )
        }
    }

    private fun transferring(
        receiver: ReceiverDevice,
        manifest: TransferManifest,
        totalBytes: Long,
        completedBytes: Long,
        fileIndex: Int,
    ) = TransferState.Transferring(
        receiver = receiver,
        completedBytes = completedBytes.coerceAtMost(totalBytes),
        totalBytes = totalBytes,
        currentFileName = manifest.files[fileIndex].name,
        completedFiles = fileIndex,
        totalFiles = manifest.files.size,
    )
}
