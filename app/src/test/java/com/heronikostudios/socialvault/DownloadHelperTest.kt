package com.heronikostudios.socialvault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadHelperTest {

    @Test
    fun isMediaUrl_identifiesImageAndVideoExtensions() {
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/photo.jpg"))
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/photo.jpeg"))
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/image.png"))
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/graphic.webp"))
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/animation.gif"))
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/video.mp4"))
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/video.webm"))
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/video.mov"))
        assertTrue(DownloadHelper.isMediaUrl("https://example.com/video.mkv"))

        // With query parameters and fragments
        assertTrue(DownloadHelper.isMediaUrl("https://pbs.twimg.com/media/abc.jpg?format=jpg&name=large"))
        assertTrue(DownloadHelper.isMediaUrl("https://preview.redd.it/xyz.png?width=640#thumb"))
    }

    @Test
    fun isMediaUrl_rejectsNonMediaUrls() {
        assertFalse(DownloadHelper.isMediaUrl("https://example.com/page.html"))
        assertFalse(DownloadHelper.isMediaUrl("https://reddit.com/r/technology"))
        assertFalse(DownloadHelper.isMediaUrl("https://x.com/home"))
        assertFalse(DownloadHelper.isMediaUrl("https://youtube.com/watch?v=123"))
        assertFalse(DownloadHelper.isMediaUrl(null))
    }
}
