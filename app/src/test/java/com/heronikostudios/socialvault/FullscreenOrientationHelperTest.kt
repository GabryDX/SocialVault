package com.heronikostudios.socialvault

import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenOrientationHelperTest {

    @Test
    fun testInstagram_alwaysReturnsPortrait() {
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("instagram", "https://www.instagram.com/stories/user/12345/")
        )
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("instagram", "https://www.instagram.com/reel/C_abc123/")
        )
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("instagram", "https://www.instagram.com/")
        )
    }

    @Test
    fun testTikTokAndThreads_alwaysReturnPortrait() {
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("tiktok", "https://www.tiktok.com/@user/video/123456")
        )
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("threads", "https://www.threads.net/@user/post/123456")
        )
    }

    @Test
    fun testYouTube_shortsReturnsPortrait_standardReturnsLandscape() {
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("youtube", "https://www.youtube.com/shorts/abc123xyz")
        )
        assertEquals(
            ScreenOrientationTarget.SENSOR_LANDSCAPE,
            FullscreenOrientationHelper.determineTargetOrientation("youtube", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
    }

    @Test
    fun testTwitch_returnsLandscape() {
        assertEquals(
            ScreenOrientationTarget.SENSOR_LANDSCAPE,
            FullscreenOrientationHelper.determineTargetOrientation("twitch", "https://www.twitch.tv/streamer")
        )
    }

    @Test
    fun testGenericPlatforms_reelsOrStoriesUrlReturnsPortrait() {
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("facebook", "https://www.facebook.com/reel/123456789")
        )
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("facebook", "https://www.facebook.com/stories/123456789")
        )
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("reddit", "https://www.reddit.com/r/videos/comments/123/reel/")
        )
    }

    @Test
    fun testGenericPlatforms_standardUrlsReturnDynamicEvaluation() {
        assertEquals(
            ScreenOrientationTarget.DYNAMIC_EVALUATION,
            FullscreenOrientationHelper.determineTargetOrientation("reddit", "https://www.reddit.com/r/videos/comments/123/")
        )
        assertEquals(
            ScreenOrientationTarget.DYNAMIC_EVALUATION,
            FullscreenOrientationHelper.determineTargetOrientation("x", "https://x.com/user/status/123456")
        )
        assertEquals(
            ScreenOrientationTarget.DYNAMIC_EVALUATION,
            FullscreenOrientationHelper.determineTargetOrientation("custom_app", "https://example.com/player")
        )
    }

    @Test
    fun testCaseInsensitivityAndNullUrl() {
        assertEquals(
            ScreenOrientationTarget.PORTRAIT,
            FullscreenOrientationHelper.determineTargetOrientation("INSTAGRAM", null)
        )
        assertEquals(
            ScreenOrientationTarget.SENSOR_LANDSCAPE,
            FullscreenOrientationHelper.determineTargetOrientation("YOUTUBE", null)
        )
        assertEquals(
            ScreenOrientationTarget.DYNAMIC_EVALUATION,
            FullscreenOrientationHelper.determineTargetOrientation("unknown", null)
        )
    }
}
