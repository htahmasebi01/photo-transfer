package com.agiletech.android.phototransfer.domain.transfer.impl.internal

import android.net.Uri
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.data.transfer.PendingUpload
import com.agiletech.android.phototransfer.data.transfer.ReceiverInfo
import com.agiletech.android.phototransfer.data.transfer.TransferGateway
import com.agiletech.android.phototransfer.data.transfer.TransferHandle
import com.agiletech.android.phototransfer.domain.transfer.TransferState
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTransferCoordinatorTest {

    private val receiver = ReceiverDevice(name = "Test Mac", host = "127.0.0.1", port = 8080)

    @Test
    fun `successful transfer ends in Completed with the file count`() = runTest {
        val gateway = FakeTransferGateway()
        val coordinator = DefaultTransferCoordinator(gateway, this)

        coordinator.start(receiver, listOf(photo("a.jpg", 100), photo("b.jpg", 200)))
        advanceUntilIdle()

        assertEquals(TransferState.Completed(transferredFiles = 2), coordinator.state.value)
        assertEquals(listOf("a.jpg", "b.jpg"), gateway.uploadedFileNames)
        assertEquals("transfer-1", gateway.completedTransferId)
    }

    @Test
    fun `progress carries over from one file to the next`() = runTest {
        val gateway = FakeTransferGateway()
        val coordinator = DefaultTransferCoordinator(gateway, this)
        val bytesAtEachUploadStart = mutableListOf<Long>()
        gateway.onBeforeUpload = {
            val state = coordinator.state.value as TransferState.Transferring
            bytesAtEachUploadStart += state.completedBytes
        }

        coordinator.start(receiver, listOf(photo("a.jpg", 100), photo("b.jpg", 200)))
        advanceUntilIdle()

        assertEquals(listOf(0L, 100L), bytesAtEachUploadStart)
    }

    @Test
    fun `files of unknown size still report total progress`() = runTest {
        val gateway = FakeTransferGateway()
        val coordinator = DefaultTransferCoordinator(gateway, this)
        var totalBytes = -1L
        gateway.onBeforeUpload = {
            totalBytes = (coordinator.state.value as TransferState.Transferring).totalBytes
        }

        coordinator.start(receiver, listOf(photo("a.jpg", null)))
        advanceUntilIdle()

        assertEquals(0L, totalBytes)
        assertEquals(TransferState.Completed(transferredFiles = 1), coordinator.state.value)
    }

    @Test
    fun `upload failure ends in a retryable Failed state`() = runTest {
        val gateway = FakeTransferGateway(failUploadOfFileId = "file-2")
        val coordinator = DefaultTransferCoordinator(gateway, this)

        coordinator.start(receiver, listOf(photo("a.jpg", 100), photo("b.jpg", 200)))
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue(state is TransferState.Failed)
        assertTrue((state as TransferState.Failed).retryable)
        assertEquals(listOf("a.jpg"), gateway.uploadedFileNames)
    }

    @Test
    fun `empty selection does not start a transfer`() = runTest {
        val gateway = FakeTransferGateway()
        val coordinator = DefaultTransferCoordinator(gateway, this)

        coordinator.start(receiver, emptyList())
        advanceUntilIdle()

        assertEquals(TransferState.Idle, coordinator.state.value)
        assertEquals(0, gateway.createdTransferCount)
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        val gateway = FakeTransferGateway()
        val coordinator = DefaultTransferCoordinator(gateway, this)

        coordinator.start(receiver, listOf(photo("a.jpg", 100)))
        advanceUntilIdle()
        coordinator.reset()

        assertEquals(TransferState.Idle, coordinator.state.value)
    }

    @Test
    fun `starting a second transfer replaces the first`() = runTest {
        val gateway = FakeTransferGateway()
        val coordinator = DefaultTransferCoordinator(gateway, this)

        coordinator.start(receiver, listOf(photo("a.jpg", 100)))
        coordinator.start(receiver, listOf(photo("b.jpg", 100)))
        advanceUntilIdle()

        assertEquals(TransferState.Completed(transferredFiles = 1), coordinator.state.value)
        assertEquals(listOf("b.jpg"), gateway.uploadedFileNames)
    }

    private fun photo(name: String, size: Long?) = SelectedFile(
        uri = mock<Uri>(),
        displayName = name,
        mediaType = "image/jpeg",
        size = size,
    )

    private class FakeTransferGateway(
        private val failUploadOfFileId: String? = null,
    ) : TransferGateway {

        var createdTransferCount = 0
        var completedTransferId: String? = null
        var onBeforeUpload: ((PendingUpload) -> Unit)? = null
        val uploadedFileNames = mutableListOf<String>()

        override suspend fun fetchReceiverInfo(receiver: ReceiverDevice) =
            ReceiverInfo(protocolVersion = 1, name = "Fake")

        override suspend fun createTransfer(
            receiver: ReceiverDevice,
            files: List<SelectedFile>,
        ): TransferHandle {
            createdTransferCount++
            return TransferHandle(
                transferId = "transfer-1",
                uploads = files.mapIndexed { index, file ->
                    PendingUpload(fileId = "file-${index + 1}", file = file)
                },
            )
        }

        override suspend fun uploadFile(
            receiver: ReceiverDevice,
            handle: TransferHandle,
            upload: PendingUpload,
            onBytesSent: (Long) -> Unit,
        ) {
            onBeforeUpload?.invoke(upload)
            if (upload.fileId == failUploadOfFileId) {
                throw IOException("Upload failed: ${upload.fileId}")
            }
            onBytesSent(upload.file.size ?: 0L)
            uploadedFileNames += upload.file.displayName
        }

        override suspend fun completeTransfer(
            receiver: ReceiverDevice,
            handle: TransferHandle,
        ): Int {
            completedTransferId = handle.transferId
            return handle.uploads.size
        }
    }
}
