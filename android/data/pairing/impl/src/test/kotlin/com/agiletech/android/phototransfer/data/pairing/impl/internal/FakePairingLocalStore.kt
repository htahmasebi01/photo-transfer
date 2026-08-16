package com.agiletech.android.phototransfer.data.pairing.impl.internal

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Stands in for the keystore-backed store, which needs a device to run on. */
internal class FakePairingLocalStore(
    private val deviceId: String = "device-1",
) : PairingLocalStore {

    private val secrets = mutableMapOf<String, ByteArray>()
    private val deviceTokens = mutableMapOf<String, String>()
    private val receiverNames = mutableMapOf<String, String>()

    override suspend fun deviceId(): String = deviceId

    override suspend fun save(
        receiverId: String,
        receiverName: String,
        deviceToken: String,
        secret: ByteArray,
    ) {
        secrets[receiverId] = secret
        deviceTokens[receiverId] = deviceToken
        receiverNames[receiverId] = receiverName
    }

    override suspend fun deviceToken(receiverId: String): String? = deviceTokens[receiverId]

    override suspend fun receiverName(receiverId: String): String? = receiverNames[receiverId]

    override suspend fun mac(receiverId: String): Mac? {
        val secret = secrets[receiverId] ?: return null
        return Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }
    }

    override suspend fun forget(receiverId: String) {
        secrets -= receiverId
        deviceTokens -= receiverId
        receiverNames -= receiverId
    }
}
