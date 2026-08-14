package com.htahmasebi.phototransfer.data.transfer.impl.internal

import com.htahmasebi.phototransfer.core.model.SelectedFile
import com.htahmasebi.phototransfer.data.media.MediaByteSource
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** Streams a picked photo straight from its provider, never buffering it in memory. */
internal class MediaRequestBody(
    private val byteSource: MediaByteSource,
    private val file: SelectedFile,
) : RequestBody() {

    override fun contentType(): MediaType? = file.mediaType.toMediaTypeOrNull()

    override fun contentLength(): Long = file.size ?: -1L

    override fun writeTo(sink: BufferedSink) {
        byteSource.openStream(file.uri).use { input ->
            sink.writeAll(input.source())
        }
    }
}
