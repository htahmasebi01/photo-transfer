package com.agiletech.android.phototransfer.data.media.impl.internal

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock

class ContentResolverMediaSourceTest {

    private val uri = mock<Uri>()

    @Test
    fun `resolve maps display name size and media type`() {
        val cursor = cursorReturning(name = "IMG_20260802.jpg", size = 4_837_912L)
        val source = ContentResolverMediaSource(contentResolverReturning(cursor, "image/jpeg"))

        val result = source.resolve(uri)

        assertEquals("IMG_20260802.jpg", result.displayName)
        assertEquals(4_837_912L, result.size)
        assertEquals("image/jpeg", result.mediaType)
        assertEquals(uri, result.uri)
    }

    @Test
    fun `resolve falls back when provider reports nothing`() {
        val source = ContentResolverMediaSource(contentResolverReturning(cursor = null, mediaType = null))

        val result = source.resolve(uri)

        assertEquals("photo", result.displayName)
        assertNull(result.size)
        assertEquals("application/octet-stream", result.mediaType)
    }

    @Test
    fun `resolve handles missing size column`() {
        val cursor = mock<Cursor> {
            on { moveToFirst() } doReturn true
            on { getColumnIndex(OpenableColumns.DISPLAY_NAME) } doReturn 0
            on { getColumnIndex(OpenableColumns.SIZE) } doReturn -1
            on { getString(0) } doReturn "a.png"
        }
        val source = ContentResolverMediaSource(contentResolverReturning(cursor, "image/png"))

        val result = source.resolve(uri)

        assertEquals("a.png", result.displayName)
        assertNull(result.size)
    }

    @Test
    fun `openStream returns the provider stream`() {
        val bytes = byteArrayOf(1, 2, 3)
        val contentResolver = mock<ContentResolver> {
            on { openInputStream(uri) } doReturn ByteArrayInputStream(bytes)
        }
        val source = ContentResolverMediaSource(contentResolver)

        val result = source.openStream(uri).use { it.readBytes() }

        assertEquals(bytes.toList(), result.toList())
    }

    @Test
    fun `openStream fails when the provider cannot open the uri`() {
        val contentResolver = mock<ContentResolver> {
            on { openInputStream(uri) } doReturn null
        }
        val source = ContentResolverMediaSource(contentResolver)

        assertThrows(IOException::class.java) { source.openStream(uri) }
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
