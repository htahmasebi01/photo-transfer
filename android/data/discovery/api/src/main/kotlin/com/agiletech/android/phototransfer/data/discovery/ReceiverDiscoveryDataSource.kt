package com.agiletech.android.phototransfer.data.discovery

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import kotlinx.coroutines.flow.Flow

/**
 * Raw discovery events from the network. Aggregating them into the current set
 * of reachable receivers is the domain layer's job.
 */
interface ReceiverDiscoveryDataSource {

    fun discover(): Flow<DiscoveryEvent>
}

sealed interface DiscoveryEvent {

    data class Found(val device: ReceiverDevice) : DiscoveryEvent

    data class Lost(val serviceName: String) : DiscoveryEvent
}
