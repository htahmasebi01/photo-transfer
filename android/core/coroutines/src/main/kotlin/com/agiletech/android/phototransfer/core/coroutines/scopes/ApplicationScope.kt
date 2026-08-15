package com.agiletech.android.phototransfer.core.coroutines.scopes

import javax.inject.Qualifier

/** A [kotlinx.coroutines.CoroutineScope] that lives as long as the process. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope
