package com.agiletech.android.phototransfer.data.pairing.impl.internal

import com.agiletech.android.phototransfer.data.pairing.SignedRequest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.`should not be equal to`
import org.junit.Test

class HmacRequestSignerTest {

    val store = FakePairingLocalStore()

    val tested = HmacRequestSigner(store, SigningClock { FIXED_TIMESTAMP })

    @Test
    fun `given a paired receiver when signing then headers match the vector the receiver expects`() = runTest {
        // given
        pair()

        // when
        val signed = tested.sign(
            receiverId = RECEIVER_ID,
            method = "POST",
            path = "/v1/transfers",
            bodyForSigning = ByteArray(0),
        )

        // then
        // Recomputed against the nonce the signer generated, since that is random.
        val expected = expectedSignature(
            nonce = signed!!.headers.getValue("X-PT-Nonce"),
            path = "/v1/transfers",
            method = "POST",
        )
        signed.headers.getValue("X-PT-Signature") `should be equal to` expected
        signed.headers.getValue("X-PT-Device") `should be equal to` "token-1"
        signed.headers.getValue("X-PT-Timestamp") `should be equal to` FIXED_TIMESTAMP.toString()
    }

    @Test
    fun `given no pairing when signing then nothing is produced`() = runTest {
        // given, when
        val signed = tested.sign(RECEIVER_ID, "POST", "/v1/transfers", ByteArray(0))

        // then
        signed.`should be null`()
    }

    @Test
    fun `given a forgotten pairing when signing then nothing is produced`() = runTest {
        // given
        pair()
        store.forget(RECEIVER_ID)

        // when
        val signed = tested.sign(RECEIVER_ID, "POST", "/v1/transfers", ByteArray(0))

        // then
        signed.`should be null`()
    }

    @Test
    fun `given a paired receiver when signing twice then each request gets a fresh nonce`() = runTest {
        // given
        pair()

        // when
        val first = tested.sign(RECEIVER_ID, "POST", "/v1/transfers", ByteArray(0))!!
        val second = tested.sign(RECEIVER_ID, "POST", "/v1/transfers", ByteArray(0))!!

        // then
        first.headers.getValue("X-PT-Nonce") `should not be equal to`
            second.headers.getValue("X-PT-Nonce")
        first.headers.getValue("X-PT-Signature") `should not be equal to`
            second.headers.getValue("X-PT-Signature")
    }

    @Test
    fun `given one nonce when two paths are signed then the signatures differ`() = runTest {
        // given
        pair()
        val nonce = "fixed-nonce"

        // when
        val forTransfers = expectedSignature(nonce, "/v1/transfers", "POST")
        val forComplete = expectedSignature(nonce, "/v1/transfers/t-1/complete", "POST")

        // then
        forTransfers `should not be equal to` forComplete
    }

    @Test
    fun `given one path when two bodies are signed then the signatures differ`() = runTest {
        // given
        pair()

        // when
        val withEmptyBody = expectedSignature("n", "/v1/transfers", "POST", ByteArray(0))
        val withBody = expectedSignature("n", "/v1/transfers", "POST", "{}".toByteArray())

        // then
        withEmptyBody `should not be equal to` withBody
    }

    @Test
    fun `given a proof over the request nonce when verified then the receiver is proven`() = runTest {
        // given
        pair()
        val signed = tested.sign(RECEIVER_ID, "POST", "/v1/verify", ByteArray(0))!!

        // when
        val proven = tested.isProvenReceiver(RECEIVER_ID, signed, receiverProof(signed))

        // then
        proven.`should be true`()
    }

    @Test
    fun `given no proof when verified then the receiver is not proven`() = runTest {
        // given
        pair()
        val signed = tested.sign(RECEIVER_ID, "POST", "/v1/verify", ByteArray(0))!!

        // when, then
        tested.isProvenReceiver(RECEIVER_ID, signed, null).`should be false`()
    }

    @Test
    fun `given the request signature when offered as a proof then it is not accepted`() = runTest {
        // given
        pair()
        val signed = tested.sign(RECEIVER_ID, "POST", "/v1/verify", ByteArray(0))!!

        // when
        val requestSignature = signed.headers.getValue("X-PT-Signature")

        // then
        tested.isProvenReceiver(RECEIVER_ID, signed, requestSignature).`should be false`()
    }

    @Test
    fun `given a proof for another nonce when verified then it is not accepted`() = runTest {
        // given
        pair()
        val first = tested.sign(RECEIVER_ID, "POST", "/v1/verify", ByteArray(0))!!
        val second = tested.sign(RECEIVER_ID, "POST", "/v1/verify", ByteArray(0))!!

        // when, then
        tested.isProvenReceiver(RECEIVER_ID, second, receiverProof(first)).`should be false`()
    }

    @Test
    fun `given a proof from another secret when verified then it is not accepted`() = runTest {
        // given
        pair()
        val signed = tested.sign(RECEIVER_ID, "POST", "/v1/verify", ByteArray(0))!!
        val impostor = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(ByteArray(32) { 9 }, "HmacSHA256"))
        }

        // when
        val forged = Base64.getEncoder().encodeToString(
            impostor.doFinal(proofString(signed).toByteArray(Charsets.UTF_8)),
        )

        // then
        tested.isProvenReceiver(RECEIVER_ID, signed, forged).`should be false`()
    }

    @Test
    fun `given malformed base64 when verified then it is rejected rather than thrown`() = runTest {
        // given
        pair()
        val signed = tested.sign(RECEIVER_ID, "POST", "/v1/verify", ByteArray(0))!!

        // when, then
        tested.isProvenReceiver(RECEIVER_ID, signed, "not base64!!").`should be false`()
    }

    @Test
    fun `given a forgotten pairing when a proof is verified then it is not accepted`() = runTest {
        // given
        pair()
        val signed = tested.sign(RECEIVER_ID, "POST", "/v1/verify", ByteArray(0))!!
        val proof = receiverProof(signed)

        // when
        store.forget(RECEIVER_ID)

        // then
        tested.isProvenReceiver(RECEIVER_ID, signed, proof).`should be false`()
    }

    private fun proofString(signed: SignedRequest) = CanonicalRequest.receiverProofString(
        method = signed.method,
        path = signed.path,
        nonce = signed.nonce,
    )

    private suspend fun receiverProof(signed: SignedRequest): String {
        val mac = store.mac(RECEIVER_ID)!!
        return Base64.getEncoder()
            .encodeToString(mac.doFinal(proofString(signed).toByteArray(Charsets.UTF_8)))
    }

    private suspend fun pair() {
        store.save(
            receiverId = RECEIVER_ID,
            receiverName = "Test Mac",
            deviceToken = "token-1",
            secret = ByteArray(32) { 7 },
        )
    }

    private suspend fun expectedSignature(
        nonce: String,
        path: String,
        method: String,
        body: ByteArray = ByteArray(0),
    ): String {
        val canonical = CanonicalRequest.string(
            method = method,
            path = path,
            timestampSeconds = FIXED_TIMESTAMP,
            nonce = nonce,
            bodySha256Hex = CanonicalRequest.sha256Hex(body),
        )
        val mac = store.mac(RECEIVER_ID)!!
        return Base64.getEncoder().encodeToString(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
    }

    companion object {
        const val RECEIVER_ID = "receiver-1"
        const val FIXED_TIMESTAMP = 1_700_000_000L
    }
}
