package com.heronikostudios.socialvault

import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformStorageManagerTest {

    @Test
    fun testMultiProfileApiAvailability() {
        assertNotNull(WebViewFeature.MULTI_PROFILE)
        assertEquals("MULTI_PROFILE", WebViewFeature.MULTI_PROFILE)
    }

    @Test
    fun getProfileName_derivesCleanAndUniqueNames() {
        val yt = Platform(id = "youtube", name = "YouTube", url = "https://m.youtube.com")
        assertEquals("sv_profile_youtube", PlatformStorageManager.getProfileName(yt))

        val ig = Platform(id = "instagram", name = "Instagram", url = "https://www.instagram.com")
        assertEquals("sv_profile_instagram", PlatformStorageManager.getProfileName(ig))

        val custom = Platform(id = "custom-site.org#1", name = "Custom", url = "https://custom.org")
        assertEquals("sv_profile_custom_site_org_1", PlatformStorageManager.getProfileName(custom))
    }

    @Test
    fun getCandidateDomains_resolvesRootAndSubdomainsForInstagram() {
        val platform = Platform(
            id = "instagram",
            name = "Instagram",
            url = "https://www.instagram.com",
            iconType = "instagram",
            allowedDomains = listOf("instagram.com", "cdninstagram.com", "instagr.am")
        )

        val domains = PlatformStorageManager.getCandidateDomains(platform)

        assertTrue(domains.contains("instagram.com"))
        assertTrue(domains.contains("www.instagram.com"))
        assertTrue(domains.contains("m.instagram.com"))
        assertTrue(domains.contains("cdninstagram.com"))
        assertTrue(domains.contains("instagr.am"))
    }

    @Test
    fun getCandidateDomains_resolvesTikTokDomainsAndShortLinks() {
        val platform = Platform(
            id = "tiktok",
            name = "TikTok",
            url = "https://www.tiktok.com",
            iconType = "tiktok",
            allowedDomains = listOf("tiktok.com", "tiktokcdn.com", "vm.tiktok.com", "vt.tiktok.com")
        )

        val domains = PlatformStorageManager.getCandidateDomains(platform)

        assertTrue(domains.contains("tiktok.com"))
        assertTrue(domains.contains("www.tiktok.com"))
        assertTrue(domains.contains("m.tiktok.com"))
        assertTrue(domains.contains("tiktokcdn.com"))
        assertTrue(domains.contains("vm.tiktok.com"))
        assertTrue(domains.contains("vt.tiktok.com"))
    }

    @Test
    fun getCandidateDomains_resolvesYouTubeDomainsAndGoogleAuth() {
        val platform = Platform(
            id = "youtube",
            name = "YouTube",
            url = "https://m.youtube.com",
            iconType = "youtube",
            allowedDomains = listOf("youtube.com", "googlevideo.com", "ytimg.com", "youtu.be", "m.youtube.com")
        )

        val domains = PlatformStorageManager.getCandidateDomains(platform)

        assertTrue(domains.contains("youtube.com"))
        assertTrue(domains.contains("m.youtube.com"))
        assertTrue(domains.contains("www.youtube.com"))
        assertTrue(domains.contains("youtu.be"))
        // Check Google authentication & consent domains needed for complete logout
        assertTrue(domains.contains("google.com"))
        assertTrue(domains.contains("accounts.google.com"))
        assertTrue(domains.contains("consent.youtube.com"))
        assertTrue(domains.contains("consent.google.com"))
    }

    @Test
    fun getCandidateDomains_handlesCustomPlatform() {
        val custom = Platform(
            id = "custom_mastodon",
            name = "Mastodon",
            url = "https://mastodon.social",
            iconType = "globe",
            allowedDomains = listOf("mastodon.social"),
            isCustom = true
        )

        val domains = PlatformStorageManager.getCandidateDomains(custom)

        assertTrue(domains.contains("mastodon.social"))
        assertTrue(domains.contains("www.mastodon.social"))
        assertTrue(domains.contains("m.mastodon.social"))
    }
}
