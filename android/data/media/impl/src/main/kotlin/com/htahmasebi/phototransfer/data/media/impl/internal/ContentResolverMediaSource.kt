package com.htahmasebi.phototransfer.data.media.impl.internal

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.htahmasebi.phototransfer.core.model.SelectedFile
import com.htahmasebi.phototransfer.data.media.MediaByteSource
import com.htahmasebi.phototransfer.data.media.MediaMetadataSource
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

internal class ContentResolverMediaSource @Inject constructor(
    private val contentResolver: ContentResolver,
) : MediaMetadataSource, MediaByteSource {

    override fun resolve(uri: Uri): SelectedFile {
        var name = DEFAULT_NAME
        var size: Long? = null

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)?.let { name = it }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }

        return SelectedFile(
            uri = uri,
            displayName = name,
            mediaType = contentResolver.getType(uri) ?: DEFAULT_MEDIA_TYPE,
            size = size,
        )
    }

    override fun openStream(uri: Uri): InputStream =
        contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    private companion object {
        const val DEFAULT_NAME = "photo"
        const val DEFAULT_MEDIA_TYPE = "application/octet-stream"
    }
}
