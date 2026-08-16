package com.agiletech.android.phototransfer.data.pairing.impl.internal

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be equal to`
import org.junit.Test

class CanonicalRequestTest {

    /**
     * Locks the wire format down with a vector the receiver asserts too. If this breaks,
     * one platform changed the canonical string and every signed request will be rejected.
     */
    @Test
    fun `given a known request when the canonical string is built then it matches the shared vector`() {
        // given, when
        val canonical = CanonicalRequest.string(
            method = "POST",
            path = "/v1/transfers",
            timestampSeconds = 1_700_000_000,
            nonce = "test-nonce",
            bodySha256Hex = CanonicalRequest.sha256Hex(ByteArray(0)),
        )

        // then
        canonical `should be equal to` "POST\n" +
            "/v1/transfers\n" +
            "1700000000\n" +
            "test-nonce\n" +
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }

    /**
     * The other half of the shared contract. If only one platform changes this string, the
     * sender concludes the receiver is an impostor and refuses to send anything.
     */
    @Test
    fun `given a known request when the proof string is built then it matches the shared vector`() {
        // given
        val proofString = CanonicalRequest.receiverProofString(
            method = "POST",
            path = "/v1/verify",
            nonce = "test-nonce",
        )

        // when
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256"))
        }
        val signature = Base64.getEncoder()
            .encodeToString(mac.doFinal(proofString.toByteArray(Charsets.UTF_8)))

        // then
        proofString `should be equal to` "PT-RESPONSE-v1\n" +
            "POST\n" +
            "/v1/verify\n" +
            "test-nonce"
        signature `should be equal to` "xRGJtiA1mw6eZBFsk9HYA8N/NTpSKr5pMTNEmia0lpU="
    }

    /**
     * A request signature and a proof over the same request must never coincide, or a
     * captured request could be replayed back as the receiver's answer.
     */
    @Test
    fun `given one request when both strings are built then the proof is in another namespace`() {
        // given
        val request = CanonicalRequest.string(
            method = "POST",
            path = "/v1/verify",
            timestampSeconds = 1_700_000_000,
            nonce = "test-nonce",
            bodySha256Hex = CanonicalRequest.sha256Hex(ByteArray(0)),
        )

        // when
        val proof = CanonicalRequest.receiverProofString("POST", "/v1/verify", "test-nonce")

        // then
        request `should not be equal to` proof
    }

    @Test
    fun `given a lowercase method when the canonical string is built then it is uppercased`() {
        // given, when
        val lowercase = CanonicalRequest.string("post", "/v1/transfers", 1, "n", "hash")
        val uppercase = CanonicalRequest.string("POST", "/v1/transfers", 1, "n", "hash")

        // then
        lowercase `should be equal to` uppercase
    }

    @Test
    fun `given a body when it is hashed then the hex is lower case`() {
        // given, when
        val empty = CanonicalRequest.sha256Hex(ByteArray(0))
        val hello = CanonicalRequest.sha256Hex("hello".toByteArray())

        // then
        empty `should be equal to`
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        hello `should be equal to`
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    }
}
