package com.heronikostudios.socialvault

import java.net.URI

object TrackerBlocker {

    // Universal analytics, tracking, telemetry, and cross-site advertising domains
    private val UNIVERSAL_TRACKER_DOMAINS = setOf(
        // Google Analytics, Tag Manager, DoubleClick, AdServices
        "google-analytics.com",
        "googletagmanager.com",
        "analytics.google.com",
        "doubleclick.net",
        "googleadservices.com",
        "pagead2.googlesyndication.com",

        // Mobile attribution & telemetry SDK beacons
        "branch.io",
        "app.link",
        "appsflyer.com",
        "adjust.com",
        "kochava.com",
        "singular.net",
        "telemetry.mozilla.org",

        // Cross-site ad networks, data brokers & retargeters
        "criteo.com",
        "criteo.net",
        "scorecardresearch.com",
        "outbrain.com",
        "taboola.com",
        "adnxs.com",
        "rubiconproject.com",
        "pubmatic.com",
        "casalemedia.com",
        "openx.net",
        "smartadserver.com",
        "moatads.com",
        "adroll.com",

        // Session recorders & behavioral trackers
        "hotjar.com",
        "clarity.ms",
        "mouseflow.com",
        "crazyegg.com",
        "fullstory.com",
        "segment.io",
        "segment.com"
    )

    // Meta tracking domains (blocked when running on non-Meta platforms)
    private val META_TRACKER_DOMAINS = setOf(
        "connect.facebook.net"
    )

    /**
     * Determines whether a resource request URL belongs to a known tracker, telemetry beacon,
     * or cross-site advertising script.
     *
     * @param url The resource URL being requested.
     * @param currentPlatform The platform context in which the request is made.
     * @return true if the resource should be blocked, false otherwise.
     */
    fun isTracker(url: String?, currentPlatform: Platform? = null): Boolean {
        if (url.isNullOrBlank()) return false
        val host = extractHost(url) ?: return false

        // 1. Check universal tracker domains
        for (tracker in UNIVERSAL_TRACKER_DOMAINS) {
            if (host == tracker || host.endsWith(".$tracker")) {
                return true
            }
        }

        // 2. Block Meta trackers (e.g. Facebook Pixel) on non-Meta platforms
        val platformId = currentPlatform?.id
        val isMetaPlatform = platformId == "facebook" || platformId == "instagram" || platformId == "threads"
        if (!isMetaPlatform) {
            for (tracker in META_TRACKER_DOMAINS) {
                if (host == tracker || host.endsWith(".$tracker")) {
                    return true
                }
            }
        }

        return false
    }

    private fun extractHost(urlString: String): String? {
        return try {
            val uri = URI(urlString)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            uri.host?.lowercase()
        } catch (_: Exception) {
            try {
                val clean = urlString.substringBefore('?').substringBefore('#')
                val uri = URI(clean)
                val scheme = uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") return null
                uri.host?.lowercase()
            } catch (_: Exception) {
                null
            }
        }
    }
}
