package com.agiletech.android.phototransfer.data.media

import android.net.Uri
import com.agiletech.android.phototransfer.core.model.SelectedFile

/** Reads the metadata a picked photo exposes, without assuming a filesystem path. */
interface MediaMetadataSource {

    fun resolve(uri: Uri): SelectedFile
}
