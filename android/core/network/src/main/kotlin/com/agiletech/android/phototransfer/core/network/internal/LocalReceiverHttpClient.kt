package com.agiletech.android.phototransfer.core.network.internal

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * The one client every receiver call goes through.
 *
 * It lives here rather than in the Hilt module so tests exercise the configuration that
 * ships. The local-network rule depends as much on how the guards are installed as on what
 * they check: an application interceptor alone misses redirect hops.
 */
internal object LocalReceiverHttpClient {

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L

    fun create(): OkHttpClient {
        val localNetworkOnly = LocalNetworkOnlyInterceptor()
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Uploads are as slow as the photo is large; no write deadline.
            .writeTimeout(0, TimeUnit.SECONDS)
            // The protocol has no redirects, and following one would re-send the photo body
            // wherever a hostile receiver points. OkHttp follows redirects *below* the
            // application interceptor, so a redirected request is never judged there.
            .followRedirects(false)
            .followSslRedirects(false)
            // Clients derived with newBuilder() inherit all of this, so the rule cannot be
            // sidestepped by adjusting timeouts for a particular call.
            .dns(LocalNetworkOnlyDns())
            // Installed twice on purpose. As an application interceptor it refuses a
            // non-local literal before a socket is opened. As a network interceptor it also
            // runs per hop, so nothing is written to a non-local address even if redirects
            // or a proxy are turned on later, though by then the connection is already open.
            .addInterceptor(localNetworkOnly)
            .addNetworkInterceptor(localNetworkOnly)
            .build()
    }
}
