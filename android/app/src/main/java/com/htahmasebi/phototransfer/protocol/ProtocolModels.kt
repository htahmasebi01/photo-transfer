package com.htahmasebi.phototransfer.protocol

import kotlinx.serialization.Serializable

const val PROTOCOL_VERSION = 1

@Serializable
data class TransferManifest(
    val protocolVersion: Int,
    val files: List<ManifestFile>,
)

@Serializable
data class ManifestFile(
    val id: String,
    val name: String,
    val mediaType: String,
    val size: Long?,
)

@Serializable
data class InfoResponse(
    val protocolVersion: Int,
    val receiverName: String,
)

@Serializable
data class TransferCreatedResponse(
    val transferId: String,
)

@Serializable
data class CompleteResponse(
    val receivedFiles: Int,
)
