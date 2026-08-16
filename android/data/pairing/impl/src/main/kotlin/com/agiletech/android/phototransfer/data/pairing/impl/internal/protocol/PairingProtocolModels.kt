package com.agiletech.android.phototransfer.data.pairing.impl.internal.protocol

import kotlinx.serialization.Serializable

@Serializable
internal data class PairRequestParamsDto(
    val protocolVersion: Int,
    val deviceId: String,
    val deviceName: String,
    val pairingCode: String,
)

@Serializable
internal data class PairDto(
    val receiverId: String,
    val receiverName: String,
    val deviceToken: String,
    val secretBase64: String,
)
