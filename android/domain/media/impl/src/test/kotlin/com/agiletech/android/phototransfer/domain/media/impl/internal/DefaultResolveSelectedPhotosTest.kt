package com.agiletech.android.phototransfer.domain.media.impl.internal

import android.net.Uri
import com.agiletech.android.phototransfer.core.coroutines.dispatchers.Dispatchers
import com.agiletech.android.phototransfer.core.model.SelectedFile
import com.agiletech.android.phototransfer.data.media.MediaMetadataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultResolveSelectedPhotosTest {

    val firstUri = mock<Uri>()

    val secondUri = mock<Uri>()

    val metadataSource = mock<MediaMetadataSource> {
        on { resolve(firstUri) } doReturn selectedFile(firstUri, "a.jpg")
        on { resolve(secondUri) } doReturn selectedFile(secondUri, "b.jpg")
    }

    val tested = DefaultResolveSelectedPhotos(
        metadataSource = metadataSource,
        dispatchers = UnconfinedTestDispatcher().let {
            Dispatchers(main = it, io = it, default = it)
        },
    )

    @Test
    fun `given two picked photos when resolved then both are returned in order`() = runTest {
        // given, when
        val result = tested(listOf(firstUri, secondUri))

        // then
        result.map { it.displayName } `should be equal to` listOf("a.jpg", "b.jpg")
    }

    @Test
    fun `given the same photo picked twice when resolved then it is resolved once`() = runTest {
        // given, when
        val result = tested(listOf(firstUri, secondUri, firstUri))

        // then
        result.map { it.displayName } `should be equal to` listOf("a.jpg", "b.jpg")
        verify(metadataSource, times(1)).resolve(firstUri)
    }

    @Test
    fun `given an empty selection when resolved then nothing is returned`() = runTest {
        // given, when
        val result = tested(emptyList())

        // then
        result.`should be empty`()
    }

    private fun selectedFile(uri: Uri, name: String) = SelectedFile(
        uri = uri,
        displayName = name,
        mediaType = "image/jpeg",
        size = 1L,
    )
}
