package com.heronikostudios.socialvault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolisherTest {

    @Test
    fun polishUrl_removesUniversalTrackingParameters() {
        val dirtyUrl = "https://example.com/page?utm_source=newsletter&utm_medium=email&utm_campaign=summer_sale&fbclid=IwAR123&gclid=xyz456"
        val result = UrlPolisher.polishUrl(dirtyUrl)

        assertTrue(result.wasPolished)
        assertEquals("https://example.com/page", result.polishedUrl)
        assertTrue(result.removedParams.contains("utm_source"))
        assertTrue(result.removedParams.contains("utm_medium"))
        assertTrue(result.removedParams.contains("utm_campaign"))
        assertTrue(result.removedParams.contains("fbclid"))
        assertTrue(result.removedParams.contains("gclid"))
    }

    @Test
    fun polishUrl_removesInstagramTracking() {
        val instaReel = "https://www.instagram.com/reel/C7abc123/?igsh=MWZ4d3J2bnEx&utm_source=qr"
        val result = UrlPolisher.polishUrl(instaReel)

        assertTrue(result.wasPolished)
        assertEquals("https://www.instagram.com/reel/C7abc123/", result.polishedUrl)
        assertTrue(result.removedParams.contains("igsh"))
        assertTrue(result.removedParams.contains("utm_source"))

        val instaShort = "https://instagr.am/p/C-12345?igshid=abcxyz"
        val resultShort = UrlPolisher.polishUrl(instaShort)
        assertTrue(resultShort.wasPolished)
        assertEquals("https://instagr.am/p/C-12345", resultShort.polishedUrl)
        assertTrue(resultShort.removedParams.contains("igshid"))
    }

    @Test
    fun polishUrl_removesTikTokTracking() {
        val tiktokVideo = "https://www.tiktok.com/@creator/video/7123456789?_t=8nXYZ123&_r=1&sender_device=pc&is_from_webapp=1"
        val result = UrlPolisher.polishUrl(tiktokVideo)

        assertTrue(result.wasPolished)
        assertEquals("https://www.tiktok.com/@creator/video/7123456789", result.polishedUrl)
        assertTrue(result.removedParams.contains("_t"))
        assertTrue(result.removedParams.contains("_r"))
        assertTrue(result.removedParams.contains("sender_device"))
        assertTrue(result.removedParams.contains("is_from_webapp"))
    }

    @Test
    fun polishUrl_removesYouTubeTrackingWhilePreservingFunctionalParams() {
        // Must preserve video ID 'v' and timestamp 't'
        val ytVideo = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=AbCdEfGhIjKl&t=42s&feature=share"
        val result = UrlPolisher.polishUrl(ytVideo)

        assertTrue(result.wasPolished)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s", result.polishedUrl)
        assertTrue(result.removedParams.contains("si"))
        assertTrue(result.removedParams.contains("feature"))

        // youtu.be short link
        val ytShort = "https://youtu.be/dQw4w9WgXcQ?si=u1kL_mNoPq"
        val resultShort = UrlPolisher.polishUrl(ytShort)
        assertTrue(resultShort.wasPolished)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", resultShort.polishedUrl)
        assertTrue(resultShort.removedParams.contains("si"))
    }

    @Test
    fun polishUrl_removesTwitterXTracking() {
        val xPost = "https://x.com/user/status/1234567890?s=20&t=abcdef12345"
        val result = UrlPolisher.polishUrl(xPost)

        assertTrue(result.wasPolished)
        assertEquals("https://x.com/user/status/1234567890", result.polishedUrl)
        assertTrue(result.removedParams.contains("s"))
        assertTrue(result.removedParams.contains("t"))

        val twitterPost = "https://twitter.com/user/status/1234567890?ref_src=twsrc%5Etfw"
        val resultTwitter = UrlPolisher.polishUrl(twitterPost)
        assertTrue(resultTwitter.wasPolished)
        assertEquals("https://twitter.com/user/status/1234567890", resultTwitter.polishedUrl)
        assertTrue(resultTwitter.removedParams.contains("ref_src"))
    }

    @Test
    fun polishUrl_removesFacebookTrackingWhilePreservingVideoId() {
        val fbWatch = "https://www.facebook.com/watch/?v=987654&mibextid=wwXIfr&fbclid=IwAR987"
        val result = UrlPolisher.polishUrl(fbWatch)

        assertTrue(result.wasPolished)
        assertEquals("https://www.facebook.com/watch/?v=987654", result.polishedUrl)
        assertTrue(result.removedParams.contains("mibextid"))
        assertTrue(result.removedParams.contains("fbclid"))
    }

    @Test
    fun polishUrl_removesRedditTracking() {
        val redditPost = "https://www.reddit.com/r/android/comments/123/sample_post/?utm_source=share&utm_medium=web3x&share_id=xyz789"
        val result = UrlPolisher.polishUrl(redditPost)

        assertTrue(result.wasPolished)
        assertEquals("https://www.reddit.com/r/android/comments/123/sample_post/", result.polishedUrl)
        assertTrue(result.removedParams.contains("utm_source"))
        assertTrue(result.removedParams.contains("utm_medium"))
        assertTrue(result.removedParams.contains("share_id"))
    }

    @Test
    fun polishUrl_removesThreadsAndLinkedInTracking() {
        val threadsPost = "https://www.threads.net/@user/post/C-abc123?xmt=AQGZ123&s=09"
        val resultThreads = UrlPolisher.polishUrl(threadsPost)
        assertTrue(resultThreads.wasPolished)
        assertEquals("https://www.threads.net/@user/post/C-abc123", resultThreads.polishedUrl)
        assertTrue(resultThreads.removedParams.contains("xmt"))
        assertTrue(resultThreads.removedParams.contains("s"))

        val linkedInPost = "https://www.linkedin.com/posts/activity-123456?utm_source=share&rcm=ACoAA123"
        val resultLinkedIn = UrlPolisher.polishUrl(linkedInPost)
        assertTrue(resultLinkedIn.wasPolished)
        assertEquals("https://www.linkedin.com/posts/activity-123456", resultLinkedIn.polishedUrl)
        assertTrue(resultLinkedIn.removedParams.contains("utm_source"))
        assertTrue(resultLinkedIn.removedParams.contains("rcm"))
    }

    @Test
    fun polishUrl_leavesCleanUrlsUnchanged() {
        val cleanInstagram = "https://www.instagram.com/reel/C7abc123/"
        val res1 = UrlPolisher.polishUrl(cleanInstagram)
        assertFalse(res1.wasPolished)
        assertEquals(cleanInstagram, res1.polishedUrl)

        val nonTrackingQuery = "https://en.wikipedia.org/wiki/Kotlin?search=yes"
        val res2 = UrlPolisher.polishUrl(nonTrackingQuery)
        assertFalse(res2.wasPolished)
        assertEquals(nonTrackingQuery, res2.polishedUrl)
    }

    @Test
    fun polishUrl_handlesUrlWithoutScheme() {
        val noScheme = "instagram.com/reel/C7abc/?igsh=123"
        val result = UrlPolisher.polishUrl(noScheme)
        assertTrue(result.wasPolished)
        assertEquals("instagram.com/reel/C7abc/", result.polishedUrl)
    }
}
