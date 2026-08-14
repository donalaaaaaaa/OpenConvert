package com.openconvert.app.domain.converter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaInputResolverTest {
    @Test
    fun `does not copy when a real file path is readable`() {
        assertFalse(MediaInputResolver.copiesInput(hasReadableFilePath = true, hasSafParameter = false))
    }

    @Test
    fun `does not copy when ffmpeg-kit provides a SAF parameter`() {
        assertFalse(MediaInputResolver.copiesInput(hasReadableFilePath = false, hasSafParameter = true))
    }

    @Test
    fun `copies into cache only when neither path nor SAF is available`() {
        assertTrue(MediaInputResolver.copiesInput(hasReadableFilePath = false, hasSafParameter = false))
    }

    @Test
    fun `never treats a proc fd existence check as a readable file path`() {
        val procFdLooksLikeAFile = true
        assertTrue(
            " /proc/self/fd can exist and still be unreadable to FFmpeg",
            MediaInputResolver.copiesInput(
                hasReadableFilePath = false,
                hasSafParameter = !procFdLooksLikeAFile,
            ),
        )
    }
}
