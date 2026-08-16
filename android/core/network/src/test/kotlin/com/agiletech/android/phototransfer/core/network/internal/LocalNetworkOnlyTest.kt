package com.agiletech.android.phototransfer.core.network.internal

import java.net.InetAddress
import java.net.UnknownHostException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.amshove.kluent.shouldThrow
import org.amshove.kluent.withMessage
import org.junit.After
import org.junit.Before
import org.junit.Test

class LocalAddressesTest {

    @Test
    fun `given a private ipv4 address when judged then it is local`() {
        for (host in listOf("192.168.1.20", "10.0.0.4", "172.16.5.9", "169.254.3.3", "127.0.0.1")) {
            // given, when
            val isLocal = LocalAddresses.isLocal(InetAddress.getByName(host))

            // then
            isLocal.`should be true`()
        }
    }

    @Test
    fun `given a unique local or link local ipv6 address when judged then it is local`() {
        for (host in listOf("fd12:3456:789a::1", "fe80::1", "::1")) {
            // given, when
            val isLocal = LocalAddresses.isLocal(InetAddress.getByName(host))

            // then
            isLocal.`should be true`()
        }
    }

    @Test
    fun `given a public address when judged then it is not local`() {
        for (host in listOf("93.184.216.34", "8.8.8.8", "2606:2800:220:1::1")) {
            // given, when
            val isLocal = LocalAddresses.isLocal(InetAddress.getByName(host))

            // then
            isLocal.`should be false`()
        }
    }
}

class LocalNetworkOnlyDnsTest {

    internal val tested = LocalNetworkOnlyDns()

    /**
     * A hostname resolving to a mix keeps only what is local, so the connection cannot leave
     * the network even when the answer includes a public address.
     */
    @Test
    fun `given an answer mixing local and public addresses when resolved then only local remain`() {
        // given
        val mixed = LocalNetworkOnlyDns(delegate = fakeDns("93.184.216.34", "192.168.1.20"))

        // when
        val resolved = mixed.lookup("receiver.local")

        // then
        resolved `should be equal to` listOf(InetAddress.getByName("192.168.1.20"))
    }

    @Test
    fun `given an answer with nothing local when resolved then it is refused`() {
        // given
        val remote = LocalNetworkOnlyDns(delegate = fakeDns("93.184.216.34"))

        // when, then
        invoking { remote.lookup("rebound.example") } shouldThrow UnknownHostException::class
    }

    @Test
    fun `given a loopback literal when resolved then it is returned unchanged`() {
        // given, when
        val resolved = tested.lookup("127.0.0.1")

        // then
        resolved `should be equal to` listOf(InetAddress.getByName("127.0.0.1"))
    }

    private fun fakeDns(vararg addresses: String) = Dns { _ ->
        addresses.map(InetAddress::getByName)
    }
}

class LocalNetworkOnlyInterceptorTest {

    val server = MockWebServer()

    val client = OkHttpClient.Builder()
        .dns(LocalNetworkOnlyDns())
        .addInterceptor(LocalNetworkOnlyInterceptor())
        .build()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `given a receiver on this machine when called then plain http is allowed`() {
        // given
        server.enqueue(MockResponse(code = 204))

        // when
        val response = client.newCall(Request.Builder().url(server.url("/v1/info")).build())
            .execute()

        // then
        response.code `should be equal to` 204
    }

    /**
     * OkHttp never resolves a literal, so this is the only place a typed-in or advertised
     * public address can be stopped.
     */
    @Test
    fun `given a public ipv4 literal when called then it is refused before connecting`() {
        // given
        val request = Request.Builder().url("http://93.184.216.34/v1/info").build()

        // when, then
        invoking { client.newCall(request).execute() } shouldThrow
            UnknownHostException::class withMessage
            "93.184.216.34 is not on the local network, and this client only sends photos " +
            "to local receivers"
    }

    @Test
    fun `given a public ipv6 literal when called then it is refused before connecting`() {
        // given
        val request = Request.Builder().url("http://[2606:2800:220:1::1]/v1/info").build()

        // when, then
        invoking { client.newCall(request).execute() } shouldThrow UnknownHostException::class
    }

    @Test
    fun `given a hostname when called then the literal check leaves it to the resolver`() {
        // given
        val request = Request.Builder().url("http://receiver.invalid/v1/info").build()

        // when, then
        invoking { client.newCall(request).execute() } shouldThrow UnknownHostException::class
    }
}
