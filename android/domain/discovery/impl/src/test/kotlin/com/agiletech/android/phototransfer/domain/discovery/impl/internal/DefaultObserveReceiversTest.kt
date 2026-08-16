package com.agiletech.android.phototransfer.domain.discovery.impl.internal

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.data.discovery.DiscoveryEvent
import com.agiletech.android.phototransfer.data.discovery.ReceiverDiscoveryDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.junit.Test

class DefaultObserveReceiversTest {

    val mac = ReceiverDevice(name = "Mac", host = "10.0.0.2", port = 8080)

    val mini = ReceiverDevice(name = "Mini", host = "10.0.0.3", port = 9090)

    @Test
    fun `given no discovery events when observed then the first emission is empty`() = runTest {
        // given, when
        val emissions = observe().toList()

        // then
        emissions.first().`should be empty`()
    }

    @Test
    fun `given two receivers found when observed then they accumulate in discovery order`() = runTest {
        // given, when
        val emissions = observe(DiscoveryEvent.Found(mac), DiscoveryEvent.Found(mini)).toList()

        // then
        emissions `should be equal to` listOf(emptyList(), listOf(mac), listOf(mac, mini))
    }

    @Test
    fun `given a receiver that disappears when observed then it is dropped`() = runTest {
        // given, when
        val emissions = observe(
            DiscoveryEvent.Found(mac),
            DiscoveryEvent.Found(mini),
            DiscoveryEvent.Lost(mac.name),
        ).toList()

        // then
        emissions.last() `should be equal to` listOf(mini)
    }

    @Test
    fun `given the same receiver found twice when observed then it is not duplicated`() = runTest {
        // given, when
        val emissions = observe(DiscoveryEvent.Found(mac), DiscoveryEvent.Found(mac)).toList()

        // then
        emissions `should be equal to` listOf(emptyList(), listOf(mac))
    }

    @Test
    fun `given a receiver that reappears on a new port when observed then the stale address goes`() = runTest {
        // given
        val movedMac = mac.copy(port = 5555)

        // when
        val emissions = observe(DiscoveryEvent.Found(mac), DiscoveryEvent.Found(movedMac)).toList()

        // then
        emissions.last() `should be equal to` listOf(movedMac)
    }

    @Test
    fun `given an unknown receiver is lost when observed then nothing changes`() = runTest {
        // given, when
        val emissions = observe(DiscoveryEvent.Found(mac), DiscoveryEvent.Lost("Ghost")).toList()

        // then
        emissions `should be equal to` listOf(emptyList(), listOf(mac))
    }

    private fun observe(vararg events: DiscoveryEvent): Flow<List<ReceiverDevice>> =
        DefaultObserveReceivers(FakeReceiverDiscoveryDataSource(events.toList())).invoke()

    class FakeReceiverDiscoveryDataSource(
        private val events: List<DiscoveryEvent>,
    ) : ReceiverDiscoveryDataSource {
        override fun discover(): Flow<DiscoveryEvent> = events.asFlow()
    }
}
