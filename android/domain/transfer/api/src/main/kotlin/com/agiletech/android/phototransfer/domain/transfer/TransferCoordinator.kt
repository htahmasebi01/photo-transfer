package com.agiletech.android.phototransfer.domain.transfer

import com.agiletech.android.phototransfer.core.model.ReceiverDevice
import com.agiletech.android.phototransfer.core.model.SelectedFile
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the one live transfer session. Implementations outlive the screen, so a
 * transfer survives configuration changes.
 */
interface TransferCoordinator {

    val state: StateFlow<TransferState>

    /** Starts sending [files], replacing any transfer already in flight. */
    fun start(receiver: ReceiverDevice, files: List<SelectedFile>)

    /** Cancels any in-flight transfer and returns to [TransferState.Idle]. */
    fun reset()
}
