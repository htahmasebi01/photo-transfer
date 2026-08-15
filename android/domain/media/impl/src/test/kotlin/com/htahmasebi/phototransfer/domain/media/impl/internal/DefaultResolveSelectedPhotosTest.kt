package com.htahmasebi.phototransfer.domain.media.impl.internal

import android.net.Uri
import com.htahmasebi.phototransfer.core.coroutines.dispatchers.Dispatchers
import com.htahmasebi.phototransfer.core.model.SelectedFile
import com.htahmasebi.phototransfer.data.media.MediaMetadataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultResolveSelectedPhotosTest {

    private val firstUri = mock<Uri>()
    private val secondUri = mock<Uri>()

    private val metadataSource = mock<MediaMetadataSource> {
        on { resolve(firstUri) } doReturn selectedFile(firstUri, "a.jpg")
        on { resolve(secondUri) } doReturn selectedFile(secondUri, "b.jpg")
    }

    private val resolveSelectedPhotos = DefaultResolveSelectedPhotos(
        metadataSource = metadataSource,
        dispatchers = UnconfinedTestDispatcher().let { Dispatchers(main = it, io = it, default = it) },
    )

    @Test
    fun `resolves every picked photo in order`() = runTest {
        val result = resolveSelectedPhotos(listOf(firstUri, secondUri))

        assertEquals(listOf("a.jpg", "b.jpg"), result.map { it.displayName })
    }

    @Test
    fun `picking the same photo twice resolves it once`() = runTest {
        val result = resolveSelectedPhotos(listOf(firstUri, secondUri, firstUri))

        assertEquals(listOf("a.jpg", "b.jpg"), result.map { it.displayName })
        verify(metadataSource, times(1)).resolve(firstUri)
    }

    @Test
    fun `empty selection resolves to nothing`() = runTest {
        assertEquals(emptyList<SelectedFile>(), resolveSelectedPhotos(emptyList()))
    }

    private fun selectedFile(uri: Uri, name: String) = SelectedFile(
        uri = uri,
        displayName = name,
        mediaType = "image/jpeg",
        size = 1L,
    )
}
