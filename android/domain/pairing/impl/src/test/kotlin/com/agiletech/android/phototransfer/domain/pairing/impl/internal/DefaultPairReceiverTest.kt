package com.agiletech.android.phototransfer.domain.pairing.impl.internal

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.data.pairing.PairedReceiverStore
import com.agiletech.android.phototransfer.data.pairing.PairingGateway
import com.agiletech.android.phototransfer.data.pairing.PairingOutcome
import com.agiletech.android.phototransfer.domain.pairing.PairingResult
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.junit.Test

class DefaultPairReceiverTest {

    val receiver = ReceiverDevice(
        name = "Test Mac",
        host = "127.0.0.1",
        port = 8080,
        receiverId = "receiver-1",
    )

    @Test
    fun `given every gateway outcome when pairing then each maps to a domain result`() = runTest {
        // given
        val expected = mapOf(
            PairingOutcome.Paired("receiver-1", "Test Mac") to PairingResult.Paired("Test Mac"),
            PairingOutcome.InvalidCode to PairingResult.WrongCode,
            PairingOutcome.Declined to PairingResult.Declined,
            PairingOutcome.TimedOut to PairingResult.TimedOut,
            PairingOutcome.Throttled to PairingResult.Throttled,
            PairingOutcome.AlreadyPaired("receiver-1", "Test Mac") to
                PairingResult.ReplacesExistingPairing("Test Mac"),
            PairingOutcome.IdentityMismatch(expected = "receiver-1", claimed = "other") to
                PairingResult.WrongReceiver,
            PairingOutcome.Failed("no route") to PairingResult.Failed("no route"),
        )

        expected.forEach { (outcome, result) ->
            // when
            val tested = DefaultPairReceiver(FakePairingGateway(outcome))

            // then
            tested(receiver, "123456") `should be equal to` result
        }
    }

    @Test
    fun `given an entered code when pairing then it reaches the gateway`() = runTest {
        // given
        val gateway = FakePairingGateway(PairingOutcome.Declined)

        // when
        DefaultPairReceiver(gateway).invoke(receiver, "123456")

        // then
        gateway.lastCode `should be equal to` "123456"
    }

    /** Replacing a pairing is only ever a user's decision, so the default must not do it. */
    @Test
    fun `given no confirmation when pairing then an existing pairing is not replaced`() = runTest {
        // given
        val gateway = FakePairingGateway(PairingOutcome.Declined)

        // when
        DefaultPairReceiver(gateway).invoke(receiver, "123456")

        // then
        gateway.lastReplaceExisting.`should be false`()
    }

    @Test
    fun `given a confirmed replacement when pairing then it reaches the gateway`() = runTest {
        // given
        val gateway = FakePairingGateway(PairingOutcome.Declined)

        // when
        DefaultPairReceiver(gateway).invoke(receiver, "123456", replaceExisting = true)

        // then
        gateway.lastReplaceExisting.`should be true`()
    }

    @Test
    fun `given a receiver with no id when pairing is checked then it is never considered paired`() = runTest {
        // given
        val store = FakePairedReceiverStore(pairedIds = setOf("receiver-1"))

        // when
        val tested = DefaultIsReceiverPaired(store)

        // then
        tested(receiver).`should be true`()
        tested(receiver.copy(receiverId = null)).`should be false`()
    }

    @Test
    fun `given a stored pairing when it is forgotten then the receiver is cleared`() = runTest {
        // given
        val store = FakePairedReceiverStore(pairedIds = setOf("receiver-1"))

        // when
        DefaultForgetPairing(store).invoke(receiver)

        // then
        store.isPaired("receiver-1").`should be false`()
    }

    @Test
    fun `given an unidentified receiver when it is forgotten then nothing is cleared`() = runTest {
        // given
        val store = FakePairedReceiverStore(pairedIds = setOf("receiver-1"))

        // when
        DefaultForgetPairing(store).invoke(receiver.copy(receiverId = null))

        // then
        store.isPaired("receiver-1").`should be true`()
    }

    class FakePairingGateway(private val outcome: PairingOutcome) : PairingGateway {

        var lastCode: String? = null
        var lastReplaceExisting = false

        override suspend fun pair(
            receiver: ReceiverDevice,
            pairingCode: String,
            replaceExisting: Boolean,
        ): PairingOutcome {
            lastCode = pairingCode
            lastReplaceExisting = replaceExisting
            return outcome
        }
    }

    class FakePairedReceiverStore(pairedIds: Set<String>) : PairedReceiverStore {

        private val paired = pairedIds.toMutableSet()

        override suspend fun isPaired(receiverId: String) = receiverId in paired

        override suspend fun pairedReceiverName(receiverId: String) =
            "Test Mac".takeIf { receiverId in paired }

        override suspend fun forget(receiverId: String) {
            paired -= receiverId
        }
    }
}
