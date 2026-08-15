package com.agiletech.android.phototransfer.data.transfer.impl.internal

import android.net.Uri
import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.data.media.MediaByteSource
import com.agiletech.android.phototransfer.data.transfer.ReceiverInfo
import com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol.TransferManifest
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/** Exercises the wire protocol documented in docs/protocol.md against a local server. */
@OptIn(ExperimentalCoroutinesApi::class)
class HttpTransferGatewayTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var receiver: ReceiverDevice
    private lateinit var gateway: HttpTransferGateway

    @Before
    fun setUp() {
        server.start()
        receiver = ReceiverDevice(name = "Mock Mac", host = server.hostName, port = server.port)
        gateway = HttpTransferGateway(
            httpClient = OkHttpClient(),
            json = json,
            byteSource = FakeMediaByteSource(PHOTO_BYTES),
            dispatchers = UnconfinedTestDispatcher().let {
                Dispatchers(main = it, io = it, default = it)
            },
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fetchReceiverInfo maps the receiver name`() = runTest {
        server.enqueue(jsonResponse(200, """{"protocolVersion":1,"receiverName":"Hamid's MacBook"}"""))

        val info = gateway.fetchReceiverInfo(receiver)

        assertEquals(ReceiverInfo(protocolVersion = 1, name = "Hamid's MacBook"), info)
        assertEquals("/v1/info", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `createTransfer sends a manifest and assigns wire file ids`() = runTest {
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))

        val handle = gateway.createTransfer(receiver, listOf(photo("a.jpg", 3), photo("b.jpg", null)))

        assertEquals("t-1", handle.transferId)
        assertEquals(listOf("file-1", "file-2"), handle.uploads.map { it.fileId })

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/transfers", request.url.encodedPath)

        val manifest = json.decodeFromString<TransferManifest>(request.body!!.utf8())
        assertEquals(1, manifest.protocolVersion)
        assertEquals(listOf("a.jpg", "b.jpg"), manifest.files.map { it.name })
        assertEquals(listOf(3L, null), manifest.files.map { it.size })
    }

    @Test
    fun `uploadFile streams the photo bytes and reports progress`() = runTest {
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))
        server.enqueue(MockResponse(code = 200))
        val handle = gateway.createTransfer(receiver, listOf(photo("a.jpg", PHOTO_BYTES.size.toLong())))
        server.takeRequest()
        val progress = mutableListOf<Long>()

        gateway.uploadFile(receiver, handle, handle.uploads.single()) { progress += it }

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/v1/transfers/t-1/files/file-1", request.url.encodedPath)
        assertEquals(String(PHOTO_BYTES), request.body!!.utf8())
        assertEquals(PHOTO_BYTES.size.toLong(), progress.last())
    }

    @Test
    fun `completeTransfer returns the confirmed file count`() = runTest {
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))
        server.enqueue(jsonResponse(200, """{"receivedFiles":2}"""))
        val handle = gateway.createTransfer(receiver, listOf(photo("a.jpg", 3)))
        server.takeRequest()

        val receivedFiles = gateway.completeTransfer(receiver, handle)

        assertEquals(2, receivedFiles)
        assertEquals("/v1/transfers/t-1/complete", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `a server error surfaces as an IOException`() = runTest {
        server.enqueue(MockResponse(code = 500))

        val error = runCatching {
            gateway.createTransfer(receiver, listOf(photo("a.jpg", 3)))
        }.exceptionOrNull()

        assertTrue(error is IOException)
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

    private companion object {
        val PHOTO_BYTES = "fake-jpeg-bytes".toByteArray()
    }
}
