package com.agiletech.android.phototransfer.data.transfer.impl.internal

import android.net.Uri
import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.data.media.MediaByteSource
import com.agiletech.android.phototransfer.data.pairing.RequestSigner
import com.agiletech.android.phototransfer.data.pairing.SignedRequest
import com.agiletech.android.phototransfer.data.transfer.NotPairedException
import com.agiletech.android.phototransfer.data.transfer.ReceiverInfo
import com.agiletech.android.phototransfer.data.transfer.ReceiverNotVerifiedException
import com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol.TransferManifestRequestParamsDto
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be greater than`
import org.amshove.kluent.coInvoking
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/** Exercises the wire protocol documented in docs/protocol.md against a local server. */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpTransferGatewayTest {

    val server = MockWebServer()

    val json = Json { ignoreUnknownKeys = true }

    val signer = FakeRequestSigner()

    val receiver by lazy {
        ReceiverDevice(
            name = "Mock Mac",
            host = server.hostName,
            port = server.port,
            receiverId = RECEIVER_ID,
        )
    }

    val tested by lazy {
        val dispatcher = UnconfinedTestDispatcher()
        HttpTransferGateway(
            httpClient = OkHttpClient(),
            json = json,
            byteSource = FakeMediaByteSource(PHOTO_BYTES),
            requestSigner = signer,
            dispatchers = Dispatchers(main = dispatcher, io = dispatcher, default = dispatcher),
        )
    }

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `given an info response when the receiver is fetched then the name is mapped`() = runTest {
        // given
        server.enqueue(
            jsonResponse(
                200,
                """{"protocolVersion":1,"receiverId":"r-1","receiverName":"Hamid's MacBook"}""",
            ),
        )

        // when
        val info = tested.fetchReceiverInfo(receiver)

        // then
        info `should be equal to` ReceiverInfo(
            protocolVersion = 1,
            receiverId = "r-1",
            name = "Hamid's MacBook",
        )
        server.takeRequest().url.encodedPath `should be equal to` "/v1/info"
    }

    @Test
    fun `given two photos when a transfer is created then a manifest with wire ids is sent`() = runTest {
        // given
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))

        // when
        val handle = tested.createTransfer(receiver, listOf(photo("a.jpg", 3), photo("b.jpg", null)))

        // then
        handle.transferId `should be equal to` "t-1"
        handle.uploads.map { it.fileId } `should be equal to` listOf("file-1", "file-2")

        val request = server.takeRequest()
        request.method `should be equal to` "POST"
        request.url.encodedPath `should be equal to` "/v1/transfers"

        val manifest = json.decodeFromString<TransferManifestRequestParamsDto>(request.body!!.utf8())
        manifest.protocolVersion `should be equal to` 1
        manifest.files.map { it.name } `should be equal to` listOf("a.jpg", "b.jpg")
        manifest.files.map { it.size } `should be equal to` listOf(3L, null)
    }

    @Test
    fun `given a created transfer when a file is uploaded then bytes stream and progress is reported`() = runTest {
        // given
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))
        server.enqueue(MockResponse(code = 200))
        val handle = tested.createTransfer(receiver, listOf(photo("a.jpg", PHOTO_BYTES.size.toLong())))
        server.takeRequest()
        val progress = mutableListOf<Long>()

        // when
        tested.uploadFile(receiver, handle, handle.uploads.single()) { progress += it }

        // then
        val request = server.takeRequest()
        request.method `should be equal to` "PUT"
        request.url.encodedPath `should be equal to` "/v1/transfers/t-1/files/file-1"
        request.body!!.utf8() `should be equal to` String(PHOTO_BYTES)
        progress.last() `should be equal to` PHOTO_BYTES.size.toLong()
    }

    @Test
    fun `given an uploaded transfer when it is completed then the confirmed count is returned`() = runTest {
        // given
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))
        server.enqueue(jsonResponse(200, """{"receivedFiles":2}"""))
        val handle = tested.createTransfer(receiver, listOf(photo("a.jpg", 3)))
        server.takeRequest()

        // when
        val receivedFiles = tested.completeTransfer(receiver, handle)

        // then
        receivedFiles `should be equal to` 2
        server.takeRequest().url.encodedPath `should be equal to` "/v1/transfers/t-1/complete"
    }

    @Test
    fun `given a server error when a transfer is created then an IOException surfaces`() = runTest {
        // given
        server.enqueue(MockResponse(code = 500))

        // when, then
        coInvoking {
            tested.createTransfer(receiver, listOf(photo("a.jpg", 3)))
        } shouldThrow IOException::class
    }

    /**
     * The receiver caps the manifest it buffers, and a bare 413 tells the user nothing about
     * what to do differently.
     */
    @Test
    fun `given too large a manifest when a transfer is created then the error explains the batch size`() = runTest {
        // given
        server.enqueue(MockResponse(code = 413))

        // when, then
        coInvoking {
            tested.createTransfer(receiver, listOf(photo("a.jpg", 3)))
        } shouldThrow IOException::class withMessage
            "That is too many photos to send at once. Try sending them in smaller batches."
    }

    @Test
    fun `given a transfer when requests are sent then each carries signature headers`() = runTest {
        // given
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))
        server.enqueue(MockResponse(code = 200))
        val handle = tested.createTransfer(receiver, listOf(photo("a.jpg", PHOTO_BYTES.size.toLong())))

        // when
        tested.uploadFile(receiver, handle, handle.uploads.single()) { }

        // then
        listOf(server.takeRequest(), server.takeRequest()).forEach { request ->
            request.headers["X-PT-Signature"] `should be equal to` "signature-for-$RECEIVER_ID"
            request.headers["X-PT-Device"] `should be equal to` "token"
        }
    }

    @Test
    fun `given a streamed upload when it is signed then the body is left out of the signature`() = runTest {
        // given
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))
        server.enqueue(MockResponse(code = 200))
        val handle = tested.createTransfer(receiver, listOf(photo("a.jpg", PHOTO_BYTES.size.toLong())))

        // when
        tested.uploadFile(receiver, handle, handle.uploads.single()) { }

        // then
        signer.signedBodySizes.first() `should be greater than` 0
        signer.signedBodySizes.last() `should be equal to` 0
    }

    @Test
    fun `given no pairing when a transfer is created then nothing is sent`() = runTest {
        // given
        signer.paired = false

        // when
        coInvoking {
            tested.createTransfer(receiver, listOf(photo("a.jpg", 3)))
        } shouldThrow NotPairedException::class

        // then
        server.requestCount `should be equal to` 0
    }

    @Test
    fun `given a receiver without an id when a transfer is created then it cannot be signed for`() = runTest {
        // given
        val unidentified = receiver.copy(receiverId = null)

        // when, then
        coInvoking {
            tested.createTransfer(unidentified, listOf(photo("a.jpg", 3)))
        } shouldThrow NotPairedException::class
    }

    @Test
    fun `given a rejected signature when a transfer is created then NotPairedException surfaces`() = runTest {
        // given
        server.enqueue(MockResponse(code = 401))

        // when, then
        coInvoking {
            tested.createTransfer(receiver, listOf(photo("a.jpg", 3)))
        } shouldThrow NotPairedException::class
    }

    @Test
    fun `given a valid proof when the receiver is verified then the verify endpoint is called`() = runTest {
        // given
        server.enqueue(MockResponse(code = 204))

        // when
        tested.verifyReceiver(receiver)

        // then
        server.takeRequest().target `should be equal to` "/v1/verify"
    }

    @Test
    fun `given a proof that does not check out when the receiver is verified then it is rejected`() = runTest {
        // given
        signer.receiverIsProven = false
        server.enqueue(MockResponse(code = 204))

        // when, then
        coInvoking { tested.verifyReceiver(receiver) } shouldThrow
            ReceiverNotVerifiedException::class
    }

    @Test
    fun `given an unproven receiver when a transfer is created then the manifest is not accepted`() = runTest {
        // given
        signer.receiverIsProven = false
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))

        // when, then
        coInvoking {
            tested.createTransfer(receiver, listOf(photo("a.jpg", 1)))
        } shouldThrow ReceiverNotVerifiedException::class
    }

    @Test
    fun `given a proof header when the receiver is verified then that header is what gets checked`() = runTest {
        // given
        server.enqueue(
            MockResponse(
                code = 204,
                headers = Headers.headersOf("X-PT-Receiver-Signature", "proof-abc"),
            ),
        )

        // when
        tested.verifyReceiver(receiver)

        // then
        signer.verifiedProofs `should be equal to` listOf("proof-abc")
    }

    private fun jsonResponse(code: Int, body: String) = MockResponse(
        code = code,
        headers = Headers.headersOf("Content-Type", "application/json"),
        body = body,
    )

    private fun photo(name: String, size: Long?) = SelectedFile(
        uri = mock<Uri>(),
        displayName = name,
        mediaType = "image/jpeg",
        size = size,
    )

    private class FakeMediaByteSource(private val bytes: ByteArray) : MediaByteSource {
        override fun openStream(uri: Uri): InputStream = ByteArrayInputStream(bytes)
    }

    private class FakeRequestSigner : RequestSigner {

        var paired = true
        var receiverIsProven = true
        val signedBodySizes = mutableListOf<Int>()
        val verifiedProofs = mutableListOf<String?>()

        override suspend fun sign(
            receiverId: String,
            method: String,
            path: String,
            bodyForSigning: ByteArray,
        ): SignedRequest? {
            if (!paired) return null
            signedBodySizes += bodyForSigning.size
            return SignedRequest(
                headers = mapOf(
                    "X-PT-Device" to "token",
                    "X-PT-Timestamp" to "1700000000",
                    "X-PT-Nonce" to "nonce",
                    "X-PT-Signature" to "signature-for-$receiverId",
                ),
                method = method,
                path = path,
                nonce = "nonce",
            )
        }

        override suspend fun isProvenReceiver(
            receiverId: String,
            request: SignedRequest,
            proofBase64: String?,
        ): Boolean {
            verifiedProofs += proofBase64
            return receiverIsProven
        }
    }

    companion object {
        val PHOTO_BYTES = "fake-jpeg-bytes".toByteArray()
        const val RECEIVER_ID = "r-1"
    }
}
