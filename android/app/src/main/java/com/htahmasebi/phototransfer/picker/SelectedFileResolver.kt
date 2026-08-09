package com.htahmasebi.phototransfer.picker

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.htahmasebi.phototransfer.model.SelectedFile

class SelectedFileResolver(
    private val contentResolver: ContentResolver,
) {

    fun resolve(uri: Uri): SelectedFile {
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

    private companion object {
        const val DEFAULT_NAME = "photo"
        const val DEFAULT_MEDIA_TYPE = "application/octet-stream"
    }
}
