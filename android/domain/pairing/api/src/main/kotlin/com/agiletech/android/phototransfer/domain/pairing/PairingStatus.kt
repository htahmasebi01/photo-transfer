package com.agiletech.android.phototransfer.domain.pairing

import com.agiletech.android.phototransfer.core.model.ReceiverDevice

interface IsReceiverPaired {

    suspend operator fun invoke(receiver: ReceiverDevice): Boolean
}

interface ForgetPairing {

    suspend operator fun invoke(receiver: ReceiverDevice)
}
