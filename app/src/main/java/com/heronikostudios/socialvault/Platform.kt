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

    val host: String by lazy { extractHost(url) ?: url }

    fun isHostAllowed(host: String): Boolean {
        val lower = host.lowercase()
        val domains = if (allowedDomains.isNotEmpty()) {
            allowedDomains
        } else {
            val mainHost = extractHost(url) ?: return false
            listOf(mainHost)
        }
        return domains.any { domain ->
            lower == domain || lower.endsWith(".$domain")
        }
    }

    fun isDomainAllowed(targetUrl: String): Boolean {
        val targetHost = extractHost(targetUrl) ?: return false
        return isHostAllowed(targetHost)
    }

    companion object {
        fun extractHost(urlString: String): String? {
            return try {
                val uri = URI(urlString)
                val scheme = uri.scheme?.lowercase()
                if (scheme != null && scheme != "http" && scheme != "https") {
                    return null
                }
                uri.host?.lowercase()
            } catch (_: Exception) {
                try {
                    val base = urlString.substringBefore('?').substringBefore('#')
                    val uri = URI(base)
                    val scheme = uri.scheme?.lowercase()
                    if (scheme != null && scheme != "http" && scheme != "https") {
                        return null
                    }
                    uri.host?.lowercase()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
