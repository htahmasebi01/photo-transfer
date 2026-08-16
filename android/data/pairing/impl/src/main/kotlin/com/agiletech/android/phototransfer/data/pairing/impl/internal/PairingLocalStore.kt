package com.agiletech.android.phototransfer.data.pairing.impl.internal

import javax.crypto.Mac

/** Local record of which receivers this device is paired with. */
internal interface PairingLocalStore {

    suspend fun deviceId(): String

    suspend fun save(receiverId: String, receiverName: String, deviceToken: String, secret: ByteArray)

    suspend fun deviceToken(receiverId: String): String?

    suspend fun receiverName(receiverId: String): String?

    /** An initialised MAC for [receiverId], or null when no pairing exists. */
    suspend fun mac(receiverId: String): Mac?

    suspend fun forget(receiverId: String)
}
