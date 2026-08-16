package com.agiletech.android.phototransfer.data.pairing.impl.internal

import android.content.SharedPreferences
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * Keeps the pairing secret in the Android keystore and everything else in preferences.
 *
 * Importing the secret as a keystore HMAC key means the app can sign without the raw key
 * ever being readable from app storage, so a backup or a rooted-device file grab does not
 * hand over the ability to upload.
 */
internal class AndroidPairingLocalStore @Inject constructor(
    private val preferences: SharedPreferences,
    private val dispatchers: Dispatchers,
) : PairingLocalStore {

    override suspend fun deviceId(): String = withContext(dispatchers.io) {
        preferences.getString(DEVICE_ID_KEY, null) ?: UUID.randomUUID().toString().also { generated ->
            preferences.edit().putString(DEVICE_ID_KEY, generated).apply()
        }
    }

    override suspend fun save(
        receiverId: String,
        receiverName: String,
        deviceToken: String,
        secret: ByteArray,
    ) = withContext(dispatchers.io) {
        keyStore().setEntry(
            keyAlias(receiverId),
            KeyStore.SecretKeyEntry(SecretKeySpec(secret, HMAC_ALGORITHM)),
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN).build(),
        )
        preferences.edit()
            .putString(deviceTokenKey(receiverId), deviceToken)
            .putString(receiverNameKey(receiverId), receiverName)
            .apply()
    }

    override suspend fun deviceToken(receiverId: String): String? = withContext(dispatchers.io) {
        preferences.getString(deviceTokenKey(receiverId), null)
    }

    override suspend fun receiverName(receiverId: String): String? = withContext(dispatchers.io) {
        preferences.getString(receiverNameKey(receiverId), null)
    }

    override suspend fun mac(receiverId: String): Mac? = withContext(dispatchers.io) {
        // An absent alias is the ordinary "not paired" case, not a failure.
        val key = try {
            keyStore().getKey(keyAlias(receiverId), null) as? SecretKey
        } catch (unavailable: GeneralSecurityException) {
            null
        } ?: return@withContext null
        Mac.getInstance(HMAC_ALGORITHM).apply { init(key) }
    }

    override suspend fun forget(receiverId: String) = withContext(dispatchers.io) {
        try {
            keyStore().deleteEntry(keyAlias(receiverId))
        } catch (alreadyGone: GeneralSecurityException) {
            // Forgetting a receiver that was never paired is not an error.
        }
        preferences.edit()
            .remove(deviceTokenKey(receiverId))
            .remove(receiverNameKey(receiverId))
            .apply()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun keyAlias(receiverId: String) = "$KEY_ALIAS_PREFIX$receiverId"

    private fun deviceTokenKey(receiverId: String) = "deviceToken.$receiverId"

    private fun receiverNameKey(receiverId: String) = "receiverName.$receiverId"

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val KEY_ALIAS_PREFIX = "phototransfer.hmac."
        const val DEVICE_ID_KEY = "deviceId"
    }
}
