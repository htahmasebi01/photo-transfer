package com.htahmasebi.phototransfer.transfer

import android.net.Uri
import com.htahmasebi.phototransfer.model.ReceiverDevice
import com.htahmasebi.phototransfer.model.SelectedFile
import com.htahmasebi.phototransfer.model.TransferState
import com.htahmasebi.phototransfer.protocol.TransferManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/** Drives the real coordinator + HTTP client against a local mock server. */
class TransferIntegrationTest {

    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var coordinator: TransferCoordinator
    private lateinit var receiver: ReceiverDevice

    @Before
    fun setUp() {
        server.start()
        receiver = ReceiverDevice(name = "Mock Mac", host = server.hostName, port = server.port)
        coordinator = TransferCoordinator(
            client = HttpTransferClient(
                httpClient = OkHttpClient(),
                bodyFactory = { file ->
                    FILE_CONTENT.toRequestBody(file.mediaType.toMediaType())
                },
                ioDispatcher = Dispatchers.IO,
            ),
            scope = scope,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    @Test
    fun `full transfer flow against mock server`() = runTest {
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))
        server.enqueue(MockResponse(code = 200))
        server.enqueue(MockResponse(code = 200))
        server.enqueue(jsonResponse(200, """{"receivedFiles":2}"""))

        coordinator.start(receiver, listOf(selectedFile("a.jpg"), selectedFile("b.jpg")))
        val finalState = coordinator.state.first { it is TransferState.Completed || it is TransferState.Failed }

        assertEquals(TransferState.Completed(transferredFiles = 2), finalState)

        val createRequest = server.takeRequest()
        assertEquals("POST", createRequest.method)
        assertEquals("/v1/transfers", createRequest.url.encodedPath)
        val manifest = json.decodeFromString<TransferManifest>(createRequest.body!!.utf8())
        assertEquals(listOf("a.jpg", "b.jpg"), manifest.files.map { it.name })

        val firstUpload = server.takeRequest()
        assertEquals("PUT", firstUpload.method)
        assertEquals("/v1/transfers/t-1/files/file-1", firstUpload.url.encodedPath)
        assertEquals(FILE_CONTENT, firstUpload.body!!.utf8())

        val secondUpload = server.takeRequest()
        assertEquals("/v1/transfers/t-1/files/file-2", secondUpload.url.encodedPath)

        val completeRequest = server.takeRequest()
        assertEquals("POST", completeRequest.method)
        assertEquals("/v1/transfers/t-1/complete", completeRequest.url.encodedPath)
    }

    @Test
    fun `server error ends in retryable Failed`() = runTest {
        server.enqueue(jsonResponse(201, """{"transferId":"t-1"}"""))
        server.enqueue(MockResponse(code = 500))

        coordinator.start(receiver, listOf(selectedFile("a.jpg")))
        val finalState = coordinator.state.first { it is TransferState.Completed || it is TransferState.Failed }

        assertTrue(finalState is TransferState.Failed)
        assertTrue((finalState as TransferState.Failed).retryable)
    }

    private fun jsonResponse(code: Int, body: String) = MockResponse(
        code = code,
        headers = okhttp3.Headers.headersOf("Content-Type", "application/json"),
        body = body,
    )

    private fun selectedFile(name: String) = SelectedFile(
        uri = mock<Uri>(),
        displayName = name,
        mediaType = "image/jpeg",
        size = FILE_CONTENT.length.toLong(),
    )

    private companion object {
        const val FILE_CONTENT = "fake-jpeg-bytes"
    }
}
