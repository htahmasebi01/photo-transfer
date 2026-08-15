package com.agiletech.android.phototransfer.feature.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.domain.discovery.ObserveReceivers
import com.agiletech.android.phototransfer.domain.media.ResolveSelectedPhotos
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
    observeReceivers: ObserveReceivers,
) : ViewModel() {

    val transferState: StateFlow<TransferState> = transferCoordinator.state

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

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
        transferCoordinator.reset()
    }

    private companion object {
        const val STOP_DISCOVERY_DELAY_MS = 5_000L
    }
}
