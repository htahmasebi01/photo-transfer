package com.agiletech.android.phototransfer.data.pairing.impl.internal

import java.security.MessageDigest

/**
 * The bytes signed for an authenticated request.
 *
 * This must stay byte-identical to `RequestSignature.canonicalString` on the receiver,
 * so the field order and the newline separator are part of the wire protocol.
 */
internal object CanonicalRequest {

    fun string(
        method: String,
        path: String,
        timestampSeconds: Long,
        nonce: String,
        bodySha256Hex: String,
    ): String = listOf(
        method.uppercase(),
        path,
        timestampSeconds.toString(),
        nonce,
        bodySha256Hex,
    ).joinToString(separator = "\n")

    /**
     * The bytes the receiver signs to prove it holds the pairing secret.
     *
     * The `PT-RESPONSE-v1` prefix keeps this in a different namespace from a request
     * signature, so a captured request signature cannot be presented as a proof.
     * Must stay byte-identical to `RequestSignature.receiverProofString` on the receiver.
     */
    fun receiverProofString(
        method: String,
        path: String,
        nonce: String,
    ): String = listOf(
        RESPONSE_DOMAIN,
        method.uppercase(),
        path,
        nonce,
    ).joinToString(separator = "\n")

    fun sha256Hex(body: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(body)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private const val RESPONSE_DOMAIN = "PT-RESPONSE-v1"
}
