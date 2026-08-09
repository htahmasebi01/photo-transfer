package com.htahmasebi.phototransfer.picker

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock

class SelectedFileResolverTest {

    private val uri = mock<Uri>()

    @Test
    fun `resolve maps display name size and media type`() {
        val cursor = cursorReturning(name = "IMG_20260802.jpg", size = 4_837_912L)
        val contentResolver = contentResolverReturning(cursor, mediaType = "image/jpeg")
        val resolver = SelectedFileResolver(contentResolver)

        val result = resolver.resolve(uri)

        assertEquals("IMG_20260802.jpg", result.displayName)
        assertEquals(4_837_912L, result.size)
        assertEquals("image/jpeg", result.mediaType)
        assertEquals(uri, result.uri)
    }

    @Test
    fun `resolve falls back when provider reports nothing`() {
        val contentResolver = contentResolverReturning(cursor = null, mediaType = null)
        val resolver = SelectedFileResolver(contentResolver)

        val result = resolver.resolve(uri)

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
        val contentResolver = contentResolverReturning(cursor, mediaType = "image/png")
        val resolver = SelectedFileResolver(contentResolver)

        val result = resolver.resolve(uri)

        assertEquals("a.png", result.displayName)
        assertNull(result.size)
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
