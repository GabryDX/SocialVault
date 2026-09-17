package com.heronikostudios.socialvault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataStripperTest {

    @Test
    fun isImageExtension_recognizesCommonImageFormats() {
        assertTrue(MetadataStripper.isImageExtension("photo.jpg"))
        assertTrue(MetadataStripper.isImageExtension("image.jpeg"))
        assertTrue(MetadataStripper.isImageExtension("screenshot.png"))
        assertTrue(MetadataStripper.isImageExtension("picture.webp"))
        assertTrue(MetadataStripper.isImageExtension("banner.bmp"))
        assertTrue(MetadataStripper.isImageExtension("photo.heic"))
        assertTrue(MetadataStripper.isImageExtension("photo.HEIF"))
    }

    @Test
    fun isImageExtension_rejectsNonImageFormats() {
        assertFalse(MetadataStripper.isImageExtension("video.mp4"))
        assertFalse(MetadataStripper.isImageExtension("document.pdf"))
        assertFalse(MetadataStripper.isImageExtension("archive.zip"))
        assertFalse(MetadataStripper.isImageExtension("script.js"))
        assertFalse(MetadataStripper.isImageExtension(null))
    }
}
