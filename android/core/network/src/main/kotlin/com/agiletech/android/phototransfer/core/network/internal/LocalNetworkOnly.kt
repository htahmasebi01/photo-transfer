package com.agiletech.android.phototransfer.core.network.internal

import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Resolves hostnames only to addresses on the local network.
 *
 * Everything this app talks to is a receiver on the same LAN, and it talks to it in the
 * clear. `network_security_config.xml` cannot express that restriction: `<domain>` matches a
 * hostname or a single IP literal, and the receiver's address is whatever DHCP handed it.
 *
 * Filtering here rather than before the call is what makes it airtight for hostnames.
 * OkHttp connects to the addresses this returns, so the addresses that were judged are the
 * addresses that are used, leaving no window for a second answer to differ from the first.
 */
internal class LocalNetworkOnlyDns(private val delegate: Dns = Dns.SYSTEM) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val local = delegate.lookup(hostname).filter(LocalAddresses::isLocal)
        if (local.isEmpty()) throw notLocal(hostname)
        return local
    }
}

/**
 * Refuses IP literals that are not on the local network.
 *
 * [LocalNetworkOnlyDns] cannot cover these: OkHttp does not resolve a host that is already
 * an address, so its `Dns` is never consulted. Receivers are reached by literal far more
 * often than by name, since addresses come from Bonjour or are typed in by hand.
 *
 * Where this is installed decides what it covers, so [LocalReceiverHttpClient] owns that
 * decision: an application interceptor is judged once per call and never sees a redirect.
 */
internal class LocalNetworkOnlyInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val literal = host.asIpLiteralOrNull()
        if (literal != null && !LocalAddresses.isLocal(literal)) throw notLocal(host)
        return chain.proceed(chain.request())
    }

    /** Parses without resolving, so a hostname is left to [LocalNetworkOnlyDns]. */
    private fun String.asIpLiteralOrNull(): InetAddress? {
        val looksNumeric = ':' in this || all { it.isDigit() || it == '.' }
        if (!looksNumeric) return null
        return try {
            InetAddress.getByName(this)
        } catch (unparseable: UnknownHostException) {
            null
        }
    }
}

internal object LocalAddresses {

    fun isLocal(address: InetAddress): Boolean = when {
        address.isLoopbackAddress || address.isLinkLocalAddress -> true
        address.isSiteLocalAddress -> true
        // isSiteLocalAddress covers the deprecated fec0::/10 but not the unique local
        // addresses that IPv6 actually uses.
        address is Inet6Address -> address.address[0].toInt() and 0xFE == 0xFC
        else -> false
    }
}

private fun notLocal(host: String) = UnknownHostException(
    "$host is not on the local network, and this client only sends photos to local receivers",
)
