package com.htahmasebi.phototransfer.data.discovery.impl.internal

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.htahmasebi.phototransfer.core.model.ReceiverDevice
import com.htahmasebi.phototransfer.data.discovery.DiscoveryEvent
import com.htahmasebi.phototransfer.data.discovery.ReceiverDiscoveryDataSource
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

internal const val SERVICE_TYPE = "_androidphototransfer._tcp"

internal class NsdReceiverDiscoveryDataSource @Inject constructor(
    private val nsdManager: NsdManager,
) : ReceiverDiscoveryDataSource {

    override fun discover(): Flow<DiscoveryEvent> = callbackFlow {
        // NsdManager allows only one in-flight resolve, so found services are
        // queued and resolved one at a time.
        val pendingResolves = Channel<NsdServiceInfo>(Channel.UNLIMITED)

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.startsWith(SERVICE_TYPE)) {
                    pendingResolves.trySend(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                trySend(DiscoveryEvent.Lost(serviceInfo.serviceName))
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close(IllegalStateException("Discovery failed to start: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        val resolveJob = launch {
            for (serviceInfo in pendingResolves) {
                resolve(serviceInfo)?.let { trySend(DiscoveryEvent.Found(it)) }
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

        awaitClose {
            resolveJob.cancel()
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        }
    }

    private suspend fun resolve(serviceInfo: NsdServiceInfo): ReceiverDevice? =
        suspendCancellableCoroutine { continuation ->
            @Suppress("DEPRECATION")
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = resolved.host?.hostAddress
                        val device = host?.let {
                            ReceiverDevice(
                                name = resolved.serviceName,
                                host = it,
                                port = resolved.port,
                            )
                        }
                        if (continuation.isActive) continuation.resume(device)
                    }

                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }
}
