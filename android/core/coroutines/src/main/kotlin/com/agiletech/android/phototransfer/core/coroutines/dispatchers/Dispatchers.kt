package com.agiletech.android.phototransfer.core.coroutines.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Injected instead of referencing [kotlinx.coroutines.Dispatchers] directly so
 * tests can substitute deterministic dispatchers.
 */
data class Dispatchers(
    val main: CoroutineDispatcher,
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
)
