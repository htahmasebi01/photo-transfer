package com.agiletech.android.phototransfer.domain.media

import android.net.Uri
import com.agiletech.android.phototransfer.core.model.SelectedFile

/** Turns picker results into the files a transfer will send. */
interface ResolveSelectedPhotos {

    suspend operator fun invoke(uris: List<Uri>): List<SelectedFile>
}
