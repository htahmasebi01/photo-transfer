package com.htahmasebi.phototransfer.model

import android.net.Uri

data class SelectedFile(
    val uri: Uri,
    val displayName: String,
    val mediaType: String,
    val size: Long?,
)
