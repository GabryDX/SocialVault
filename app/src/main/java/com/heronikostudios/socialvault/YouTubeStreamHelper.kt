package com.heronikostudios.socialvault

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

data class YouTubeStreamItem(
    val title: String,
    val resolution: String,
    val formatName: String,
    val url: String,
    val isAudio: Boolean = false
) {
    val fileExtension: String
        get() = if (isAudio) "m4a" else "mp4"

    val mimeType: String
        get() = if (isAudio) "audio/mp4" else "video/mp4"

    val safeFileName: String
        get() {
            val clean = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
            val baseName = if (clean.isNotBlank()) clean else "youtube_download_${System.currentTimeMillis()}"
            return "$baseName.$fileExtension"
        }
}

object YouTubeStreamHelper {

    @Volatile
    private var isInitialized = false

    fun init() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    NewPipe.init(OkHttpDownloader())
                    isInitialized = true
                }
            }
        }
    }

    /**
     * Checks whether a URL is a direct link to a YouTube video, Short, live stream, or embed.
     */
    fun isYouTubeVideoUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("watch?v=") ||
                lower.contains("/watch/") ||
                lower.contains("/shorts/") ||
                lower.contains("youtu.be/") ||
                lower.contains("/live/") ||
                lower.contains("/embed/") ||
                lower.contains("/v/")
    }

    /**
     * Extracts progressive playable video and audio streams for a given YouTube URL.
     * Must be called from a background thread.
     */
    @Suppress("DEPRECATION")
    fun extractStreams(videoUrl: String): Result<List<YouTubeStreamItem>> {
        return try {
            init()
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val rawTitle = streamInfo.name?.trim()?.replace(Regex("[\\r\\n]+"), " ")
            val videoTitle = if (!rawTitle.isNullOrBlank()) rawTitle else "YouTube Video"
            val items = mutableListOf<YouTubeStreamItem>()

            // 1. Progressive video streams (contain both audio and video)
            val progressiveStreams = streamInfo.videoStreams?.filter { !it.isVideoOnly && !it.getUrl().isNullOrBlank() }
                ?.sortedByDescending { it.getResolution()?.filter { ch -> ch.isDigit() }?.toIntOrNull() ?: 0 }
                ?: emptyList()

            for (stream in progressiveStreams) {
                val streamUrl = stream.getUrl() ?: continue
                val res = stream.getResolution() ?: "Video"
                val fmt = stream.format?.getName() ?: "MP4"
                items.add(
                    YouTubeStreamItem(
                        title = videoTitle,
                        resolution = res,
                        formatName = "$fmt (Video + Audio)",
                        url = streamUrl,
                        isAudio = false
                    )
                )
            }

            // 2. Best audio stream (M4A / AAC preferred)
            val audioStreams = streamInfo.audioStreams?.filter { !it.getUrl().isNullOrBlank() } ?: emptyList()
            val m4aStreams = audioStreams.filter { it.format?.getName()?.contains("m4a", ignoreCase = true) == true }
            val bestAudio = m4aStreams.maxByOrNull { it.averageBitrate }
                ?: audioStreams.maxByOrNull { it.averageBitrate }

            if (bestAudio != null && !bestAudio.getUrl().isNullOrBlank()) {
                val bitrateStr = if (bestAudio.averageBitrate > 0) "${bestAudio.averageBitrate} kbps" else "High Quality"
                items.add(
                    YouTubeStreamItem(
                        title = videoTitle,
                        resolution = bitrateStr,
                        formatName = "Audio (M4A)",
                        url = bestAudio.getUrl()!!,
                        isAudio = true
                    )
                )
            }

            if (items.isNotEmpty()) {
                Result.success(items)
            } else {
                Result.failure(Exception("No progressive video streams available"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
