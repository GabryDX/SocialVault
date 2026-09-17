package com.heronikostudios.socialvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTest {

    @Test
    fun domainAllowed_matchesExactAndSubdomain() {
        val platform = Platform(
            id = "tiktok",
            name = "TikTok",
            url = "https://www.tiktok.com",
            iconType = "tiktok",
            allowedDomains = listOf("tiktok.com", "tiktokcdn.com")
        )

        assertTrue(platform.isDomainAllowed("https://tiktok.com/@user"))
        assertTrue(platform.isDomainAllowed("https://www.tiktok.com/foryou"))
        assertTrue(platform.isDomainAllowed("https://v.tiktok.com/abc"))
        assertTrue(platform.isDomainAllowed("https://sf16-va.tiktokcdn.com/video.mp4"))

        assertFalse(platform.isDomainAllowed("https://evil-tiktok.com"))
        assertFalse(platform.isDomainAllowed("https://google.com"))
        assertFalse(platform.isDomainAllowed("https://facebook.com"))
    }

    @Test
    fun customPlatform_allowedDomainsDerivedFromUrl() {
        val platform = Platform(
            id = "custom_1",
            name = "Mastodon",
            url = "https://mastodon.social",
            iconType = "globe",
            isCustom = true
        )

        assertTrue(platform.isDomainAllowed("https://mastodon.social/explore"))
        assertTrue(platform.isDomainAllowed("https://files.mastodon.social/media.jpg"))
        assertFalse(platform.isDomainAllowed("https://twitter.com"))
    }

    @Test
    fun defaultPlatforms_orderedByPopularity() {
        val defaults = PlatformManager.DEFAULT_PLATFORMS
        val expectedOrder = listOf(
            "facebook",
            "youtube",
            "instagram",
            "tiktok",
            "reddit",
            "x",
            "pinterest",
            "linkedin",
            "threads",
            "twitch",
            "bluesky",
            "mastodon"
        )
        assertEquals(expectedOrder, defaults.map { it.id })
    }

    @Test
    fun domainAllowed_rejectsNonHttpSchemesAndMalformedUrls() {
        val platform = Platform(
            id = "tiktok",
            name = "TikTok",
            url = "https://www.tiktok.com",
            iconType = "tiktok",
            allowedDomains = listOf("tiktok.com", "tiktokcdn.com")
        )

        // Non-http schemes must be rejected
        assertFalse(platform.isDomainAllowed("file://tiktok.com/etc/hosts"))
        assertFalse(platform.isDomainAllowed("javascript:alert(1)"))
        assertFalse(platform.isDomainAllowed("content://com.android.providers.media/external"))
        assertFalse(platform.isDomainAllowed("data:text/html,<h1>test</h1>"))

        // Malformed or query with brackets should not throw and still be allowed if valid host
        assertTrue(platform.isDomainAllowed("https://tiktok.com/search?q=[music]"))
        assertFalse(platform.isDomainAllowed("https://evil.com/search?q=[music]"))
    }

    @Test
    fun normalizeUrl_addsHttpsWhenMissingAndTrims() {
        assertEquals("https://www.instagram.com/p/123", PlatformManager.normalizeUrl("www.instagram.com/p/123"))
        assertEquals("https://tiktok.com/@user", PlatformManager.normalizeUrl("   tiktok.com/@user   "))
        assertEquals("http://x.com/post", PlatformManager.normalizeUrl("http://x.com/post"))
        assertEquals("https://m.youtube.com", PlatformManager.normalizeUrl("https://m.youtube.com"))
    }

    @Test
    fun findMatchingPlatform_matchesPopularPlatformsAndShortLinks() {
        val platforms = PlatformManager.DEFAULT_PLATFORMS

        // Instagram reel and short link
        val instaReel = PlatformManager.findMatchingPlatform(platforms, "https://www.instagram.com/reel/C7abc/?igsh=123")
        assertEquals("instagram", instaReel?.id)
        val instaShort = PlatformManager.findMatchingPlatform(platforms, "https://instagr.am/p/C7xyz/")
        assertEquals("instagram", instaShort?.id)

        // TikTok video and short links
        val tiktokVideo = PlatformManager.findMatchingPlatform(platforms, "https://www.tiktok.com/@user/video/123456789")
        assertEquals("tiktok", tiktokVideo?.id)
        val tiktokShortVm = PlatformManager.findMatchingPlatform(platforms, "https://vm.tiktok.com/ZM8abc123/")
        assertEquals("tiktok", tiktokShortVm?.id)
        val tiktokShortVt = PlatformManager.findMatchingPlatform(platforms, "https://vt.tiktok.com/ZM8abc123/")
        assertEquals("tiktok", tiktokShortVt?.id)

        // YouTube video and short link
        val ytVideo = PlatformManager.findMatchingPlatform(platforms, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("youtube", ytVideo?.id)
        val ytShort = PlatformManager.findMatchingPlatform(platforms, "https://youtu.be/dQw4w9WgXcQ")
        assertEquals("youtube", ytShort?.id)

        // X/Twitter and t.co
        val xPost = PlatformManager.findMatchingPlatform(platforms, "https://x.com/user/status/123456")
        assertEquals("x", xPost?.id)
        val twitterPost = PlatformManager.findMatchingPlatform(platforms, "https://twitter.com/user/status/123456")
        assertEquals("x", twitterPost?.id)
        val tCoLink = PlatformManager.findMatchingPlatform(platforms, "https://t.co/xyz123")
        assertEquals("x", tCoLink?.id)

        // Facebook and fb.watch / fb.me
        val fbWatch = PlatformManager.findMatchingPlatform(platforms, "https://fb.watch/v/abc123xyz/")
        assertEquals("facebook", fbWatch?.id)
        val fbMe = PlatformManager.findMatchingPlatform(platforms, "https://fb.me/abc123xyz")
        assertEquals("facebook", fbMe?.id)

        // Pinterest pin.it
        val pinIt = PlatformManager.findMatchingPlatform(platforms, "https://pin.it/abcXYZ")
        assertEquals("pinterest", pinIt?.id)

        // LinkedIn lnkd.in
        val lnkdIn = PlatformManager.findMatchingPlatform(platforms, "https://lnkd.in/gAbCdEf")
        assertEquals("linkedin", lnkdIn?.id)

        // Reddit redd.it
        val reddIt = PlatformManager.findMatchingPlatform(platforms, "https://redd.it/abc123xyz")
        assertEquals("reddit", reddIt?.id)
    }

    @Test
    fun findMatchingPlatform_rejectsUnsupportedDomains() {
        val platforms = PlatformManager.DEFAULT_PLATFORMS

        // Random websites and phishing attacks must return null
        org.junit.Assert.assertNull(PlatformManager.findMatchingPlatform(platforms, "https://google.com"))
        org.junit.Assert.assertNull(PlatformManager.findMatchingPlatform(platforms, "https://phishing-instagram.com/login"))
        org.junit.Assert.assertNull(PlatformManager.findMatchingPlatform(platforms, "https://evil-tiktok.com/@user"))
        org.junit.Assert.assertNull(PlatformManager.findMatchingPlatform(platforms, "https://mybank.com/transfer"))
        org.junit.Assert.assertNull(PlatformManager.findMatchingPlatform(platforms, "javascript:alert(1)"))
    }
}
