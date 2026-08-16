package com.agiletech.android.phototransfer.data.pairing

/** Read and forget side of the local pairing record; writing happens during pairing. */
interface PairedReceiverStore {

    suspend fun isPaired(receiverId: String): Boolean

    suspend fun pairedReceiverName(receiverId: String): String?

    suspend fun forget(receiverId: String)
}
