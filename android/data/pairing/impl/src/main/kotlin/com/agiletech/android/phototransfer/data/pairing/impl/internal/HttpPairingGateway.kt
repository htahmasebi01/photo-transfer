package com.agiletech.android.phototransfer.data.pairing.impl.internal

import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import com.agiletech.android.phototransfer.core.model.PhotoTransferProtocol
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.data.pairing.PairingGateway
import com.agiletech.android.phototransfer.data.pairing.PairingOutcome
import com.agiletech.android.phototransfer.data.pairing.impl.internal.protocol.PairDto
import com.agiletech.android.phototransfer.data.pairing.impl.internal.protocol.PairRequestParamsDto
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class HttpPairingGateway @Inject constructor(
    httpClient: OkHttpClient,
    private val json: Json,
    private val store: PairingLocalStore,
    private val deviceName: PairingDeviceName,
    private val dispatchers: Dispatchers,
) : PairingGateway {

    // The receiver holds the request open while the user approves it, which outlives
    // the default read timeout.
    private val pairingClient = httpClient.newBuilder()
        .readTimeout(APPROVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun pair(
        receiver: ReceiverDevice,
        pairingCode: String,
        replaceExisting: Boolean,
    ): PairingOutcome =
        withContext(dispatchers.io) {
            val body = PairRequestParamsDto(
                protocolVersion = PhotoTransferProtocol.VERSION,
                deviceId = store.deviceId(),
                deviceName = deviceName.value,
                pairingCode = pairingCode,
            )
            val request = Request.Builder()
                .url(receiver.pairEndpoint())
                .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))
                .build()

            try {
                pairingClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        HTTP_OK -> savePairing(
                            responseBody = response.body.string(),
                            expectedReceiverId = receiver.receiverId,
                            replaceExisting = replaceExisting,
                        )
                        HTTP_UNAUTHORIZED -> PairingOutcome.InvalidCode
                        HTTP_FORBIDDEN -> PairingOutcome.Declined
                        HTTP_TOO_MANY_REQUESTS -> PairingOutcome.Throttled
                        HTTP_GATEWAY_TIMEOUT -> PairingOutcome.TimedOut
                        else -> PairingOutcome.Failed("Receiver replied ${response.code}")
                    }
                }
            } catch (error: IOException) {
                PairingOutcome.Failed(error.message ?: "Could not reach the receiver")
            }
        }

    /**
     * Nothing is written until the response is shown to be about the receiver being paired
     * with, and to not be quietly displacing a pairing that already exists.
     *
     * The `receiverId` in the response decides which keystore alias is written, and the
     * responder chooses it. Left unchecked, a device can advertise an id the phone has never
     * seen, so the phone offers to pair, and then issue credentials under a *different* id
     * to overwrite the pairing for a receiver it has nothing to do with.
     */
    private suspend fun savePairing(
        responseBody: String,
        expectedReceiverId: String?,
        replaceExisting: Boolean,
    ): PairingOutcome {
        val paired = decodePairing(responseBody) ?: return unreadableReply
        val secret = decodeSecret(paired.secretBase64) ?: return unreadableReply

        if (expectedReceiverId != null && paired.receiverId != expectedReceiverId) {
            return PairingOutcome.IdentityMismatch(
                expected = expectedReceiverId,
                claimed = paired.receiverId,
            )
        }
        if (!replaceExisting && store.deviceToken(paired.receiverId) != null) {
            return PairingOutcome.AlreadyPaired(
                receiverId = paired.receiverId,
                receiverName = paired.receiverName,
            )
        }

        store.save(
            receiverId = paired.receiverId,
            receiverName = paired.receiverName,
            deviceToken = paired.deviceToken,
            secret = secret,
        )
        return PairingOutcome.Paired(receiverId = paired.receiverId, receiverName = paired.receiverName)
    }

    /**
     * Anything on the network can answer a pairing request, so a reply that does not match
     * the protocol is an ordinary failure rather than a reason to bring the app down.
     */
    private fun decodePairing(responseBody: String): PairDto? =
        try {
            json.decodeFromString<PairDto>(responseBody)
        } catch (malformed: SerializationException) {
            null
        }

    private fun decodeSecret(secretBase64: String): ByteArray? =
        try {
            Base64.getDecoder().decode(secretBase64)
        } catch (malformed: IllegalArgumentException) {
            null
        }

    private fun ReceiverDevice.pairEndpoint(): HttpUrl = HttpUrl.Builder()
        .scheme("http")
        .host(host)
        .port(port)
        .addPathSegment("v1")
        .addPathSegment("pair")
        .build()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val unreadableReply =
            PairingOutcome.Failed("The receiver sent a reply this app could not read")

        const val APPROVAL_TIMEOUT_SECONDS = 90L
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429

        // The receiver reports an unapproved request as 504 rather than 408, because
        // OkHttp silently resends a 408 and the pairing code is single use.
        const val HTTP_GATEWAY_TIMEOUT = 504
    }
}

/** The name shown in the receiver's approval prompt. */
internal data class PairingDeviceName(val value: String)
