package com.agiletech.android.phototransfer.feature.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.domain.discovery.ObserveReceivers
import com.agiletech.android.phototransfer.domain.media.ResolveSelectedPhotos
import com.agiletech.android.phototransfer.domain.pairing.ForgetPairing
import com.agiletech.android.phototransfer.domain.pairing.IsReceiverPaired
import com.agiletech.android.phototransfer.domain.pairing.PairReceiver
import com.agiletech.android.phototransfer.domain.pairing.PairingResult
import com.agiletech.android.phototransfer.domain.transfer.TransferCoordinator
import com.agiletech.android.phototransfer.domain.transfer.TransferState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val resolveSelectedPhotos: ResolveSelectedPhotos,
    private val transferCoordinator: TransferCoordinator,
    private val pairReceiver: PairReceiver,
    private val forgetPairing: ForgetPairing,
    private val isReceiverPaired: IsReceiverPaired,
    observeReceivers: ObserveReceivers,
) : ViewModel() {

    val transferState: StateFlow<TransferState> = transferCoordinator.state

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

    private val _pairingError = MutableStateFlow<String?>(null)
    val pairingError: StateFlow<String?> = _pairingError.asStateFlow()

    private val _isPairing = MutableStateFlow(false)
    val isPairing: StateFlow<Boolean> = _isPairing.asStateFlow()

    /** The name of the receiver whose stored pairing would be replaced, once confirmed. */
    private val _replacedPairing = MutableStateFlow<String?>(null)
    val replacedPairing: StateFlow<String?> = _replacedPairing.asStateFlow()

    val devices: StateFlow<List<ReceiverDevice>> = observeReceivers()
        .catch { error -> _discoveryError.value = error.message }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_DISCOVERY_DELAY_MS),
            initialValue = emptyList(),
        )

    private var lastReceiver: ReceiverDevice? = null

    fun onPhotosSelected(uris: List<Uri>) {
        viewModelScope.launch {
            _selectedFiles.value = resolveSelectedPhotos(uris)
        }
    }

    fun send(receiver: ReceiverDevice) {
        lastReceiver = receiver
        transferCoordinator.start(receiver, _selectedFiles.value)
    }

    fun sendToManualAddress(host: String, port: Int) {
        send(ReceiverDevice(name = "$host:$port", host = host, port = port))
    }

    fun retry() {
        lastReceiver?.let(::send)
    }

    fun resetTransfer() {
        _pairingError.value = null
        _replacedPairing.value = null
        transferCoordinator.reset()
    }

    /**
     * Pairs with [receiver], then resumes the transfer that asked for pairing.
     *
     * Replacing an existing pairing is asked about before the code is spent, because the
     * receiver treats the code as single use and would reject the confirmed retry.
     */
    fun pair(receiver: ReceiverDevice, code: String) {
        viewModelScope.launch {
            if (isReceiverPaired(receiver)) {
                _replacedPairing.value = receiver.name
            } else {
                exchange(receiver, code, replaceExisting = false)
            }
        }
    }

    fun confirmPairingReplacement(receiver: ReceiverDevice, code: String) {
        viewModelScope.launch {
            _replacedPairing.value = null
            exchange(receiver, code, replaceExisting = true)
        }
    }

    fun cancelPairingReplacement() {
        _replacedPairing.value = null
    }

    fun unpair(receiver: ReceiverDevice) {
        viewModelScope.launch {
            forgetPairing(receiver)
            resetTransfer()
        }
    }

    private suspend fun exchange(receiver: ReceiverDevice, code: String, replaceExisting: Boolean) {
        _isPairing.value = true
        _pairingError.value = null

        when (val result = pairReceiver(receiver, code, replaceExisting)) {
            is PairingResult.Paired -> send(receiver)
            PairingResult.WrongCode -> _pairingError.value =
                "That code was not accepted. Check the code on your Mac and try again."
            PairingResult.Declined -> _pairingError.value = "Your Mac declined this device."
            PairingResult.TimedOut -> _pairingError.value =
                "Nobody approved this device on the Mac in time."
            PairingResult.Throttled -> _pairingError.value =
                "Your Mac is refusing pairing attempts for a moment. The code on screen is " +
                    "still valid. Wait about a minute and try again."
            is PairingResult.ReplacesExistingPairing -> _replacedPairing.value = result.receiverName
            PairingResult.WrongReceiver -> _pairingError.value =
                "That device answered for a different Mac than the one you chose, so nothing " +
                    "was saved. Pair again, and check the name shown on your Mac."
            is PairingResult.Failed -> _pairingError.value = result.reason
        }
        _isPairing.value = false
    }

    private companion object {
        const val STOP_DISCOVERY_DELAY_MS = 5_000L
    }
}
