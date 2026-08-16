package com.agiletech.android.phototransfer.data.pairing.impl.internal

import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.data.pairing.PairingOutcome
import com.agiletech.android.phototransfer.data.pairing.impl.internal.protocol.PairRequestParamsDto
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be instance of`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be null`
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpPairingGatewayTest {

    val server = MockWebServer()

    val json = Json { ignoreUnknownKeys = true }

    val store = FakePairingLocalStore()

    val receiver by lazy {
        ReceiverDevice(name = "Mock Mac", host = server.hostName, port = server.port)
    }

    val tested by lazy {
        val dispatcher = UnconfinedTestDispatcher()
        HttpPairingGateway(
            httpClient = OkHttpClient(),
            json = json,
            store = store,
            deviceName = PairingDeviceName("Test Pixel"),
            dispatchers = Dispatchers(main = dispatcher, io = dispatcher, default = dispatcher),
        )
    }

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `given an approved device when pairing then credentials are stored`() = runTest {
        // given
        val secret = Base64.getEncoder().encodeToString(ByteArray(32) { 3 })
        server.enqueue(
            jsonResponse(
                200,
                """
                {"receiverId":"r-1","receiverName":"Hamid's MacBook",
                 "deviceToken":"token-1","secretBase64":"$secret"}
                """.trimIndent(),
            ),
        )

        // when
        val outcome = tested.pair(receiver, "123456")

        // then
        outcome `should be equal to` PairingOutcome.Paired(
            receiverId = "r-1",
            receiverName = "Hamid's MacBook",
        )
        store.deviceToken("r-1") `should be equal to` "token-1"
        store.receiverName("r-1") `should be equal to` "Hamid's MacBook"
        store.mac("r-1").`should not be null`()
    }

    @Test
    fun `given a pairing code when pairing then the request carries the code, name and version`() = runTest {
        // given
        server.enqueue(jsonResponse(403, ""))

        // when
        tested.pair(receiver, "123456")

        // then
        val request = server.takeRequest()
        request.method `should be equal to` "POST"
        request.url.encodedPath `should be equal to` "/v1/pair"

        val body = json.decodeFromString<PairRequestParamsDto>(request.body!!.utf8())
        body.pairingCode `should be equal to` "123456"
        body.deviceName `should be equal to` "Test Pixel"
        body.deviceId `should be equal to` "device-1"
        body.protocolVersion `should be equal to` 1
    }

    @Test
    fun `given a rejected code when pairing then the outcome is InvalidCode`() = runTest {
        // given
        server.enqueue(jsonResponse(401, ""))

        // when
        val outcome = tested.pair(receiver, "000000")

        // then
        outcome `should be equal to` PairingOutcome.InvalidCode
        store.deviceToken("r-1").`should be null`()
    }

    @Test
    fun `given a declined device when pairing then the outcome is Declined`() = runTest {
        // given
        server.enqueue(jsonResponse(403, ""))

        // when, then
        tested.pair(receiver, "123456") `should be equal to` PairingOutcome.Declined
    }

    @Test
    fun `given nobody approves when pairing then the outcome is TimedOut`() = runTest {
        // given
        server.enqueue(jsonResponse(504, ""))

        // when, then
        tested.pair(receiver, "123456") `should be equal to` PairingOutcome.TimedOut
    }

    /**
     * OkHttp resends a 408 by itself, which would burn a second pairing code, so the
     * receiver must never use it for the approval timeout.
     */
    @Test
    fun `given a 408 when pairing then it is not treated as a pairing timeout`() = runTest {
        // given
        server.enqueue(jsonResponse(408, ""))
        server.enqueue(jsonResponse(403, ""))

        // when, then
        tested.pair(receiver, "123456") `should be equal to` PairingOutcome.Declined
    }

    @Test
    fun `given a rate limited receiver when pairing then the outcome is Throttled`() = runTest {
        // given
        server.enqueue(jsonResponse(429, ""))

        // when, then
        tested.pair(receiver, "123456") `should be equal to` PairingOutcome.Throttled
    }

    /**
     * The responder picks the `receiverId` the credentials are stored under. Accepting one
     * it was not asked about would let any device on the network overwrite the pairing for
     * a receiver it has nothing to do with.
     */
    @Test
    fun `given credentials for another receiver when pairing then nothing is stored`() = runTest {
        // given
        store.save("real-mac", "Hamid's MacBook", "token-real", ByteArray(32) { 1 })
        server.enqueue(pairedResponse(receiverId = "real-mac", deviceToken = "token-impostor"))

        // when
        val outcome = tested.pair(identified("advertised-id"), "123456")

        // then
        outcome `should be equal to` PairingOutcome.IdentityMismatch(
            expected = "advertised-id",
            claimed = "real-mac",
        )
        store.deviceToken("real-mac") `should be equal to` "token-real"
    }

    @Test
    fun `given credentials for the receiver being paired with when pairing then they are stored`() = runTest {
        // given
        server.enqueue(pairedResponse(receiverId = "r-1", deviceToken = "token-1"))

        // when
        val outcome = tested.pair(identified("r-1"), "123456")

        // then
        outcome `should be equal to` PairingOutcome.Paired(
            receiverId = "r-1",
            receiverName = "Hamid's MacBook",
        )
        store.deviceToken("r-1") `should be equal to` "token-1"
    }

    @Test
    fun `given an existing pairing when pairing then it is reported rather than replaced`() = runTest {
        // given
        store.save("r-1", "Hamid's MacBook", "token-old", ByteArray(32) { 1 })
        server.enqueue(pairedResponse(receiverId = "r-1", deviceToken = "token-new"))

        // when
        val outcome = tested.pair(identified("r-1"), "123456")

        // then
        outcome `should be equal to` PairingOutcome.AlreadyPaired(
            receiverId = "r-1",
            receiverName = "Hamid's MacBook",
        )
        store.deviceToken("r-1") `should be equal to` "token-old"
    }

    @Test
    fun `given a confirmed replacement when pairing then the existing pairing is replaced`() = runTest {
        // given
        store.save("r-1", "Hamid's MacBook", "token-old", ByteArray(32) { 1 })
        server.enqueue(pairedResponse(receiverId = "r-1", deviceToken = "token-new"))

        // when
        val outcome = tested.pair(identified("r-1"), "123456", replaceExisting = true)

        // then
        outcome `should be instance of` PairingOutcome.Paired::class
        store.deviceToken("r-1") `should be equal to` "token-new"
    }

    @Test
    fun `given an unexpected status when pairing then the outcome is Failed`() = runTest {
        // given
        server.enqueue(jsonResponse(500, ""))

        // when
        val outcome = tested.pair(receiver, "123456")

        // then
        outcome `should be equal to` PairingOutcome.Failed("Receiver replied 500")
    }

    @Test
    fun `given an unreachable receiver when pairing then the outcome is Failed`() = runTest {
        // given
        val unreachable = receiver
        server.close()

        // when
        val outcome = tested.pair(unreachable, "123456")

        // then
        outcome `should be instance of` PairingOutcome.Failed::class
    }

    private fun identified(receiverId: String) = receiver.copy(receiverId = receiverId)

    private fun pairedResponse(receiverId: String, deviceToken: String) = jsonResponse(
        200,
        """
        {"receiverId":"$receiverId","receiverName":"Hamid's MacBook","deviceToken":"$deviceToken",
         "secretBase64":"${Base64.getEncoder().encodeToString(ByteArray(32) { 9 })}"}
        """.trimIndent(),
    )

    private fun jsonResponse(code: Int, body: String) = MockResponse(
        code = code,
        headers = Headers.headersOf("Content-Type", "application/json"),
        body = body,
    )
}
