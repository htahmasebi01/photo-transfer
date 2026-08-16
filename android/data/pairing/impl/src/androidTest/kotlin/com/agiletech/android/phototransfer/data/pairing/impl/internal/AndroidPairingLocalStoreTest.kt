package com.agiletech.android.phototransfer.data.pairing.impl.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers.Unconfined
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be equal to`
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class AndroidPairingLocalStoreTest {

    val context = ApplicationProvider.getApplicationContext<Context>()

    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val tested = AndroidPairingLocalStore(
        preferences = preferences,
        dispatchers = Dispatchers(main = Unconfined, io = Unconfined, default = Unconfined),
    )

    @Before
    @After
    fun clearState() {
        preferences.edit().clear().commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.aliases().toList()
            .filter { it.startsWith("phototransfer.hmac.") }
            .forEach(keyStore::deleteEntry)
    }

    @Test
    fun `given a generated device id when it is read again then it is unchanged`() = runTest {
        // given
        val first = tested.deviceId()

        // when
        val second = tested.deviceId()

        // then
        second `should be equal to` first
    }

    @Test
    fun `given a saved secret when it signs then it matches a plain hmac over the same bytes`() = runTest {
        // given
        val secret = ByteArray(32) { it.toByte() }
        tested.save(RECEIVER_ID, "Test Mac", "token-1", secret)

        // when
        val fromKeystore = tested.mac(RECEIVER_ID)?.doFinal(PAYLOAD)

        // then
        val expected = Mac.getInstance("HmacSHA256")
            .apply { init(SecretKeySpec(secret, "HmacSHA256")) }
            .doFinal(PAYLOAD)
        fromKeystore?.toList() `should be equal to` expected.toList()
    }

    @Test
    fun `given saved metadata when it is read back then the token and name are returned`() = runTest {
        // given, when
        tested.save(RECEIVER_ID, "Test Mac", "token-1", ByteArray(32))

        // then
        tested.deviceToken(RECEIVER_ID) `should be equal to` "token-1"
        tested.receiverName(RECEIVER_ID) `should be equal to` "Test Mac"
    }

    @Test
    fun `given two paired receivers when each signs then the secrets are scoped per receiver`() = runTest {
        // given
        tested.save(RECEIVER_ID, "Mac A", "token-a", ByteArray(32) { 1 })
        tested.save(OTHER_RECEIVER_ID, "Mac B", "token-b", ByteArray(32) { 2 })

        // when
        val first = tested.mac(RECEIVER_ID)?.doFinal(PAYLOAD)?.toList()
        val second = tested.mac(OTHER_RECEIVER_ID)?.doFinal(PAYLOAD)?.toList()

        // then
        first `should not be equal to` second
    }

    @Test
    fun `given a saved pairing when it is forgotten then the key and metadata are gone`() = runTest {
        // given
        tested.save(RECEIVER_ID, "Test Mac", "token-1", ByteArray(32))

        // when
        tested.forget(RECEIVER_ID)

        // then
        tested.mac(RECEIVER_ID).`should be null`()
        tested.deviceToken(RECEIVER_ID).`should be null`()
        tested.receiverName(RECEIVER_ID).`should be null`()
    }

    @Test
    fun `given an unknown receiver when a mac is requested then there is none`() = runTest {
        // given, when
        val mac = tested.mac(RECEIVER_ID)

        // then
        mac.`should be null`()
    }

    companion object {
        const val PREFERENCES_NAME = "pairing-local-store-test"
        const val RECEIVER_ID = "receiver-1"
        const val OTHER_RECEIVER_ID = "receiver-2"
        val PAYLOAD = "GET\n/v1/transfers\n1700000000".toByteArray()
    }
}
