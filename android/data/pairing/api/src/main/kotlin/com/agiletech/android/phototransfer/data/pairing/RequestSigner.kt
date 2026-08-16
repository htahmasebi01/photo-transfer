package com.agiletech.android.phototransfer.data.pairing

/**
 * The headers that prove a request came from a paired device, plus the nonce they carry.
 *
 * The nonce is exposed because the receiver's proof is bound to it, so the caller needs it
 * to check the response.
 */
data class SignedRequest(
    val headers: Map<String, String>,
    val method: String,
    val path: String,
    val nonce: String,
)

/**
 * Signs outgoing requests and checks the receiver's reply.
 *
 * The pairing secret never leaves the implementation, so callers can sign and verify
 * without ever holding key material.
 */
interface RequestSigner {

    /**
     * Returns what to attach, or null when this device is not paired with [receiverId].
     *
     * [bodyForSigning] is empty for streamed uploads, which the receiver cannot hash either.
     */
    suspend fun sign(
        receiverId: String,
        method: String,
        path: String,
        bodyForSigning: ByteArray,
    ): SignedRequest?

    /**
     * Whether [proofBase64] proves the responder holds the secret paired with [receiverId].
     *
     * Signing authenticates this device to the receiver. Nothing authenticates the receiver
     * back, and `receiverId` is broadcast in cleartext, so without this check any device on
     * the network can claim a known `receiverId` and be treated as already paired.
     */
    suspend fun isProvenReceiver(
        receiverId: String,
        request: SignedRequest,
        proofBase64: String?,
    ): Boolean
}
