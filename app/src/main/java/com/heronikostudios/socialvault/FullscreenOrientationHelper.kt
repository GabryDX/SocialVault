package com.heronikostudios.socialvault

enum class ScreenOrientationTarget {
    PORTRAIT,
    SENSOR_LANDSCAPE,
    DYNAMIC_EVALUATION
}

/**
 * Helper to determine appropriate screen orientation when HTML5 videos or custom web views
 * enter fullscreen mode, ensuring vertical-first platforms like Instagram, TikTok, and Threads,
 * as well as vertical video formats (Stories, Reels, Shorts), remain vertical.
 */
object FullscreenOrientationHelper {

    private val VERTICAL_PLATFORM_IDS = setOf(
        "instagram",
        "tiktok",
        "threads"
    )

    private val LANDSCAPE_PLATFORM_IDS = setOf(
        "youtube",
        "twitch"
    )

    /**
     * Determines the target screen orientation when a web page enters fullscreen.
     *
     * @param platformId ID of the platform (e.g. "instagram", "youtube", "tiktok")
     * @param currentUrl The current active URL inside the WebView
     * @return Target screen orientation rule
     */
    fun determineTargetOrientation(platformId: String, currentUrl: String?): ScreenOrientationTarget {
        val normalizedId = platformId.trim().lowercase()
        val urlLower = currentUrl?.lowercase().orEmpty()

        val isVerticalUrl = urlLower.contains("/stories/") ||
                urlLower.contains("/story/") ||
                urlLower.contains("/reels/") ||
                urlLower.contains("/reel/") ||
                urlLower.contains("/shorts/")

        if (isVerticalUrl || VERTICAL_PLATFORM_IDS.contains(normalizedId)) {
            return ScreenOrientationTarget.PORTRAIT
        }

        if (LANDSCAPE_PLATFORM_IDS.contains(normalizedId)) {
            return ScreenOrientationTarget.SENSOR_LANDSCAPE
        }

        return ScreenOrientationTarget.DYNAMIC_EVALUATION
    }
}
