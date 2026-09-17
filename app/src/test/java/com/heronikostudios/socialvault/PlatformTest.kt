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
}
