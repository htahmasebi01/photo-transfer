package com.agiletech.android.phototransfer.data.transfer.impl.internal

import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.data.media.MediaByteSource
import com.agiletech.android.phototransfer.data.pairing.RequestSigner
import com.agiletech.android.phototransfer.data.pairing.SignedRequest
import com.agiletech.android.phototransfer.data.transfer.NotPairedException
import com.agiletech.android.phototransfer.data.transfer.PendingUpload
import com.agiletech.android.phototransfer.data.transfer.ReceiverInfo
import com.agiletech.android.phototransfer.data.transfer.ReceiverNotVerifiedException
import com.agiletech.android.phototransfer.data.transfer.TransferGateway
import com.agiletech.android.phototransfer.data.transfer.TransferHandle
import com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol.CompleteDto
import com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol.InfoDto
import com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol.ManifestFileDto
import com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol.PROTOCOL_VERSION
import com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol.TransferCreatedDto
import com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol.TransferManifestRequestParamsDto
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class HttpTransferGateway @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val byteSource: MediaByteSource,
    private val requestSigner: RequestSigner,
    private val dispatchers: Dispatchers,
) : TransferGateway {

    override suspend fun fetchReceiverInfo(receiver: ReceiverDevice): ReceiverInfo {
        val request = Request.Builder()
            .url(receiver.endpoint("v1/info"))
            .get()
            .build()
        return execute(request) { response ->
            val info = json.decodeFromString<InfoDto>(response.body.string())
            ReceiverInfo(
                protocolVersion = info.protocolVersion,
                receiverId = info.receiverId,
                name = info.receiverName,
            )
        }
    }

    override suspend fun verifyReceiver(receiver: ReceiverDevice) {
        val url = receiver.endpoint("v1/verify")
        val signed = receiver.sign("POST", url, ByteArray(0))
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody())
            .withHeaders(signed)
            .build()
        executeVerified(receiver, request, signed) { }
    }

    override suspend fun createTransfer(
        receiver: ReceiverDevice,
        files: List<SelectedFile>,
    ): TransferHandle {
        val uploads = files.mapIndexed { index, file ->
            PendingUpload(fileId = "file-${index + 1}", file = file)
        }
        val manifest = TransferManifestRequestParamsDto(
            protocolVersion = PROTOCOL_VERSION,
            files = uploads.map { upload ->
                ManifestFileDto(
                    id = upload.fileId,
                    name = upload.file.displayName,
                    mediaType = upload.file.mediaType,
                    size = upload.file.size,
                )
            },
        )
        val body = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        val url = receiver.endpoint("v1/transfers")
        val signed = receiver.sign("POST", url, body)
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .withHeaders(signed)
            .build()

        val transferId = executeVerified(
            receiver = receiver,
            request = request,
            signed = signed,
            // The receiver caps the manifest it will buffer, which a very large selection
            // reaches long before anything else goes wrong.
            oversizedMessage = "That is too many photos to send at once. " +
                "Try sending them in smaller batches.",
        ) { response ->
            json.decodeFromString<TransferCreatedDto>(response.body.string()).transferId
        }
        return TransferHandle(transferId = transferId, uploads = uploads)
    }

    override suspend fun uploadFile(
        receiver: ReceiverDevice,
        handle: TransferHandle,
        upload: PendingUpload,
        onBytesSent: (Long) -> Unit,
    ) {
        val body = ProgressRequestBody(
            delegate = MediaRequestBody(byteSource, upload.file),
            onBytesSent = onBytesSent,
        )
        val url = receiver.endpoint("v1/transfers/${handle.transferId}/files/${upload.fileId}")
        val signed = receiver.sign("PUT", url, STREAMED_BODY)
        val request = Request.Builder()
            .url(url)
            .put(body)
            .withHeaders(signed)
            .build()
        executeVerified(receiver, request, signed) { }
    }

    override suspend fun completeTransfer(receiver: ReceiverDevice, handle: TransferHandle): Int {
        val url = receiver.endpoint("v1/transfers/${handle.transferId}/complete")
        val signed = receiver.sign("POST", url, ByteArray(0))
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody())
            .withHeaders(signed)
            .build()
        return executeVerified(receiver, request, signed) { response ->
            json.decodeFromString<CompleteDto>(response.body.string()).receivedFiles
        }
    }

    /**
     * Streamed uploads sign [STREAMED_BODY] because the receiver writes the body straight
     * to disk and cannot hash it either.
     */
    private suspend fun ReceiverDevice.sign(
        method: String,
        url: HttpUrl,
        bodyForSigning: ByteArray,
    ): SignedRequest = requestSigner.sign(
        receiverId = identifier,
        method = method,
        path = url.encodedPath,
        bodyForSigning = bodyForSigning,
    ) ?: throw NotPairedException("Not paired with $name")

    private val ReceiverDevice.identifier: String
        get() = receiverId
            ?: throw NotPairedException("Receiver $name has not been identified yet")

    private fun Request.Builder.withHeaders(signed: SignedRequest): Request.Builder = apply {
        signed.headers.forEach { (name, value) -> header(name, value) }
    }

    /**
     * Runs [request] and refuses the result unless the responder proved it holds the
     * pairing secret. Without that check a device that claims a known `receiverId` is
     * indistinguishable from the real receiver.
     */
    private suspend fun <T> executeVerified(
        receiver: ReceiverDevice,
        request: Request,
        signed: SignedRequest,
        oversizedMessage: String? = null,
        transform: (Response) -> T,
    ): T = withContext(dispatchers.io) {
        httpClient.newCall(request).execute().use { response ->
            if (response.code == HTTP_UNAUTHORIZED) {
                throw NotPairedException("The receiver rejected this device's credentials")
            }
            if (!requestSigner.isProvenReceiver(
                    receiverId = receiver.identifier,
                    request = signed,
                    proofBase64 = response.header(HEADER_RECEIVER_SIGNATURE),
                )
            ) {
                throw ReceiverNotVerifiedException(
                    "${receiver.name} could not prove it is the device you paired with",
                )
            }
            if (response.code == HTTP_CONTENT_TOO_LARGE && oversizedMessage != null) {
                throw IOException(oversizedMessage)
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${request.url.encodedPath}")
            }
            read(response, transform)
        }
    }

    private suspend fun <T> execute(request: Request, transform: (Response) -> T): T =
        withContext(dispatchers.io) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for ${request.url.encodedPath}")
                }
                read(response, transform)
            }
        }

    /**
     * A reply that does not match the protocol is a failure of this exchange, not a
     * programming error, so callers only ever have to handle [IOException].
     */
    private fun <T> read(response: Response, transform: (Response) -> T): T =
        try {
            transform(response)
        } catch (malformed: SerializationException) {
            throw IOException("The receiver sent a reply this app could not read", malformed)
        }

    private fun ReceiverDevice.endpoint(path: String): HttpUrl {
        val builder = HttpUrl.Builder()
            .scheme("http")
            .host(host)
            .port(port)
        path.split('/').forEach(builder::addPathSegment)
        return builder.build()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val STREAMED_BODY = ByteArray(0)

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_CONTENT_TOO_LARGE = 413
        const val HEADER_RECEIVER_SIGNATURE = "X-PT-Receiver-Signature"
    }
}
