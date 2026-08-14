package com.htahmasebi.phototransfer.domain.discovery.impl.internal

import com.htahmasebi.phototransfer.core.model.ReceiverDevice
import com.htahmasebi.phototransfer.data.discovery.DiscoveryEvent
import com.htahmasebi.phototransfer.data.discovery.ReceiverDiscoveryDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultObserveReceiversTest {

    private val mac = ReceiverDevice(name = "Mac", host = "10.0.0.2", port = 8080)
    private val mini = ReceiverDevice(name = "Mini", host = "10.0.0.3", port = 9090)

    @Test
    fun `starts empty so the UI can show a searching state`() = runTest {
        val emissions = observe().toList()

        assertEquals(emptyList<ReceiverDevice>(), emissions.first())
    }

    @Test
    fun `accumulates found receivers in discovery order`() = runTest {
        val emissions = observe(DiscoveryEvent.Found(mac), DiscoveryEvent.Found(mini)).toList()

        assertEquals(listOf(emptyList(), listOf(mac), listOf(mac, mini)), emissions)
    }

    @Test
    fun `drops receivers that disappear`() = runTest {
        val emissions = observe(
            DiscoveryEvent.Found(mac),
            DiscoveryEvent.Found(mini),
            DiscoveryEvent.Lost(mac.name),
        ).toList()

        assertEquals(listOf(mini), emissions.last())
    }

    @Test
    fun `rediscovering the same receiver does not duplicate it`() = runTest {
        val emissions = observe(DiscoveryEvent.Found(mac), DiscoveryEvent.Found(mac)).toList()

        assertEquals(listOf(emptyList(), listOf(mac)), emissions)
    }

    @Test
    fun `a receiver that reappears on a new port replaces the stale address`() = runTest {
        val movedMac = mac.copy(port = 5555)

        val emissions = observe(DiscoveryEvent.Found(mac), DiscoveryEvent.Found(movedMac)).toList()

        assertEquals(listOf(movedMac), emissions.last())
    }

    @Test
    fun `losing an unknown receiver changes nothing`() = runTest {
        val emissions = observe(DiscoveryEvent.Found(mac), DiscoveryEvent.Lost("Ghost")).toList()

        assertEquals(listOf(emptyList(), listOf(mac)), emissions)
    }

    private fun observe(vararg events: DiscoveryEvent): Flow<List<ReceiverDevice>> =
        DefaultObserveReceivers(FakeReceiverDiscoveryDataSource(events.toList())).invoke()

    private class FakeReceiverDiscoveryDataSource(
        private val events: List<DiscoveryEvent>,
    ) : ReceiverDiscoveryDataSource {
        override fun discover(): Flow<DiscoveryEvent> = events.asFlow()
    }
}
