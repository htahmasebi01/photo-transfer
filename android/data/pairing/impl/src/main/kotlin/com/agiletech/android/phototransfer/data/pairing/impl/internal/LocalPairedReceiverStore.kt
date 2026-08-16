package com.agiletech.android.phototransfer.data.pairing.impl.internal

import com.agiletech.android.phototransfer.data.pairing.PairedReceiverStore
import javax.inject.Inject

internal class LocalPairedReceiverStore @Inject constructor(
    private val store: PairingLocalStore,
) : PairedReceiverStore {

    override suspend fun isPaired(receiverId: String): Boolean =
        store.deviceToken(receiverId) != null && store.mac(receiverId) != null

    override suspend fun pairedReceiverName(receiverId: String): String? =
        store.receiverName(receiverId)

    override suspend fun forget(receiverId: String) {
        store.forget(receiverId)
    }
}
