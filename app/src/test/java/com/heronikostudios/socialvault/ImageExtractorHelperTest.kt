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

    @Test
    fun testParseExtractedImages_replacesLowerResolutionWithMaster() {
        val json = """
            [
                {"url": "https://instagram.fcia1-1.fna.fbcdn.net/v/t51.2885-15/photo1.jpg?stp=dst-jpg_e35_p640x640&nc=1", "width": 640, "height": 640, "alt": "slide 1"},
                {"url": "https://instagram.fcia1-1.fna.fbcdn.net/v/t51.2885-15/photo2.jpg?stp=dst-jpg_e35_p1080x1080&nc=2", "width": 1080, "height": 1080, "alt": "slide 2"},
                {"url": "https://instagram.fcia1-1.fna.fbcdn.net/v/t51.2885-15/photo1.jpg?stp=dst-jpg_e35_p1080x1080&nc=3", "width": 1080, "height": 1080, "alt": "slide 1 high-res"}
            ]
        """.trimIndent()

        val result = ImageExtractorHelper.parseExtractedImages(json, "instagram")
        assertEquals(2, result.size)
        // Slide 1 was updated to high-res (1080x1080)
        assertEquals(1080, result[0].width)
        assertEquals(1080, result[0].height)
        assertTrue(result[0].url.contains("p1080x1080"))
        // Order was preserved: slide 1 first, slide 2 second
        assertTrue(result[1].url.contains("photo2.jpg"))
    }

    @Test
    fun testCleanMediaUrl_removesByteRangesAndUnescapes() {
        val dirty = """https:\/\/scontent.cdninstagram.com\/v\/t50.1234-16\/reel.mp4?bytestart=0\u0026byteend=5000\u0026_nc_cat=100"""
        val clean = ImageExtractorHelper.cleanMediaUrl(dirty)
        assertEquals("https://scontent.cdninstagram.com/v/t50.1234-16/reel.mp4?_nc_cat=100", clean)

        val cleanSimple = ImageExtractorHelper.cleanMediaUrl("https://example.com/video.mp4?bytestart=0&byteend=100")
        assertEquals("https://example.com/video.mp4", cleanSimple)

        val nullResult = ImageExtractorHelper.cleanMediaUrl("not-a-url")
        org.junit.Assert.assertNull(nullResult)
    }

    @Test
    fun testParseActiveMedia_validVideoAndImage() {
        val bothJson = """{
            "videoUrl": "https://instagram.fcia1-1.fna.fbcdn.net/v/t50.1234/story.mp4?bytestart=0&byteend=1000&cat=1",
            "imageUrl": "https://instagram.fcia1-1.fna.fbcdn.net/v/t51.1234/story.jpg"
        }"""
        val activeBoth = ImageExtractorHelper.parseActiveMedia(bothJson)
        org.junit.Assert.assertNotNull(activeBoth)
        assertTrue(activeBoth!!.hasVideo)
        assertTrue(activeBoth.hasImage)
        assertEquals("https://instagram.fcia1-1.fna.fbcdn.net/v/t50.1234/story.mp4?cat=1", activeBoth.videoUrl)
        assertEquals("https://instagram.fcia1-1.fna.fbcdn.net/v/t51.1234/story.jpg", activeBoth.imageUrl)

        val imageOnlyJson = """{"imageUrl": "https://instagram.fcia1-1.fna.fbcdn.net/v/t51.1234/story.jpg"}"""
        val activeImageOnly = ImageExtractorHelper.parseActiveMedia(imageOnlyJson)
        org.junit.Assert.assertNotNull(activeImageOnly)
        assertFalse(activeImageOnly!!.hasVideo)
        assertTrue(activeImageOnly.hasImage)
        org.junit.Assert.assertNull(activeImageOnly.videoUrl)
        assertEquals("https://instagram.fcia1-1.fna.fbcdn.net/v/t51.1234/story.jpg", activeImageOnly.imageUrl)

        org.junit.Assert.assertNull(ImageExtractorHelper.parseActiveMedia(null))
        org.junit.Assert.assertNull(ImageExtractorHelper.parseActiveMedia("{}"))
        org.junit.Assert.assertNull(ImageExtractorHelper.parseActiveMedia("""{"videoUrl": "", "imageUrl": ""}"""))
    }

    @Test
    fun testGenerateStoryFileName() {
        val igVideo = ImageExtractorHelper.generateStoryFileName("instagram", isVideo = true)
        assertTrue(igVideo.startsWith("Instagram_story_video_"))
        assertTrue(igVideo.endsWith(".mp4"))

        val igPhoto = ImageExtractorHelper.generateStoryFileName("instagram", isVideo = false, ext = "webp")
        assertTrue(igPhoto.startsWith("Instagram_story_photo_"))
        assertTrue(igPhoto.endsWith(".webp"))

        val tiktokVideo = ImageExtractorHelper.generateStoryFileName("tiktok", isVideo = true)
        assertTrue(tiktokVideo.startsWith("TikTok_story_video_"))
        assertTrue(tiktokVideo.endsWith(".mp4"))
    }
}
