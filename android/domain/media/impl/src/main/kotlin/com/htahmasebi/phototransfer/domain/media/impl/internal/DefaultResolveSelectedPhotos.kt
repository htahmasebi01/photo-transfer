package com.htahmasebi.phototransfer.domain.media.impl.internal

import android.net.Uri
import com.htahmasebi.phototransfer.core.coroutines.dispatchers.Dispatchers
import com.htahmasebi.phototransfer.core.model.SelectedFile
import com.htahmasebi.phototransfer.data.media.MediaMetadataSource
import com.htahmasebi.phototransfer.domain.media.ResolveSelectedPhotos
import javax.inject.Inject
import kotlinx.coroutines.withContext

internal class DefaultResolveSelectedPhotos @Inject constructor(
    private val metadataSource: MediaMetadataSource,
    private val dispatchers: Dispatchers,
) : ResolveSelectedPhotos {

    /** Resolving queries the provider, so it stays off the main thread. */
    override suspend fun invoke(uris: List<Uri>): List<SelectedFile> =
        withContext(dispatchers.io) {
            // Picking the same photo twice should still send it once.
            uris.distinct().map(metadataSource::resolve)
        }
}
