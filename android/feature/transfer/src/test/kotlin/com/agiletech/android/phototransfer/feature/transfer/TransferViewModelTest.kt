package com.agiletech.android.phototransfer.feature.transfer

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.domain.discovery.ObserveReceivers
import com.agiletech.android.phototransfer.domain.media.ResolveSelectedPhotos
import com.agiletech.android.phototransfer.domain.pairing.ForgetPairing
import com.agiletech.android.phototransfer.domain.pairing.IsReceiverPaired
import com.agiletech.android.phototransfer.domain.pairing.PairReceiver
import com.agiletech.android.phototransfer.domain.pairing.PairingResult
import com.agiletech.android.phototransfer.domain.transfer.TransferCoordinator
import com.agiletech.android.phototransfer.domain.transfer.TransferState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should contain`
import org.amshove.kluent.`should not be null`
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class TransferViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    val receiver = ReceiverDevice(
        name = "Hamid's MacBook",
        host = "192.168.1.20",
        port = 8080,
        receiverId = "receiver-1",
    )

    val transferCoordinator = mock<TransferCoordinator> {
        on { state } doReturn MutableStateFlow<TransferState>(TransferState.Idle)
    }

    val pairReceiver = mock<PairReceiver> {
        on { invoke(any(), any(), any()) } doReturn PairingResult.Paired("Hamid's MacBook")
    }

    val isReceiverPaired = mock<IsReceiverPaired>()

    val observeReceivers = mock<ObserveReceivers> {
        on { invoke() } doReturn emptyFlow()
    }

    val tested by lazy {
        TransferViewModel(
            resolveSelectedPhotos = mock<ResolveSelectedPhotos>(),
            transferCoordinator = transferCoordinator,
            pairReceiver = pairReceiver,
            forgetPairing = mock<ForgetPairing>(),
            isReceiverPaired = isReceiverPaired,
            observeReceivers = observeReceivers,
        )
    }

    @Test
    fun `given an unpaired receiver when pairing then the code is sent and the transfer resumes`() = runTest {
        // given
        whenever { isReceiverPaired(receiver) } doReturn false

        // when
        tested.pair(receiver, "123456")

        // then
        verify(pairReceiver).invoke(receiver, "123456", false)
        verify(transferCoordinator).start(eq(receiver), any())
        tested.replacedPairing.value.`should be null`()
    }

    /**
     * The receiver spends the code on the first attempt, so asking after the exchange would
     * leave the user with a confirmation they can no longer act on.
     */
    @Test
    fun `given an already paired receiver when pairing then the user is asked before the code is spent`() = runTest {
        // given
        whenever { isReceiverPaired(receiver) } doReturn true

        // when
        tested.pair(receiver, "123456")

        // then
        tested.replacedPairing.value `should be equal to` "Hamid's MacBook"
        verify(pairReceiver, never()).invoke(any(), any(), any())
    }

    @Test
    fun `given a pending replacement when it is confirmed then the exchange replaces the pairing`() = runTest {
        // given
        whenever { isReceiverPaired(receiver) } doReturn true
        tested.pair(receiver, "123456")

        // when
        tested.confirmPairingReplacement(receiver, "123456")

        // then
        verify(pairReceiver).invoke(receiver, "123456", true)
        tested.replacedPairing.value.`should be null`()
    }

    @Test
    fun `given a pending replacement when it is cancelled then nothing is paired`() = runTest {
        // given
        whenever { isReceiverPaired(receiver) } doReturn true
        tested.pair(receiver, "123456")

        // when
        tested.cancelPairingReplacement()

        // then
        tested.replacedPairing.value.`should be null`()
        verify(pairReceiver, never()).invoke(any(), any(), any())
    }

    @Test
    fun `given a receiver answering for another Mac when pairing then the error says nothing was saved`() = runTest {
        // given
        whenever { isReceiverPaired(receiver) } doReturn false
        whenever { pairReceiver(any(), any(), any()) } doReturn PairingResult.WrongReceiver

        // when
        tested.pair(receiver, "123456")

        // then
        tested.pairingError.value.`should not be null`() `should contain` "different Mac"
        verify(transferCoordinator, never()).start(any(), any())
    }

    @Test
    fun `given a throttled receiver when pairing then the error says the code is still valid`() = runTest {
        // given
        whenever { isReceiverPaired(receiver) } doReturn false
        whenever { pairReceiver(any(), any(), any()) } doReturn PairingResult.Throttled

        // when
        tested.pair(receiver, "123456")

        // then
        tested.pairingError.value.`should not be null`() `should contain` "still valid"
    }

    @Test
    fun `given a pairing error when the transfer is reset then the error is cleared`() = runTest {
        // given
        whenever { isReceiverPaired(receiver) } doReturn false
        whenever { pairReceiver(any(), any(), any()) } doReturn PairingResult.Declined
        tested.pair(receiver, "123456")

        // when
        tested.resetTransfer()

        // then
        tested.pairingError.value.`should be null`()
        verify(transferCoordinator).reset()
    }
}
