package com.htahmasebi.phototransfer.transfer

import com.htahmasebi.phototransfer.model.ReceiverDevice
import com.htahmasebi.phototransfer.model.SelectedFile
import com.htahmasebi.phototransfer.protocol.CompleteResponse
import com.htahmasebi.phototransfer.protocol.InfoResponse
import com.htahmasebi.phototransfer.protocol.ManifestFile
import com.htahmasebi.phototransfer.protocol.TransferCreatedResponse
import com.htahmasebi.phototransfer.protocol.TransferManifest
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class HttpTransferClient(
    private val httpClient: OkHttpClient,
    private val bodyFactory: (SelectedFile) -> RequestBody,
    private val ioDispatcher: CoroutineDispatcher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TransferClient {

    override suspend fun fetchInfo(receiver: ReceiverDevice): InfoResponse {
        val request = Request.Builder()
            .url(receiver.endpoint("v1/info"))
            .get()
            .build()
        return execute(request) { response ->
            json.decodeFromString<InfoResponse>(response.bodyString())
        }
    }

    override suspend fun createTransfer(receiver: ReceiverDevice, manifest: TransferManifest): String {
        val body = json.encodeToString(manifest).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(receiver.endpoint("v1/transfers"))
            .post(body)
            .build()
        return execute(request) { response ->
            json.decodeFromString<TransferCreatedResponse>(response.bodyString()).transferId
        }
    }

    override suspend fun uploadFile(
        receiver: ReceiverDevice,
        transferId: String,
        manifestFile: ManifestFile,
        source: SelectedFile,
        onBytesSent: (Long) -> Unit,
    ) {
        val body = ProgressRequestBody(bodyFactory(source), onBytesSent)
        val request = Request.Builder()
            .url(receiver.endpoint("v1/transfers/$transferId/files/${manifestFile.id}"))
            .put(body)
            .build()
        execute(request) { }
    }

    override suspend fun completeTransfer(receiver: ReceiverDevice, transferId: String): Int {
        val request = Request.Builder()
            .url(receiver.endpoint("v1/transfers/$transferId/complete"))
            .post(ByteArray(0).toRequestBody())
            .build()
        return execute(request) { response ->
            json.decodeFromString<CompleteResponse>(response.bodyString()).receivedFiles
        }
    }

    private suspend fun <T> execute(request: Request, transform: (Response) -> T): T =
        withContext(ioDispatcher) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for ${request.url.encodedPath}")
                }
                transform(response)
            }
        }

    private fun Response.bodyString(): String = body.string()

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
