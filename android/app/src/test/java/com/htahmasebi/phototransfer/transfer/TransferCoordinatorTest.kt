package com.htahmasebi.phototransfer.transfer

import android.net.Uri
import com.htahmasebi.phototransfer.model.ReceiverDevice
import com.htahmasebi.phototransfer.model.SelectedFile
import com.htahmasebi.phototransfer.model.TransferState
import com.htahmasebi.phototransfer.protocol.InfoResponse
import com.htahmasebi.phototransfer.protocol.ManifestFile
import com.htahmasebi.phototransfer.protocol.TransferManifest
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class TransferCoordinatorTest {

    private val receiver = ReceiverDevice(name = "Test Mac", host = "127.0.0.1", port = 8080)

    private fun selectedFile(name: String, size: Long?) = SelectedFile(
        uri = mock<Uri>(),
        displayName = name,
        mediaType = "image/jpeg",
        size = size,
    )

    @Test
    fun `successful transfer ends in Completed with file count`() = runTest {
        val client = FakeTransferClient()
        val coordinator = TransferCoordinator(client, this)
        val files = listOf(selectedFile("a.jpg", 100), selectedFile("b.jpg", 200))

        coordinator.start(receiver, files)
        advanceUntilIdle()

        assertEquals(TransferState.Completed(transferredFiles = 2), coordinator.state.value)
        assertEquals(listOf("file-1", "file-2"), client.uploadedFileIds)
        assertEquals("transfer-1", client.completedTransferId)
    }

    @Test
    fun `manifest carries names sizes and media types`() = runTest {
        val client = FakeTransferClient()
        val coordinator = TransferCoordinator(client, this)

        coordinator.start(receiver, listOf(selectedFile("a.jpg", 100), selectedFile("b.jpg", null)))
        advanceUntilIdle()

        val manifest = requireNotNull(client.createdManifest)
        assertEquals(1, manifest.protocolVersion)
        assertEquals(
            listOf(
                ManifestFile(id = "file-1", name = "a.jpg", mediaType = "image/jpeg", size = 100),
                ManifestFile(id = "file-2", name = "b.jpg", mediaType = "image/jpeg", size = null),
            ),
            manifest.files,
        )
    }

    @Test
    fun `upload failure ends in retryable Failed`() = runTest {
        val client = FakeTransferClient(failUploadOfFileId = "file-2")
        val coordinator = TransferCoordinator(client, this)

        coordinator.start(receiver, listOf(selectedFile("a.jpg", 100), selectedFile("b.jpg", 200)))
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue(state is TransferState.Failed)
        assertTrue((state as TransferState.Failed).retryable)
        assertEquals(listOf("file-1"), client.uploadedFileIds)
    }

    @Test
    fun `empty selection does not start a transfer`() = runTest {
        val client = FakeTransferClient()
        val coordinator = TransferCoordinator(client, this)

        coordinator.start(receiver, emptyList())
        advanceUntilIdle()

        assertEquals(TransferState.Idle, coordinator.state.value)
        assertEquals(null, client.createdManifest)
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        val client = FakeTransferClient()
        val coordinator = TransferCoordinator(client, this)

        coordinator.start(receiver, listOf(selectedFile("a.jpg", 100)))
        advanceUntilIdle()
        coordinator.reset()

        assertEquals(TransferState.Idle, coordinator.state.value)
    }

    private class FakeTransferClient(
        private val failUploadOfFileId: String? = null,
    ) : TransferClient {

        var createdManifest: TransferManifest? = null
        var completedTransferId: String? = null
        val uploadedFileIds = mutableListOf<String>()

        override suspend fun fetchInfo(receiver: ReceiverDevice) =
            InfoResponse(protocolVersion = 1, receiverName = "Fake")

        override suspend fun createTransfer(receiver: ReceiverDevice, manifest: TransferManifest): String {
            createdManifest = manifest
            return "transfer-1"
        }

        override suspend fun uploadFile(
            receiver: ReceiverDevice,
            transferId: String,
            manifestFile: ManifestFile,
            source: SelectedFile,
            onBytesSent: (Long) -> Unit,
        ) {
            if (manifestFile.id == failUploadOfFileId) {
                throw IOException("Upload failed: ${manifestFile.id}")
            }
            onBytesSent(manifestFile.size ?: 0L)
            uploadedFileIds += manifestFile.id
        }

        override suspend fun completeTransfer(receiver: ReceiverDevice, transferId: String): Int {
            completedTransferId = transferId
            return uploadedFileIds.size
        }
    }
}
