package com.heronikostudios.socialvault

import java.net.URI

data class Platform(
    val id: String,
    val name: String,
    val url: String,
    val iconType: String = "globe",
    val accentColor: String = "#0284C7",
    val allowedDomains: List<String> = emptyList(),
    val isCustom: Boolean = false
) {
    val iconResId: Int
        get() = when (iconType) {
            "facebook" -> R.drawable.ic_platform_facebook
            "youtube" -> R.drawable.ic_platform_youtube
            "instagram" -> R.drawable.ic_platform_instagram
            "tiktok" -> R.drawable.ic_platform_tiktok
            "reddit" -> R.drawable.ic_platform_reddit
            "x" -> R.drawable.ic_platform_x
            "pinterest" -> R.drawable.ic_platform_pinterest
            "linkedin" -> R.drawable.ic_platform_linkedin
            "threads" -> R.drawable.ic_platform_threads
            "twitch" -> R.drawable.ic_platform_twitch
            "bluesky" -> R.drawable.ic_platform_bluesky
            "mastodon" -> R.drawable.ic_platform_mastodon
            else -> R.drawable.ic_globe
        }

    fun isDomainAllowed(targetUrl: String): Boolean {
        val host = extractHost(targetUrl) ?: return false
        val domains = if (allowedDomains.isNotEmpty()) {
            allowedDomains
        } else {
            val mainHost = extractHost(url) ?: return false
            listOf(mainHost)
        }
        return domains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    private fun extractHost(urlString: String): String? {
        return try {
            URI(urlString).host?.lowercase()
        } catch (_: Exception) {
            null
        }
    }
}
