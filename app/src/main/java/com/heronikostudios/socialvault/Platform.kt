package com.heronikostudios.socialvault

import java.net.URI

data class Platform(
    val id: String,
    val name: String,
    val url: String,
    val iconResId: Int = R.drawable.ic_globe,
    val accentColor: String = "#0284C7",
    val allowedDomains: List<String> = emptyList(),
    val isCustom: Boolean = false
) {
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
