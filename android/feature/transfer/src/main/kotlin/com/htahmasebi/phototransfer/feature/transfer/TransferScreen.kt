package com.htahmasebi.phototransfer.feature.transfer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.htahmasebi.phototransfer.core.model.ReceiverDevice
import com.htahmasebi.phototransfer.core.model.SelectedFile
import com.htahmasebi.phototransfer.domain.transfer.TransferState

@Composable
fun TransferScreen(viewModel: TransferViewModel) {
    val transferState by viewModel.transferState.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val discoveryError by viewModel.discoveryError.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Photo Transfer", style = MaterialTheme.typography.headlineSmall)

        when (val state = transferState) {
            is TransferState.Idle -> IdleContent(
                selectedFiles = selectedFiles,
                devices = devices,
                discoveryError = discoveryError,
                onPhotosSelected = viewModel::onPhotosSelected,
                onSend = viewModel::send,
                onSendManual = viewModel::sendToManualAddress,
            )
            is TransferState.Transferring -> TransferringContent(state)
            is TransferState.Completed -> CompletedContent(
                state = state,
                onDone = viewModel::resetTransfer,
            )
            is TransferState.Failed -> FailedContent(
                state = state,
                onRetry = viewModel::retry,
                onDismiss = viewModel::resetTransfer,
            )
        }
    }
}

@Composable
private fun IdleContent(
    selectedFiles: List<SelectedFile>,
    devices: List<ReceiverDevice>,
    discoveryError: String?,
    onPhotosSelected: (List<Uri>) -> Unit,
    onSend: (ReceiverDevice) -> Unit,
    onSendManual: (host: String, port: Int) -> Unit,
) {
    PhotoSelectionSection(selectedFiles, onPhotosSelected)
    HorizontalDivider()
    DeviceSection(
        devices = devices,
        discoveryError = discoveryError,
        sendEnabled = selectedFiles.isNotEmpty(),
        onSend = onSend,
        onSendManual = onSendManual,
    )
}

@Composable
private fun PhotoSelectionSection(
    selectedFiles: List<SelectedFile>,
    onPhotosSelected: (List<Uri>) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPhotosSelected(uris)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        ) {
            Text("Select photos")
        }

        if (selectedFiles.isNotEmpty()) {
            Text(
                "${selectedFiles.size} photo(s) selected",
                style = MaterialTheme.typography.bodyMedium,
            )
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                items(selectedFiles, key = { it.uri }) { file ->
                    Text(
                        file.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceSection(
    devices: List<ReceiverDevice>,
    discoveryError: String?,
    sendEnabled: Boolean,
    onSend: (ReceiverDevice) -> Unit,
    onSendManual: (host: String, port: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Send to", style = MaterialTheme.typography.titleMedium)

        if (discoveryError != null) {
            Text(
                "Discovery unavailable: $discoveryError",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (devices.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.width(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Looking for your Mac on this Wi-Fi network…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        devices.forEach { device ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${device.host}:${device.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { onSend(device) }, enabled = sendEnabled) {
                        Text("Send")
                    }
                }
            }
        }

        ManualAddressEntry(sendEnabled = sendEnabled, onSendManual = onSendManual)
    }
}

@Composable
private fun ManualAddressEntry(
    sendEnabled: Boolean,
    onSendManual: (host: String, port: Int) -> Unit,
) {
    var host by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf("") }
    val port = portText.toIntOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Or enter the Mac's address manually",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP address") },
                modifier = Modifier.weight(2f),
                singleLine = true,
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { portText = it },
                label = { Text("Port") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        OutlinedButton(
            onClick = { port?.let { onSendManual(host.trim(), it) } },
            enabled = sendEnabled && host.isNotBlank() && port != null,
        ) {
            Text("Send to address")
        }
    }
}

@Composable
private fun TransferringContent(state: TransferState.Transferring) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sending to ${state.receiver.name}", style = MaterialTheme.typography.titleMedium)

        val progress = if (state.totalBytes > 0) {
            state.completedBytes.toFloat() / state.totalBytes.toFloat()
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "File ${state.completedFiles + 1} of ${state.totalFiles}: ${state.currentFileName}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${state.completedBytes / BYTES_PER_KB} KB of ${state.totalBytes / BYTES_PER_KB} KB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompletedContent(state: TransferState.Completed, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Transfer complete",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("${state.transferredFiles} photo(s) sent to your Mac.")
        Button(onClick = onDone) {
            Text("Done")
        }
    }
}

@Composable
private fun FailedContent(
    state: TransferState.Failed,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Transfer failed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(state.reason, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.retryable) {
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
            OutlinedButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    }
}

private const val BYTES_PER_KB = 1024
