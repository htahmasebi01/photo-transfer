package com.htahmasebi.phototransfer.data.media

import android.net.Uri
import java.io.IOException
import java.io.InputStream

/**
 * Opens the bytes behind a picked photo. Split from [MediaMetadataSource] so
 * callers that only stream bytes do not depend on metadata resolution.
 */
interface MediaByteSource {

    /** @throws IOException when the provider cannot open [uri]. */
    fun openStream(uri: Uri): InputStream
}
