package com.htahmasebi.phototransfer.data.transfer.impl.internal

import com.htahmasebi.phototransfer.core.coroutines.DispatcherProvider
import com.htahmasebi.phototransfer.core.model.ReceiverDevice
import com.htahmasebi.phototransfer.core.model.SelectedFile
import com.htahmasebi.phototransfer.data.media.MediaByteSource
import com.htahmasebi.phototransfer.data.transfer.PendingUpload
import com.htahmasebi.phototransfer.data.transfer.ReceiverInfo
import com.htahmasebi.phototransfer.data.transfer.TransferGateway
import com.htahmasebi.phototransfer.data.transfer.TransferHandle
import com.htahmasebi.phototransfer.data.transfer.impl.internal.protocol.CompleteResponse
import com.htahmasebi.phototransfer.data.transfer.impl.internal.protocol.InfoResponse
import com.htahmasebi.phototransfer.data.transfer.impl.internal.protocol.ManifestFile
import com.htahmasebi.phototransfer.data.transfer.impl.internal.protocol.PROTOCOL_VERSION
import com.htahmasebi.phototransfer.data.transfer.impl.internal.protocol.TransferCreatedResponse
import com.htahmasebi.phototransfer.data.transfer.impl.internal.protocol.TransferManifest
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.withContext
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
    private val dispatchers: DispatcherProvider,
) : TransferGateway {

    override suspend fun fetchReceiverInfo(receiver: ReceiverDevice): ReceiverInfo {
        val request = Request.Builder()
            .url(receiver.endpoint("v1/info"))
            .get()
            .build()
        return execute(request) { response ->
            val info = json.decodeFromString<InfoResponse>(response.body.string())
            ReceiverInfo(protocolVersion = info.protocolVersion, name = info.receiverName)
        }
    }

    override suspend fun createTransfer(
        receiver: ReceiverDevice,
        files: List<SelectedFile>,
    ): TransferHandle {
        val uploads = files.mapIndexed { index, file ->
            PendingUpload(fileId = "file-${index + 1}", file = file)
        }
        val manifest = TransferManifest(
            protocolVersion = PROTOCOL_VERSION,
            files = uploads.map { upload ->
                ManifestFile(
                    id = upload.fileId,
                    name = upload.file.displayName,
                    mediaType = upload.file.mediaType,
                    size = upload.file.size,
                )
            },
        )
        val request = Request.Builder()
            .url(receiver.endpoint("v1/transfers"))
            .post(json.encodeToString(manifest).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val transferId = execute(request) { response ->
            json.decodeFromString<TransferCreatedResponse>(response.body.string()).transferId
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
        val request = Request.Builder()
            .url(receiver.endpoint("v1/transfers/${handle.transferId}/files/${upload.fileId}"))
            .put(body)
            .build()
        execute(request) { }
    }

    override suspend fun completeTransfer(receiver: ReceiverDevice, handle: TransferHandle): Int {
        val request = Request.Builder()
            .url(receiver.endpoint("v1/transfers/${handle.transferId}/complete"))
            .post(ByteArray(0).toRequestBody())
            .build()
        return execute(request) { response ->
            json.decodeFromString<CompleteResponse>(response.body.string()).receivedFiles
        }
    }

    private suspend fun <T> execute(request: Request, transform: (Response) -> T): T =
        withContext(dispatchers.io) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for ${request.url.encodedPath}")
                }
                transform(response)
            }
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
    }
}
