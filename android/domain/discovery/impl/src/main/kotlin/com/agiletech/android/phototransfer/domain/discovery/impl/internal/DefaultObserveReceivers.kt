package com.agiletech.android.phototransfer.domain.discovery.impl.internal

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.data.discovery.DiscoveryEvent
import com.agiletech.android.phototransfer.data.discovery.ReceiverDiscoveryDataSource
import com.agiletech.android.phototransfer.domain.discovery.ObserveReceivers
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

internal class DefaultObserveReceivers @Inject constructor(
    private val dataSource: ReceiverDiscoveryDataSource,
) : ObserveReceivers {

    override fun invoke(): Flow<List<ReceiverDevice>> = dataSource.discover()
        .scan(emptyMap<String, ReceiverDevice>()) { receivers, event ->
            when (event) {
                is DiscoveryEvent.Found -> receivers + (event.device.name to event.device)
                is DiscoveryEvent.Lost -> receivers - event.serviceName
            }
        }
        .map { it.values.toList() }
        .distinctUntilChanged()
}
