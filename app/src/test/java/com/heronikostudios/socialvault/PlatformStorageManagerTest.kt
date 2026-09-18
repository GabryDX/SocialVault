package com.heronikostudios.socialvault

import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformStorageManagerTest {

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
    fun getCandidateDomains_resolvesYouTubeDomains() {
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
