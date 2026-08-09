package com.htahmasebi.phototransfer.transfer

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** Streams a content:// URI directly into the request without buffering it in memory. */
class ContentUriRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val contentType: MediaType?,
    private val declaredLength: Long?,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = declaredLength ?: -1L

    override fun writeTo(sink: BufferedSink) {
        contentResolver.openInputStream(uri)?.use { input ->
            sink.writeAll(input.source())
        } ?: throw IOException("Cannot open $uri")
    }
}
