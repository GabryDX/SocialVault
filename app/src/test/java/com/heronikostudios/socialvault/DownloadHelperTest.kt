package com.heronikostudios.socialvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        // URLs with format in query parameters (Twitter, Reddit, etc.)
        assertTrue(DownloadHelper.isMediaUrl("https://pbs.twimg.com/media/GXabcdef?format=jpg&name=large"))
        assertTrue(DownloadHelper.isMediaUrl("https://pbs.twimg.com/media/GXabcdef?format=png&name=orig"))
        assertTrue(DownloadHelper.isMediaUrl("https://preview.redd.it/xyz?auto=webp"))
        assertTrue(DownloadHelper.isMediaUrl("https://cdn.example.com/media/item123?ext=webp"))

        // URLs with Twitter colon suffixes
        assertTrue(DownloadHelper.isMediaUrl("https://pbs.twimg.com/media/GXabcdef.jpg:large"))
        assertTrue(DownloadHelper.isMediaUrl("https://pbs.twimg.com/media/GXabcdef.png:orig"))
    }

    @Test
    fun isMediaUrl_rejectsNonMediaUrls() {
        assertFalse(DownloadHelper.isMediaUrl("https://example.com/page.html"))
        assertFalse(DownloadHelper.isMediaUrl("https://reddit.com/r/technology"))
        assertFalse(DownloadHelper.isMediaUrl("https://x.com/home"))
        assertFalse(DownloadHelper.isMediaUrl("https://youtube.com/watch?v=123"))
        assertFalse(DownloadHelper.isMediaUrl(null))
        assertFalse(DownloadHelper.isMediaUrl(""))
    }

    @Test
    fun isImageUrl_distinguishesImagesFromVideos() {
        assertTrue(DownloadHelper.isImageUrl("https://example.com/photo.jpg"))
        assertTrue(DownloadHelper.isImageUrl("https://pbs.twimg.com/media/GXabcdef?format=jpg&name=large"))
        assertTrue(DownloadHelper.isImageUrl("https://pbs.twimg.com/media/GXabcdef?format=png"))
        assertTrue(DownloadHelper.isImageUrl("https://preview.redd.it/xyz?auto=webp"))

        assertFalse(DownloadHelper.isImageUrl("https://example.com/video.mp4"))
        assertFalse(DownloadHelper.isImageUrl("https://example.com/video.webm"))
        assertFalse(DownloadHelper.isImageUrl("https://x.com/home"))
    }

    @Test
    fun inferMediaFormat_detectsDeclaredMime() {
        val jpgFormat = DownloadHelper.inferMediaFormat("https://example.com/stream", declaredMime = "image/jpeg")
        assertNotNull(jpgFormat)
        assertEquals("jpg", jpgFormat?.first)
        assertEquals("image/jpeg", jpgFormat?.second)

        val pngFormat = DownloadHelper.inferMediaFormat("https://example.com/stream", declaredMime = "image/png")
        assertNotNull(pngFormat)
        assertEquals("png", pngFormat?.first)
        assertEquals("image/png", pngFormat?.second)

        val mp4Format = DownloadHelper.inferMediaFormat("https://example.com/stream", declaredMime = "video/mp4")
        assertNotNull(mp4Format)
        assertEquals("mp4", mp4Format?.first)
        assertEquals("video/mp4", mp4Format?.second)
    }

    @Test
    fun inferMediaFormat_detectsQueryParams() {
        val twitterJpg = DownloadHelper.inferMediaFormat("https://pbs.twimg.com/media/GXabcdef?format=jpg&name=large")
        assertEquals(Pair("jpg", "image/jpeg"), twitterJpg)

        val twitterPng = DownloadHelper.inferMediaFormat("https://pbs.twimg.com/media/GXabcdef?format=png&name=large")
        assertEquals(Pair("png", "image/png"), twitterPng)

        val redditWebp = DownloadHelper.inferMediaFormat("https://preview.redd.it/xyz123?auto=webp")
        assertEquals(Pair("webp", "image/webp"), redditWebp)

        val customExt = DownloadHelper.inferMediaFormat("https://cdn.example.com/asset?ext=gif")
        assertEquals(Pair("gif", "image/gif"), customExt)
    }

    @Test
    fun inferMediaFormat_fallsBackToIsImage() {
        val opaqueImage = DownloadHelper.inferMediaFormat("https://example.com/opaque/resource", isImage = true)
        assertEquals(Pair("jpg", "image/jpeg"), opaqueImage)

        val opaqueUnknown = DownloadHelper.inferMediaFormat("https://example.com/opaque/resource", isImage = false)
        assertNull(opaqueUnknown)
    }

    @Test
    fun resolveFileName_fixesTwitterImageFileNameAndMime() {
        // Modern Twitter URL where path lacks extension and format is in query param
        val (fileName, mime) = DownloadHelper.resolveFileName(
            url = "https://pbs.twimg.com/media/GXabcdef?format=jpg&name=large",
            isImage = true
        )
        assertTrue("Filename should end with .jpg, but was: $fileName", fileName.endsWith(".jpg"))
        assertFalse("Filename must not end with .bin", fileName.endsWith(".bin"))
        assertEquals("image/jpeg", mime)
    }

    @Test
    fun resolveFileName_handlesTwitterColonSuffixes() {
        // Legacy Twitter URL with colon size suffix
        val (fileName, mime) = DownloadHelper.resolveFileName(
            url = "https://pbs.twimg.com/media/GXabcdef.jpg:large",
            isImage = true
        )
        assertEquals("GXabcdef.jpg", fileName)
        assertEquals("image/jpeg", mime)
    }

    @Test
    fun resolveFileName_handlesRedditAutoWebp() {
        val (fileName, mime) = DownloadHelper.resolveFileName(
            url = "https://preview.redd.it/hash987654?auto=webp&s=abcdef",
            isImage = true
        )
        assertTrue("Filename should end with .webp, but was: $fileName", fileName.endsWith(".webp"))
        assertEquals("image/webp", mime)
    }

    @Test
    fun resolveFileName_appendsInferredExtensionToCustomFileName() {
        val (fileName, mime) = DownloadHelper.resolveFileName(
            url = "https://pbs.twimg.com/media/GXabcdef?format=png&name=orig",
            customFileName = "user_saved_image",
            isImage = true
        )
        assertEquals("user_saved_image.png", fileName)
        assertEquals("image/png", mime)
    }

    @Test
    fun sanitizeFileName_removesIllegalCharacters() {
        assertEquals("safe_name", DownloadHelper.sanitizeFileName("safe/name"))
        assertEquals("safe_name", DownloadHelper.sanitizeFileName("safe:name"))
        assertEquals("safe_name", DownloadHelper.sanitizeFileName("safe?name"))
        assertEquals("safe_name", DownloadHelper.sanitizeFileName("..safe_name.."))
    }
}
