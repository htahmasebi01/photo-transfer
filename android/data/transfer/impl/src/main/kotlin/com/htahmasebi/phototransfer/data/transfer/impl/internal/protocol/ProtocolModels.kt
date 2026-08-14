package com.htahmasebi.phototransfer.data.transfer.impl.internal.protocol

import kotlinx.serialization.Serializable

internal const val PROTOCOL_VERSION = 1

@Serializable
internal data class TransferManifest(
    val protocolVersion: Int,
    val files: List<ManifestFile>,
)

@Serializable
internal data class ManifestFile(
    val id: String,
    val name: String,
    val mediaType: String,
    val size: Long?,
)

@Serializable
internal data class InfoResponse(
    val protocolVersion: Int,
    val receiverName: String,
)

@Serializable
internal data class TransferCreatedResponse(
    val transferId: String,
)

@Serializable
internal data class CompleteResponse(
    val receivedFiles: Int,
)
