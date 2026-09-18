package com.heronikostudios.socialvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeStreamHelperTest {

    @Test
    fun testYouTubeStreamHelperInitialization() {
        YouTubeStreamHelper.init()
        assertNotNull(YouTubeStreamHelper)
    }

    @Test
    fun testIsYouTubeVideoUrl() {
        assertTrue(YouTubeStreamHelper.isYouTubeVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YouTubeStreamHelper.isYouTubeVideoUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=10s"))
        assertTrue(YouTubeStreamHelper.isYouTubeVideoUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(YouTubeStreamHelper.isYouTubeVideoUrl("https://www.youtube.com/shorts/abcdef12345"))
        assertTrue(YouTubeStreamHelper.isYouTubeVideoUrl("https://m.youtube.com/shorts/abcdef12345"))
        assertTrue(YouTubeStreamHelper.isYouTubeVideoUrl("https://www.youtube.com/live/abcdef12345"))
        assertTrue(YouTubeStreamHelper.isYouTubeVideoUrl("https://www.youtube.com/embed/dQw4w9WgXcQ"))

        assertFalse(YouTubeStreamHelper.isYouTubeVideoUrl("https://m.youtube.com/"))
        assertFalse(YouTubeStreamHelper.isYouTubeVideoUrl("https://www.youtube.com/feed/subscriptions"))
        assertFalse(YouTubeStreamHelper.isYouTubeVideoUrl("https://m.youtube.com/results?search_query=android"))
        assertFalse(YouTubeStreamHelper.isYouTubeVideoUrl("https://www.google.com/"))
        assertFalse(YouTubeStreamHelper.isYouTubeVideoUrl(""))
        assertFalse(YouTubeStreamHelper.isYouTubeVideoUrl(null))
    }

    @Test
    fun testYouTubeStreamItemSafeFileNameAndProperties() {
        val videoItem = YouTubeStreamItem(
            title = "Test Video: How to Build / Test * App? [2026] <HQ>",
            resolution = "720p",
            formatName = "MP4 (Video + Audio)",
            url = "https://example.com/stream.mp4",
            isAudio = false
        )
        assertEquals("mp4", videoItem.fileExtension)
        assertEquals("video/mp4", videoItem.mimeType)
        assertEquals("Test Video_ How to Build _ Test _ App_ [2026] _HQ_.mp4", videoItem.safeFileName)

        val audioItem = YouTubeStreamItem(
            title = "Awesome Song | Artist?",
            resolution = "128 kbps",
            formatName = "Audio (M4A)",
            url = "https://example.com/audio.m4a",
            isAudio = true
        )
        assertEquals("m4a", audioItem.fileExtension)
        assertEquals("audio/mp4", audioItem.mimeType)
        assertEquals("Awesome Song _ Artist_.m4a", audioItem.safeFileName)

        val blankItem = YouTubeStreamItem(
            title = "   ///   ",
            resolution = "360p",
            formatName = "MP4",
            url = "https://example.com/blank.mp4",
            isAudio = false
        )
        assertTrue(blankItem.safeFileName.endsWith(".mp4"))

        val leadingDotItem = YouTubeStreamItem(
            title = "...hidden_file...",
            resolution = "720p",
            formatName = "MP4",
            url = "https://example.com/stream.mp4",
            isAudio = false
        )
        assertEquals("hidden_file.mp4", leadingDotItem.safeFileName)

        val longTitle = "A".repeat(200)
        val longItem = YouTubeStreamItem(
            title = longTitle,
            resolution = "720p",
            formatName = "MP4",
            url = "https://example.com/stream.mp4",
            isAudio = false
        )
        assertTrue(longItem.safeFileName.length <= 105)
    }

    @Test
    fun testExtractStreamsHandling() {
        YouTubeStreamHelper.init()
        val result = YouTubeStreamHelper.extractStreams("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(result.isSuccess)
        val items = result.getOrNull()
        assertNotNull(items)
        assertTrue(items!!.isNotEmpty())
        assertTrue(items.any { !it.isAudio })
        assertTrue(items.any { it.isAudio })
    }

    @Test
    fun testExtractStreamsInvalidUrl() {
        YouTubeStreamHelper.init()
        val result = YouTubeStreamHelper.extractStreams("https://www.youtube.com/watch?v=invalid_id_not_found_12345")
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }
}
