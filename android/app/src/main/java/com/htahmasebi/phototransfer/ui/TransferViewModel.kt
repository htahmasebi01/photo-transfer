package com.htahmasebi.phototransfer.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.htahmasebi.phototransfer.AppContainer
import com.htahmasebi.phototransfer.discovery.DiscoveryEvent
import com.htahmasebi.phototransfer.discovery.ReceiverDiscovery
import com.htahmasebi.phototransfer.model.ReceiverDevice
import com.htahmasebi.phototransfer.model.SelectedFile
import com.htahmasebi.phototransfer.model.TransferState
import com.htahmasebi.phototransfer.picker.SelectedFileResolver
import com.htahmasebi.phototransfer.transfer.TransferCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferViewModel(
    private val fileResolver: SelectedFileResolver,
    discovery: ReceiverDiscovery,
    private val coordinator: TransferCoordinator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    val transferState: StateFlow<TransferState> = coordinator.state

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _devices = MutableStateFlow<List<ReceiverDevice>>(emptyList())
    val devices: StateFlow<List<ReceiverDevice>> = _devices.asStateFlow()

    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

    private val discoveredByName = LinkedHashMap<String, ReceiverDevice>()
    private var lastReceiver: ReceiverDevice? = null

    init {
        viewModelScope.launch {
            discovery.discover()
                .catch { error -> _discoveryError.value = error.message }
                .collect { event ->
                    when (event) {
                        is DiscoveryEvent.Found -> discoveredByName[event.device.name] = event.device
                        is DiscoveryEvent.Lost -> discoveredByName.remove(event.serviceName)
                    }
                    _devices.value = discoveredByName.values.toList()
                }
        }
    }

    fun onPhotosSelected(uris: List<Uri>) {
        viewModelScope.launch {
            _selectedFiles.value = withContext(ioDispatcher) {
                uris.map(fileResolver::resolve)
            }
        }
    }

    fun send(receiver: ReceiverDevice) {
        lastReceiver = receiver
        coordinator.start(receiver, _selectedFiles.value)
    }

    fun sendToManualAddress(host: String, port: Int) {
        send(ReceiverDevice(name = "$host:$port", host = host, port = port))
    }

    fun retry() {
        lastReceiver?.let(::send)
    }

    fun resetTransfer() {
        coordinator.reset()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TransferViewModel(
                        fileResolver = container.selectedFileResolver,
                        discovery = container.receiverDiscovery,
                        coordinator = container.transferCoordinator,
                    ) as T
            }
    }
}
