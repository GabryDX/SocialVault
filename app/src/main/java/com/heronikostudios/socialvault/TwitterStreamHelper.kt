package com.heronikostudios.socialvault

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TwitterStreamItem(
    val title: String,
    val resolution: String,
    val bitrate: Int,
    val url: String,
    val isGif: Boolean = false
) {
    val formatName: String
        get() = if (isGif) "Animated GIF (MP4 Loop)" else "$resolution • MP4"

    val fileExtension: String
        get() = "mp4"

    val mimeType: String
        get() = "video/mp4"

    val safeFileName: String
        get() {
            val clean = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimStart('.')
            val truncated = clean.take(100).trim().trimEnd('.')
            val baseName = if (truncated.isNotBlank()) truncated else "x_video_${System.currentTimeMillis()}"
            return "$baseName.$fileExtension"
        }
}

object TwitterStreamHelper {

    private const val SYNDICATION_BASE_URL = "https://cdn.syndication.twimg.com/tweet-result"
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Extracts the numeric Tweet ID from a given Twitter/X URL or raw ID string.
     */
    fun extractTweetId(urlOrId: String?): String? {
        if (urlOrId.isNullOrBlank()) return null
        val trimmed = urlOrId.trim()
        if (trimmed.all { it.isDigit() }) return trimmed

        val match = Regex("""(?:status|statuses)/(\d+)""", RegexOption.IGNORE_CASE).find(trimmed)
        return match?.groupValues?.get(1)
    }

    /**
     * Generates the syndication security token used by Twitter's embed widget API.
     * Formula: ((Number(id) / 1e15) * Math.PI).toString(36).replace(/(0+|\.)/g, '')
     */
    fun generateSyndicationToken(tweetId: String): String {
        val idDouble = tweetId.toDoubleOrNull() ?: return ""
        val value = (idDouble / 1e15) * Math.PI
        val intPart = value.toLong()
        var fracPart = value - intPart

        val chars = "0123456789abcdefghijklmnopqrstuvwxyz"
        val intBase36 = intPart.toString(36)

        val fracSb = StringBuilder()
        for (i in 0 until 16) {
            fracPart *= 36
            val digit = fracPart.toInt()
            if (digit in 0 until 36) {
                fracSb.append(chars[digit])
            }
            fracPart -= digit
            if (fracPart <= 0.0) break
        }

        val raw = "$intBase36.$fracSb"
        return raw.replace("0", "").replace(".", "")
    }

    /**
     * Checks whether a URL is from Twitter or X.
     */
    fun isTwitterUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("twitter.com") ||
                lower.contains("x.com") ||
                lower.contains("twimg.com") ||
                Regex("""(^|https?://|www\.)t\.co(/|$)""").containsMatchIn(lower)
    }

    /**
     * Extracts video streams for a Twitter/X post.
     * Must be called from a background thread.
     */
    fun extractStreams(
        urlOrId: String,
        client: OkHttpClient = defaultClient
    ): Result<List<TwitterStreamItem>> {
        val tweetId = extractTweetId(urlOrId)
            ?: return Result.failure(IllegalArgumentException("Could not extract tweet ID from: $urlOrId"))

        val token = generateSyndicationToken(tweetId)
        val requestUrl = "$SYNDICATION_BASE_URL?id=$tweetId&token=$token&lang=en"

        return try {
            val request = Request.Builder()
                .url(requestUrl)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(IOException("Syndication API returned HTTP ${response.code}"))
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrBlank()) {
                return Result.failure(IOException("Empty response from syndication API"))
            }

            val parsed = JSONObject(responseBody)
            val items = parseMediaDetails(parsed, tweetId)

            if (items.isNotEmpty()) {
                Result.success(items)
            } else {
                Result.failure(Exception("No downloadable video streams found in post"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMediaDetails(root: JSONObject, tweetId: String): List<TwitterStreamItem> {
        val mediaArray = root.optJSONArray("mediaDetails")
            ?: root.optJSONObject("quoted_tweet")?.optJSONArray("mediaDetails")
            ?: root.optJSONObject("parent")?.optJSONArray("mediaDetails")
            ?: return emptyList()

        // Extract author & text for human-readable file naming
        val userObj = root.optJSONObject("user")
        val userName = userObj?.optString("name")?.trim().orEmpty()
        val userScreenName = userObj?.optString("screen_name")?.trim().orEmpty()
        val tweetText = root.optString("text").trim().replace(Regex("[\\r\\n]+"), " ")

        val authorPrefix = when {
            userScreenName.isNotBlank() -> "@$userScreenName"
            userName.isNotBlank() -> userName
            else -> "X Post"
        }
        val baseTitle = if (tweetText.isNotBlank()) {
            "$authorPrefix - $tweetText"
        } else {
            "$authorPrefix Video $tweetId"
        }

        val streamItems = mutableListOf<TwitterStreamItem>()

        for (i in 0 until mediaArray.length()) {
            val mediaObj = mediaArray.optJSONObject(i) ?: continue
            val mediaType = mediaObj.optString("type")
            val isVideo = mediaType == "video"
            val isGif = mediaType == "animated_gif"

            if (!isVideo && !isGif) continue

            val videoInfo = mediaObj.optJSONObject("video_info") ?: continue
            val variants = videoInfo.optJSONArray("variants") ?: continue

            for (v in 0 until variants.length()) {
                val variant = variants.optJSONObject(v) ?: continue
                val contentType = variant.optString("content_type")
                val streamUrl = variant.optString("url")
                val bitrate = variant.optInt("bitrate", 0)

                // Only take direct MP4 progressive streams
                if (contentType != "video/mp4" || streamUrl.isBlank()) continue

                val resolution = resolveResolutionLabel(streamUrl, bitrate, isGif)
                streamItems.add(
                    TwitterStreamItem(
                        title = baseTitle,
                        resolution = resolution,
                        bitrate = bitrate,
                        url = streamUrl,
                        isGif = isGif
                    )
                )
            }
        }

        // Deduplicate by URL and sort descending by bitrate
        return streamItems
            .distinctBy { it.url }
            .sortedByDescending { it.bitrate }
    }

    private fun resolveResolutionLabel(streamUrl: String, bitrate: Int, isGif: Boolean): String {
        if (isGif) return "GIF Loop"

        // Check if resolution is encoded in the URL path, e.g. /vid/avc1/720x1280/... or /vid/1280x720/...
        val match = Regex("""/vid/(?:[a-zA-Z0-9_-]+/)?(\d+)x(\d+)/""").find(streamUrl)
        if (match != null) {
            val w = match.groupValues[1].toIntOrNull() ?: 0
            val h = match.groupValues[2].toIntOrNull() ?: 0
            if (w > 0 && h > 0) {
                val minDim = minOf(w, h)
                return "${minDim}p (${w}x${h})"
            }
        }

        // Fallback to bitrate tiers
        return when {
            bitrate >= 2_000_000 -> "1080p (${bitrate / 1000} kbps)"
            bitrate >= 800_000 -> "720p (${bitrate / 1000} kbps)"
            bitrate >= 400_000 -> "480p (${bitrate / 1000} kbps)"
            bitrate > 0 -> "${bitrate / 1000} kbps"
            else -> "MP4 Video"
        }
    }
}
