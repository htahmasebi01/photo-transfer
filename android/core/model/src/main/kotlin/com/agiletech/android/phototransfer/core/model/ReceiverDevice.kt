package com.agiletech.android.phototransfer.core.model

/** A receiver discovered on the local network, resolved to an address we can reach. */
data class ReceiverDevice(
    val name: String,
    val host: String,
    val port: Int,
)
