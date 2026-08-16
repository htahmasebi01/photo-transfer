package com.agiletech.android.phototransfer.domain.transfer.impl.internal

import android.net.Uri
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.data.transfer.NotPairedException
import com.agiletech.android.phototransfer.data.transfer.PendingUpload
import com.agiletech.android.phototransfer.data.transfer.ReceiverInfo
import com.agiletech.android.phototransfer.data.transfer.ReceiverNotVerifiedException
import com.agiletech.android.phototransfer.data.transfer.TransferGateway
import com.agiletech.android.phototransfer.data.transfer.TransferHandle
import com.agiletech.android.phototransfer.domain.pairing.IsReceiverPaired
import com.agiletech.android.phototransfer.domain.transfer.TransferState
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be instance of`
import org.amshove.kluent.`should be true`
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTransferCoordinatorTest {

    val receiver = ReceiverDevice(
        name = "Test Mac",
        host = "127.0.0.1",
        port = 8080,
        receiverId = "receiver-1",
    )

    @Test
    fun `given two photos when the transfer runs then it ends in Completed with the file count`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)

        // when
        tested.start(receiver, listOf(photo("a.jpg", 100), photo("b.jpg", 200)))
        advanceUntilIdle()

        // then
        tested.state.value `should be equal to` TransferState.Completed(transferredFiles = 2)
        gateway.uploadedFileNames `should be equal to` listOf("a.jpg", "b.jpg")
        gateway.completedTransferId `should be equal to` "transfer-1"
    }

    @Test
    fun `given two photos when the second upload starts then progress carries over from the first`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)
        val bytesAtEachUploadStart = mutableListOf<Long>()
        gateway.onBeforeUpload = {
            val state = tested.state.value as TransferState.Transferring
            bytesAtEachUploadStart += state.completedBytes
        }

        // when
        tested.start(receiver, listOf(photo("a.jpg", 100), photo("b.jpg", 200)))
        advanceUntilIdle()

        // then
        bytesAtEachUploadStart `should be equal to` listOf(0L, 100L)
    }

    @Test
    fun `given a photo of unknown size when the transfer runs then it still completes`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)
        var totalBytes = -1L
        gateway.onBeforeUpload = {
            totalBytes = (tested.state.value as TransferState.Transferring).totalBytes
        }

        // when
        tested.start(receiver, listOf(photo("a.jpg", null)))
        advanceUntilIdle()

        // then
        totalBytes `should be equal to` 0L
        tested.state.value `should be equal to` TransferState.Completed(transferredFiles = 1)
    }

    @Test
    fun `given a failing upload when the transfer runs then it ends in a retryable Failed state`() = runTest {
        // given
        val gateway = FakeTransferGateway(failUploadOfFileId = "file-2")
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)

        // when
        tested.start(receiver, listOf(photo("a.jpg", 100), photo("b.jpg", 200)))
        advanceUntilIdle()

        // then
        val state = tested.state.value
        state `should be instance of` TransferState.Failed::class
        (state as TransferState.Failed).retryable.`should be true`()
        gateway.uploadedFileNames `should be equal to` listOf("a.jpg")
    }

    @Test
    fun `given an empty selection when a transfer is started then nothing is sent`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)

        // when
        tested.start(receiver, emptyList())
        advanceUntilIdle()

        // then
        tested.state.value `should be equal to` TransferState.Idle
        gateway.createdTransferCount `should be equal to` 0
    }

    @Test
    fun `given a completed transfer when reset then the state returns to Idle`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)
        tested.start(receiver, listOf(photo("a.jpg", 100)))
        advanceUntilIdle()

        // when
        tested.reset()

        // then
        tested.state.value `should be equal to` TransferState.Idle
    }

    @Test
    fun `given a running transfer when a second one starts then it replaces the first`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)

        // when
        tested.start(receiver, listOf(photo("a.jpg", 100)))
        tested.start(receiver, listOf(photo("b.jpg", 100)))
        advanceUntilIdle()

        // then
        tested.state.value `should be equal to` TransferState.Completed(transferredFiles = 1)
        gateway.uploadedFileNames `should be equal to` listOf("b.jpg")
    }

    @Test
    fun `given an unpaired receiver when a transfer starts then it stops before any photo is sent`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(paired = false), this)

        // when
        tested.start(receiver, listOf(photo("a.jpg", 100)))
        advanceUntilIdle()

        // then
        tested.state.value `should be equal to` TransferState.PairingRequired(receiver)
        gateway.createdTransferCount `should be equal to` 0
        gateway.uploadedFileNames.`should be empty`()
    }

    @Test
    fun `given a manual address when a transfer starts then it is identified before pairing is checked`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val pairing = PairedReceivers(paired = false)
        val tested = DefaultTransferCoordinator(gateway, pairing, this)

        // when
        tested.start(receiver.copy(receiverId = null), listOf(photo("a.jpg", 100)))
        advanceUntilIdle()

        // then
        val state = tested.state.value as TransferState.PairingRequired
        state.receiver.receiverId `should be equal to` "fake-receiver-id"
        pairing.checkedReceiverIds `should be equal to` listOf("fake-receiver-id")
    }

    @Test
    fun `given credentials rejected mid-transfer when uploading then pairing is asked for again`() = runTest {
        // given
        val gateway = FakeTransferGateway(failUploadWith = NotPairedException("rejected"))
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)

        // when
        tested.start(receiver, listOf(photo("a.jpg", 100)))
        advanceUntilIdle()

        // then
        tested.state.value `should be equal to` TransferState.PairingRequired(receiver)
    }

    @Test
    fun `given an unproven receiver when a transfer starts then no photo is sent`() = runTest {
        // given
        val gateway = FakeTransferGateway().apply { receiverIsProven = false }
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)

        // when
        tested.start(receiver, listOf(photo("a.jpg", 100)))
        advanceUntilIdle()

        // then
        tested.state.value `should be equal to` TransferState.ReceiverUnverified(receiver)
        gateway.createdTransferCount `should be equal to` 0
        gateway.uploadedFileNames.`should be empty`()
    }

    @Test
    fun `given a paired receiver when a transfer starts then it is verified before the manifest is sent`() = runTest {
        // given
        val gateway = FakeTransferGateway()
        val tested = DefaultTransferCoordinator(gateway, PairedReceivers(), this)

        // when
        tested.start(receiver, listOf(photo("a.jpg", 100)))
        advanceUntilIdle()

        // then
        gateway.verifiedReceiverCount `should be equal to` 1
        gateway.createdTransferCount `should be equal to` 1
    }

    private fun photo(name: String, size: Long?) = SelectedFile(
        uri = mock<Uri>(),
        displayName = name,
        mediaType = "image/jpeg",
        size = size,
    )

    class PairedReceivers(private val paired: Boolean = true) : IsReceiverPaired {

        val checkedReceiverIds = mutableListOf<String>()

        override suspend fun invoke(receiver: ReceiverDevice): Boolean {
            receiver.receiverId?.let { checkedReceiverIds += it }
            return paired
        }
    }

    class FakeTransferGateway(
        private val failUploadOfFileId: String? = null,
        private val failUploadWith: Exception? = null,
    ) : TransferGateway {

        var createdTransferCount = 0
        var completedTransferId: String? = null
        var onBeforeUpload: ((PendingUpload) -> Unit)? = null
        var receiverIsProven = true
        var verifiedReceiverCount = 0
        val uploadedFileNames = mutableListOf<String>()

        override suspend fun fetchReceiverInfo(receiver: ReceiverDevice) =
            ReceiverInfo(protocolVersion = 1, receiverId = "fake-receiver-id", name = "Fake")

        override suspend fun verifyReceiver(receiver: ReceiverDevice) {
            verifiedReceiverCount++
            if (!receiverIsProven) {
                throw ReceiverNotVerifiedException("${receiver.name} could not be verified")
            }
        }

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
            failUploadWith?.let { throw it }
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
