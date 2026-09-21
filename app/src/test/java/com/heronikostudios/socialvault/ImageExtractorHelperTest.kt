package com.heronikostudios.socialvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageExtractorHelperTest {

    @Test
    fun testParseExtractedImages_validJson() {
        val json = """
            [
                {"url": "https://instagram.fcia1-1.fna.fbcdn.net/v/photo1.jpg", "width": 1080, "height": 1350, "alt": "A sunset"},
                {"url": "https://instagram.fcia1-1.fna.fbcdn.net/v/photo2.jpg", "width": 1080, "height": 1080, "alt": ""}
            ]
        """.trimIndent()

        val result = ImageExtractorHelper.parseExtractedImages(json, "instagram")
        assertEquals(2, result.size)
        assertEquals("https://instagram.fcia1-1.fna.fbcdn.net/v/photo1.jpg", result[0].url)
        assertEquals(1080, result[0].width)
        assertEquals(1350, result[0].height)
        assertEquals("A sunset", result[0].alt)
        assertTrue(result[0].isSelected)
        assertEquals("1080 × 1350", result[0].displayResolution)
    }

    @Test
    fun testParseExtractedImages_emptyAndInvalidJson() {
        assertTrue(ImageExtractorHelper.parseExtractedImages(null).isEmpty())
        assertTrue(ImageExtractorHelper.parseExtractedImages("").isEmpty())
        assertTrue(ImageExtractorHelper.parseExtractedImages("[]").isEmpty())
        assertTrue(ImageExtractorHelper.parseExtractedImages("invalid json").isEmpty())
    }

    @Test
    fun testParseExtractedImages_filtersNonHttpAndDuplicates() {
        val json = """
            [
                {"url": "data:image/png;base64,iVBORw0KGgo...", "width": 50, "height": 50},
                {"url": "blob:https://example.com/uuid", "width": 500, "height": 500},
                {"url": "https://example.com/image.jpg", "width": 800, "height": 600},
                {"url": "https://example.com/image.jpg", "width": 800, "height": 600}
            ]
        """.trimIndent()

        val result = ImageExtractorHelper.parseExtractedImages(json)
        assertEquals(1, result.size)
        assertEquals("https://example.com/image.jpg", result[0].url)
    }

    @Test
    fun testOptimizeTwitterImageUrl_upgradesResolution() {
        val standard = "https://pbs.twimg.com/media/GQ_AbcDef123?format=jpg&name=small"
        val optimized = ImageExtractorHelper.optimizeTwitterImageUrl(standard)
        assertEquals("https://pbs.twimg.com/media/GQ_AbcDef123?format=jpg&name=large", optimized)

        val raw = "https://pbs.twimg.com/media/GQ_AbcDef123"
        assertEquals("https://pbs.twimg.com/media/GQ_AbcDef123?name=large", ImageExtractorHelper.optimizeTwitterImageUrl(raw))
    }

    @Test
    fun testGenerateSafeFileName_instagramCarouselAndSingle() {
        val item = ExtractedImage("https://example.com/photo.jpg?token=123", 1080, 1080)

        // Single image
        val singleName = ImageExtractorHelper.generateSafeFileName(item, "instagram", 0, 1)
        assertTrue(singleName.startsWith("Instagram_photo_"))
        assertTrue(singleName.endsWith(".jpg"))
        assertFalse(singleName.contains("_of_"))

        // Carousel image 2 of 4
        val carouselName = ImageExtractorHelper.generateSafeFileName(item, "instagram", 1, 4)
        assertTrue(carouselName.startsWith("Instagram_photo_2_of_4_"))
        assertTrue(carouselName.endsWith(".jpg"))
    }

    @Test
    fun testGenerateSafeFileName_differentPlatforms() {
        val webpItem = ExtractedImage("https://reddit.com/pic.webp", 800, 600)
        val redditName = ImageExtractorHelper.generateSafeFileName(webpItem, "reddit", 0, 1)
        assertTrue(redditName.startsWith("Reddit_photo_"))
        assertTrue(redditName.endsWith(".webp"))

        val xItem = ExtractedImage("https://pbs.twimg.com/media/pic?format=png&name=large", 1200, 800)
        val xName = ImageExtractorHelper.generateSafeFileName(xItem, "x", 2, 3)
        assertTrue(xName.startsWith("X_photo_3_of_3_"))
        assertTrue(xName.endsWith(".png"))
    }

    @Test
    fun testParseExtractedImages_escapedJsonString() {
        // evaluateJavascript returns JSON-encoded string
        val escaped = "\"[{\\\"url\\\":\\\"https://instagram.com/p1.jpg\\\",\\\"width\\\":1080,\\\"height\\\":1080}]\""
        val result = ImageExtractorHelper.parseExtractedImages(escaped)
        assertEquals(1, result.size)
        assertEquals("https://instagram.com/p1.jpg", result[0].url)
    }
}
