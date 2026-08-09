package com.htahmasebi.phototransfer.transfer

import com.htahmasebi.phototransfer.model.ReceiverDevice
import com.htahmasebi.phototransfer.model.SelectedFile
import com.htahmasebi.phototransfer.protocol.InfoResponse
import com.htahmasebi.phototransfer.protocol.ManifestFile
import com.htahmasebi.phototransfer.protocol.TransferManifest

interface TransferClient {

    suspend fun fetchInfo(receiver: ReceiverDevice): InfoResponse

    suspend fun createTransfer(receiver: ReceiverDevice, manifest: TransferManifest): String

    suspend fun uploadFile(
        receiver: ReceiverDevice,
        transferId: String,
        manifestFile: ManifestFile,
        source: SelectedFile,
        onBytesSent: (Long) -> Unit,
    )

    suspend fun completeTransfer(receiver: ReceiverDevice, transferId: String): Int
}
