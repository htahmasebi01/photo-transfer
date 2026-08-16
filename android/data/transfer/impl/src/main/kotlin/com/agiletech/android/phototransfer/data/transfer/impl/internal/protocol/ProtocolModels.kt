package com.agiletech.android.phototransfer.data.transfer.impl.internal.protocol

import com.agiletech.android.phototransfer.core.model.PhotoTransferProtocol
import kotlinx.serialization.Serializable

internal const val PROTOCOL_VERSION = PhotoTransferProtocol.VERSION

@Serializable
internal data class TransferManifestRequestParamsDto(
    val protocolVersion: Int,
    val files: List<ManifestFileDto>,
)

@Serializable
internal data class ManifestFileDto(
    val id: String,
    val name: String,
    val mediaType: String,
    val size: Long?,
)

@Serializable
internal data class InfoDto(
    val protocolVersion: Int,
    val receiverId: String,
    val receiverName: String,
)

@Serializable
internal data class TransferCreatedDto(
    val transferId: String,
)

@Serializable
internal data class CompleteDto(
    val receivedFiles: Int,
)
