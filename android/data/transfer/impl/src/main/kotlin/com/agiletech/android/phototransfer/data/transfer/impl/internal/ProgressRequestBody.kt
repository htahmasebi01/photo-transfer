package com.agiletech.android.phototransfer.data.transfer.impl.internal

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** Reports cumulative bytes written while delegating to another body. */
internal class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onBytesSent: (Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = object : ForwardingSink(sink) {
            private var totalBytes = 0L

            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                totalBytes += byteCount
                onBytesSent(totalBytes)
            }
        }
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }
}
