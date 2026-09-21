package com.heronikostudios.socialvault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerBlockerTest {

    private val redditPlatform = Platform(
        id = "reddit",
        name = "Reddit",
        url = "https://www.reddit.com",
        allowedDomains = listOf("reddit.com", "redd.it")
    )

    private val facebookPlatform = Platform(
        id = "facebook",
        name = "Facebook",
        url = "https://m.facebook.com",
        allowedDomains = listOf("facebook.com", "fbcdn.net")
    )

    @Test
    fun isTracker_identifiesGoogleAnalyticsAndAds() {
        assertTrue(TrackerBlocker.isTracker("https://www.google-analytics.com/analytics.js"))
        assertTrue(TrackerBlocker.isTracker("https://ssl.google-analytics.com/collect"))
        assertTrue(TrackerBlocker.isTracker("https://googletagmanager.com/gtm.js?id=GTM-12345"))
        assertTrue(TrackerBlocker.isTracker("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(TrackerBlocker.isTracker("https://stats.g.doubleclick.net/r/collect"))
    }

    @Test
    fun isTracker_identifiesAttributionAndTelemetry() {
        assertTrue(TrackerBlocker.isTracker("https://api2.branch.io/v1/open"))
        assertTrue(TrackerBlocker.isTracker("https://app.appsflyer.com/com.example.app"))
        assertTrue(TrackerBlocker.isTracker("https://app.adjust.com/event"))
        assertTrue(TrackerBlocker.isTracker("https://control.kochava.com/track/json"))
    }

    @Test
    fun isTracker_identifiesCrossSiteAdNetworks() {
        assertTrue(TrackerBlocker.isTracker("https://static.criteo.net/js/ld/ld.js"))
        assertTrue(TrackerBlocker.isTracker("https://sb.scorecardresearch.com/beacon.js"))
        assertTrue(TrackerBlocker.isTracker("https://widgets.outbrain.com/outbrain.js"))
        assertTrue(TrackerBlocker.isTracker("https://cdn.taboola.com/libtrc/unip/1234/tfa.js"))
    }

    @Test
    fun isTracker_identifiesBehavioralRecorders() {
        assertTrue(TrackerBlocker.isTracker("https://static.hotjar.com/c/hotjar-123.js"))
        assertTrue(TrackerBlocker.isTracker("https://www.clarity.ms/tag/abc12345"))
        assertTrue(TrackerBlocker.isTracker("https://cdn.segment.com/analytics.js/v1/123/analytics.min.js"))
    }

    @Test
    fun isTracker_blocksMetaPixelOnNonMetaPlatforms() {
        // Facebook tracking pixel on Reddit -> BLOCKED
        assertTrue(TrackerBlocker.isTracker("https://connect.facebook.net/en_US/fbevents.js", redditPlatform))

        // Facebook tracking pixel on Facebook -> ALLOWED
        assertFalse(TrackerBlocker.isTracker("https://connect.facebook.net/en_US/fbevents.js", facebookPlatform))
    }

    @Test
    fun isTracker_allowsNormalPlatformResources() {
        assertFalse(TrackerBlocker.isTracker("https://www.reddit.com/r/technology", redditPlatform))
        assertFalse(TrackerBlocker.isTracker("https://www.redditstatic.com/desktop2x/app.js", redditPlatform))
        assertFalse(TrackerBlocker.isTracker("https://m.youtube.com/watch?v=12345"))
        assertFalse(TrackerBlocker.isTracker("https://abs.twimg.com/responsive-web/client-web/main.js"))
    }

    @Test
    fun isTracker_handlesInvalidUrls() {
        assertFalse(TrackerBlocker.isTracker(null))
        assertFalse(TrackerBlocker.isTracker(""))
        assertFalse(TrackerBlocker.isTracker("not-a-url"))
        assertFalse(TrackerBlocker.isTracker("ftp://example.com"))
    }

    @Test
    fun isTrackerHost_identifiesSubdomainsAndRejectsSpoofs() {
        assertTrue(TrackerBlocker.isTrackerHost("analytics.google.com"))
        assertTrue(TrackerBlocker.isTrackerHost("sub.doubleclick.net"))
        assertTrue(TrackerBlocker.isTrackerHost("pagead2.googlesyndication.com"))
        assertFalse(TrackerBlocker.isTrackerHost("evil-doubleclick.net"))
        assertFalse(TrackerBlocker.isTrackerHost("mygoogle-analytics.com"))
        assertFalse(TrackerBlocker.isTrackerHost("twitter.com"))
        assertFalse(TrackerBlocker.isTrackerHost("reddit.com"))
    }
}
