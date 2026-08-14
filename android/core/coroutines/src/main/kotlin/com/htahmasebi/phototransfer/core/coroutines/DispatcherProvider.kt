package com.htahmasebi.phototransfer.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Injected instead of referencing [kotlinx.coroutines.Dispatchers] directly so
 * tests can substitute a deterministic dispatcher.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}
