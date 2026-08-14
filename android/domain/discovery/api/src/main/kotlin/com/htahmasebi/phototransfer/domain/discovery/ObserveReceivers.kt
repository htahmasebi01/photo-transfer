package com.htahmasebi.phototransfer.domain.discovery

import com.htahmasebi.phototransfer.core.model.ReceiverDevice
import kotlinx.coroutines.flow.Flow

/**
 * Emits the receivers currently reachable on the local network, starting with an
 * empty list and updating as receivers appear and disappear.
 */
interface ObserveReceivers {

    operator fun invoke(): Flow<List<ReceiverDevice>>
}
