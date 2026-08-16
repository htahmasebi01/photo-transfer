package com.agiletech.android.phototransfer.core.model

/**
 * A receiver discovered on the local network, resolved to an address we can reach.
 *
 * [receiverId] identifies which stored pairing signs requests to this receiver. It is null for
 * a manually entered address, where it has to be fetched from the receiver before sending.
 */
data class ReceiverDevice(
    val name: String,
    val host: String,
    val port: Int,
    val receiverId: String? = null,
)
