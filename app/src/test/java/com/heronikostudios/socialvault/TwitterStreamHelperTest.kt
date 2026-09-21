package com.heronikostudios.socialvault

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitterStreamHelperTest {

    @Test
    fun testExtractTweetId() {
        assertEquals("1514564966564651008", TwitterStreamHelper.extractTweetId("https://x.com/NASA/status/1514564966564651008"))
        assertEquals("1514564966564651008", TwitterStreamHelper.extractTweetId("https://twitter.com/elonmusk/status/1514564966564651008?s=20"))
        assertEquals("12345", TwitterStreamHelper.extractTweetId("https://mobile.twitter.com/user/status/12345/video/1"))
        assertEquals("987654321", TwitterStreamHelper.extractTweetId("https://x.com/i/status/987654321"))
        assertEquals("998877", TwitterStreamHelper.extractTweetId("https://x.com/user/statuses/998877"))
        assertEquals("1514564966564651008", TwitterStreamHelper.extractTweetId("1514564966564651008"))

        assertNull(TwitterStreamHelper.extractTweetId("https://x.com/home"))
        assertNull(TwitterStreamHelper.extractTweetId("https://twitter.com/explore"))
        assertNull(TwitterStreamHelper.extractTweetId("https://x.com/notifications"))
        assertNull(TwitterStreamHelper.extractTweetId("https://instagram.com/p/abcdef"))
        assertNull(TwitterStreamHelper.extractTweetId(""))
        assertNull(TwitterStreamHelper.extractTweetId(null))
    }

    @Test
    fun testGenerateSyndicationToken() {
        // Jack Dorsey tweet 20 verification
        val token20 = TwitterStreamHelper.generateSyndicationToken("20")
        assertEquals("6dq1a2xw", token20)

        // Real tweet ID
        val tokenReal = TwitterStreamHelper.generateSyndicationToken("1514564966564651008")
        assertTrue(tokenReal.isNotEmpty())
        assertFalse(tokenReal.contains("0"))
        assertFalse(tokenReal.contains("."))

        // Invalid input returns empty
        assertEquals("", TwitterStreamHelper.generateSyndicationToken("invalid"))
    }

    @Test
    fun testIsTwitterUrl() {
        assertTrue(TwitterStreamHelper.isTwitterUrl("https://x.com/elonmusk/status/123"))
        assertTrue(TwitterStreamHelper.isTwitterUrl("https://twitter.com/home"))
        assertTrue(TwitterStreamHelper.isTwitterUrl("https://mobile.twitter.com/i/flow"))
        assertTrue(TwitterStreamHelper.isTwitterUrl("https://t.co/abcdef"))
        assertTrue(TwitterStreamHelper.isTwitterUrl("https://video.twimg.com/amplify_video/123.mp4"))

        assertFalse(TwitterStreamHelper.isTwitterUrl("https://youtube.com/watch?v=123"))
        assertFalse(TwitterStreamHelper.isTwitterUrl("https://instagram.com/reels/123"))
        assertFalse(TwitterStreamHelper.isTwitterUrl("https://reddit.com/r/android"))
        assertFalse(TwitterStreamHelper.isTwitterUrl(""))
        assertFalse(TwitterStreamHelper.isTwitterUrl(null))
    }

    @Test
    fun testTwitterStreamItemPropertiesAndSanitization() {
        val item = TwitterStreamItem(
            title = "@NASA - Liftoff! Mars Rover launch <success> *2026* / [HD]",
            resolution = "720p (1280x720)",
            bitrate = 2176000,
            url = "https://video.twimg.com/amplify_video/123/vid/1280x720/test.mp4",
            isGif = false
        )

        assertEquals("mp4", item.fileExtension)
        assertEquals("video/mp4", item.mimeType)
        assertEquals("720p (1280x720) • MP4", item.formatName)
        assertEquals("@NASA - Liftoff! Mars Rover launch _success_ _2026_ _ [HD].mp4", item.safeFileName)

        val gifItem = TwitterStreamItem(
            title = "@Cat - Funny Cat Loop",
            resolution = "GIF Loop",
            bitrate = 0,
            url = "https://video.twimg.com/tweet_video/test.mp4",
            isGif = true
        )
        assertEquals("Animated GIF (MP4 Loop)", gifItem.formatName)
        assertEquals("@Cat - Funny Cat Loop.mp4", gifItem.safeFileName)

        val blankTitleItem = TwitterStreamItem(
            title = "   ///   ",
            resolution = "480p",
            bitrate = 832000,
            url = "https://video.twimg.com/test.mp4"
        )
        assertTrue(blankTitleItem.safeFileName.endsWith(".mp4"))
        assertFalse(blankTitleItem.safeFileName.startsWith("/"))

        val longTitle = "A".repeat(150)
        val longItem = TwitterStreamItem(
            title = longTitle,
            resolution = "1080p",
            bitrate = 5000000,
            url = "https://video.twimg.com/test.mp4"
        )
        assertTrue(longItem.safeFileName.length <= 105)
    }

    @Test
    fun testExtractStreamsWithMockJsonResponse() {
        val mockJson = """
            {
                "id_str": "123456789",
                "text": "Check out this cool launch video! #space",
                "user": {
                    "name": "Space Agency",
                    "screen_name": "SpaceAgency"
                },
                "mediaDetails": [
                    {
                        "type": "video",
                        "video_info": {
                            "aspect_ratio": [16, 9],
                            "variants": [
                                {
                                    "bitrate": 2176000,
                                    "content_type": "video/mp4",
                                    "url": "https://video.twimg.com/amplify_video/123/vid/1280x720/high.mp4"
                                },
                                {
                                    "bitrate": 832000,
                                    "content_type": "video/mp4",
                                    "url": "https://video.twimg.com/amplify_video/123/vid/640x360/med.mp4"
                                },
                                {
                                    "content_type": "application/x-mpegURL",
                                    "url": "https://video.twimg.com/amplify_video/123/pl/index.m3u8"
                                }
                            ]
                        }
                    }
                ]
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockJson.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val result = TwitterStreamHelper.extractStreams("https://x.com/SpaceAgency/status/123456789", mockClient)
        assertTrue(result.isSuccess)
        val items = result.getOrNull()
        assertNotNull(items)
        assertEquals(2, items!!.size)

        // Verify sorted descending by bitrate
        assertEquals(2176000, items[0].bitrate)
        assertEquals("720p (1280x720)", items[0].resolution)
        assertEquals("https://video.twimg.com/amplify_video/123/vid/1280x720/high.mp4", items[0].url)
        assertEquals("@SpaceAgency - Check out this cool launch video! #space", items[0].title)

        assertEquals(832000, items[1].bitrate)
        assertEquals("360p (640x360)", items[1].resolution)
        assertEquals("https://video.twimg.com/amplify_video/123/vid/640x360/med.mp4", items[1].url)
    }

    @Test
    fun testExtractStreamsNoVideoInPost() {
        val mockJson = """
            {
                "id_str": "123456789",
                "text": "Just text, no video here!",
                "user": {
                    "screen_name": "JustText"
                },
                "mediaDetails": []
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockJson.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val result = TwitterStreamHelper.extractStreams("123456789", mockClient)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun testExtractStreamsHttpError() {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val result = TwitterStreamHelper.extractStreams("123456789", mockClient)
        assertTrue(result.isFailure)
    }
}
