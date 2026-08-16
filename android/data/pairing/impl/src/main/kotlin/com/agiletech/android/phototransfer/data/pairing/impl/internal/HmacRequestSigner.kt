package com.agiletech.android.phototransfer.data.pairing.impl.internal

import com.agiletech.android.phototransfer.data.pairing.RequestSigner
import com.agiletech.android.phototransfer.data.pairing.SignedRequest
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

internal class HmacRequestSigner @Inject constructor(
    private val store: PairingLocalStore,
    private val clock: SigningClock,
) : RequestSigner {

    private val random = SecureRandom()
    private val base64 = Base64.getEncoder()

    override suspend fun sign(
        receiverId: String,
        method: String,
        path: String,
        bodyForSigning: ByteArray,
    ): SignedRequest? {
        val deviceToken = store.deviceToken(receiverId) ?: return null
        val mac = store.mac(receiverId) ?: return null

        val timestampSeconds = clock.nowSeconds()
        val nonce = newNonce()
        val canonicalString = CanonicalRequest.string(
            method = method,
            path = path,
            timestampSeconds = timestampSeconds,
            nonce = nonce,
            bodySha256Hex = CanonicalRequest.sha256Hex(bodyForSigning),
        )
        val signature = base64.encodeToString(mac.doFinal(canonicalString.toByteArray(Charsets.UTF_8)))

        return SignedRequest(
            headers = mapOf(
                HEADER_DEVICE_TOKEN to deviceToken,
                HEADER_TIMESTAMP to timestampSeconds.toString(),
                HEADER_NONCE to nonce,
                HEADER_SIGNATURE to signature,
            ),
            method = method,
            path = path,
            nonce = nonce,
        )
    }

    override suspend fun isProvenReceiver(
        receiverId: String,
        request: SignedRequest,
        proofBase64: String?,
    ): Boolean {
        if (proofBase64 == null) return false
        val mac = store.mac(receiverId) ?: return false

        val expected = mac.doFinal(
            CanonicalRequest.receiverProofString(
                method = request.method,
                path = request.path,
                nonce = request.nonce,
            ).toByteArray(Charsets.UTF_8),
        )
        val provided = try {
            Base64.getDecoder().decode(proofBase64)
        } catch (malformed: IllegalArgumentException) {
            return false
        }

        return MessageDigest.isEqual(expected, provided)
    }

    private fun newNonce(): String {
        val bytes = ByteArray(NONCE_BYTES).also(random::nextBytes)
        return base64.encodeToString(bytes)
    }

    internal companion object {
        const val HEADER_DEVICE_TOKEN = "X-PT-Device"
        const val HEADER_TIMESTAMP = "X-PT-Timestamp"
        const val HEADER_NONCE = "X-PT-Nonce"
        const val HEADER_SIGNATURE = "X-PT-Signature"
        const val HEADER_RECEIVER_SIGNATURE = "X-PT-Receiver-Signature"

        private const val NONCE_BYTES = 16
    }
}

/** Wall-clock seconds, injected so signature freshness is testable. */
internal fun interface SigningClock {

    fun nowSeconds(): Long
}
