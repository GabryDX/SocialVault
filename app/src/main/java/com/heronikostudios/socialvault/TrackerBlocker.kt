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

    val EMPTY_BYTES = ByteArray(0)

    /**
     * Determines whether a resource request URL belongs to a known tracker, telemetry beacon,
     * or cross-site advertising script.
     *
     * Overload taking android.net.Uri directly, avoiding string allocations on hot paths.
     */
    fun isTracker(uri: android.net.Uri, currentPlatform: Platform? = null): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return isTrackerHost(host, currentPlatform)
    }

    /**
     * Determines whether a resource request URL string belongs to a known tracker, telemetry beacon,
     * or cross-site advertising script.
     */
    fun isTracker(url: String?, currentPlatform: Platform? = null): Boolean {
        if (url.isNullOrBlank()) return false
        val host = extractHost(url) ?: return false
        return isTrackerHost(host, currentPlatform)
    }

    /**
     * Checks if a domain or any of its parent domain segments match the tracker sets.
     * Runs in O(1) time per segment (at most 2-3 set lookups) instead of linear scan.
     */
    fun isTrackerHost(host: String, currentPlatform: Platform? = null): Boolean {
        // 1. Check universal tracker domains hierarchically
        if (matchesTrackerDomain(host, UNIVERSAL_TRACKER_DOMAINS)) {
            return true
        }

        // 2. Block Meta trackers (e.g. Facebook Pixel) on non-Meta platforms
        val platformId = currentPlatform?.id
        val isMetaPlatform = platformId == "facebook" || platformId == "instagram" || platformId == "threads"
        if (!isMetaPlatform && matchesTrackerDomain(host, META_TRACKER_DOMAINS)) {
            return true
        }

        return false
    }

    private fun matchesTrackerDomain(host: String, trackerSet: Set<String>): Boolean {
        var current: String? = host
        while (current != null) {
            if (trackerSet.contains(current)) {
                return true
            }
            val dot = current.indexOf('.')
            current = if (dot != -1 && dot < current.length - 1) {
                current.substring(dot + 1)
            } else {
                null
            }
        }
        return false
    }

    private fun extractHost(urlString: String): String? {
        return Platform.extractHost(urlString)
    }
}
