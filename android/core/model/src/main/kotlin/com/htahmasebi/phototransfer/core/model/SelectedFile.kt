package com.htahmasebi.phototransfer.core.model

import android.net.Uri

/**
 * A photo the user picked. The [uri] stays the only handle to the bytes: a
 * selected photo may come from a cloud-backed provider with no filesystem path,
 * and [size] is null when the provider does not report one.
 */
data class SelectedFile(
    val uri: Uri,
    val displayName: String,
    val mediaType: String,
    val size: Long?,
)
