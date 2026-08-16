package com.agiletech.android.phototransfer.data.media.impl.internal

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.IOException
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.shouldThrow
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock

class ContentResolverMediaSourceTest {

    val uri = mock<Uri>()

    @Test
    fun `given a provider that reports everything when resolved then name, size and type are mapped`() {
        // given
        val cursor = cursorReturning(name = "IMG_20260802.jpg", size = 4_837_912L)
        val tested = ContentResolverMediaSource(contentResolverReturning(cursor, "image/jpeg"))

        // when
        val result = tested.resolve(uri)

        // then
        result.displayName `should be equal to` "IMG_20260802.jpg"
        result.size `should be equal to` 4_837_912L
        result.mediaType `should be equal to` "image/jpeg"
        result.uri `should be equal to` uri
    }

    @Test
    fun `given a provider that reports nothing when resolved then defaults are used`() {
        // given
        val tested = ContentResolverMediaSource(
            contentResolverReturning(cursor = null, mediaType = null),
        )

        // when
        val result = tested.resolve(uri)

        // then
        result.displayName `should be equal to` "photo"
        result.size.`should be null`()
        result.mediaType `should be equal to` "application/octet-stream"
    }

    @Test
    fun `given no size column when resolved then the size is unknown`() {
        // given
        val cursor = mock<Cursor> {
            on { moveToFirst() } doReturn true
            on { getColumnIndex(OpenableColumns.DISPLAY_NAME) } doReturn 0
            on { getColumnIndex(OpenableColumns.SIZE) } doReturn -1
            on { getString(0) } doReturn "a.png"
        }
        val tested = ContentResolverMediaSource(contentResolverReturning(cursor, "image/png"))

        // when
        val result = tested.resolve(uri)

        // then
        result.displayName `should be equal to` "a.png"
        result.size.`should be null`()
    }

    @Test
    fun `given an openable uri when a stream is requested then the provider stream is returned`() {
        // given
        val bytes = byteArrayOf(1, 2, 3)
        val contentResolver = mock<ContentResolver> {
            on { openInputStream(uri) } doReturn ByteArrayInputStream(bytes)
        }
        val tested = ContentResolverMediaSource(contentResolver)

        // when
        val result = tested.openStream(uri).use { it.readBytes() }

        // then
        result.toList() `should be equal to` bytes.toList()
    }

    @Test
    fun `given a uri the provider cannot open when a stream is requested then it fails`() {
        // given
        val contentResolver = mock<ContentResolver> {
            on { openInputStream(uri) } doReturn null
        }
        val tested = ContentResolverMediaSource(contentResolver)

        // when, then
        invoking { tested.openStream(uri) } shouldThrow IOException::class
    }

    private fun cursorReturning(name: String, size: Long): Cursor = mock {
        on { moveToFirst() } doReturn true
        on { getColumnIndex(OpenableColumns.DISPLAY_NAME) } doReturn 0
        on { getColumnIndex(OpenableColumns.SIZE) } doReturn 1
        on { getString(0) } doReturn name
        on { isNull(1) } doReturn false
        on { getLong(1) } doReturn size
    }

    private fun contentResolverReturning(cursor: Cursor?, mediaType: String?): ContentResolver = mock {
        on { query(eq(uri), any(), isNull(), isNull(), isNull()) } doReturn cursor
        on { getType(uri) } doReturn mediaType
    }
}
