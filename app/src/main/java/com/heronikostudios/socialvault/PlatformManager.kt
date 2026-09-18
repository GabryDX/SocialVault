package com.heronikostudios.socialvault

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Collections
import java.util.UUID

class PlatformManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val currentPlatforms = mutableListOf<Platform>()

    init {
        loadPlatforms()
    }

    companion object {
        private const val PREFS_NAME = "social_vault_platforms"
        private const val KEY_PLATFORMS = "saved_platforms_list_v4"
        private const val KEY_STRIP_METADATA = "pref_strip_metadata"
        private const val KEY_POLISH_URLS = "pref_polish_urls"
        private const val KEY_FULL_SCREEN = "pref_full_screen_mode"

        val DEFAULT_PLATFORMS = listOf(
            Platform(
                id = "facebook",
                name = "Facebook",
                url = "https://m.facebook.com",
                iconType = "facebook",
                accentColor = "#1877F2",
                allowedDomains = listOf("facebook.com", "fbcdn.net", "m.facebook.com", "fb.watch", "fb.me")
            ),
            Platform(
                id = "youtube",
                name = "YouTube",
                url = "https://m.youtube.com",
                iconType = "youtube",
                accentColor = "#FF0000",
                allowedDomains = listOf("youtube.com", "googlevideo.com", "ytimg.com", "youtu.be", "m.youtube.com")
            ),
            Platform(
                id = "instagram",
                name = "Instagram",
                url = "https://www.instagram.com",
                iconType = "instagram",
                accentColor = "#E1306C",
                allowedDomains = listOf("instagram.com", "cdninstagram.com", "instagr.am")
            ),
            Platform(
                id = "tiktok",
                name = "TikTok",
                url = "https://www.tiktok.com",
                iconType = "tiktok",
                accentColor = "#00F2FE",
                allowedDomains = listOf("tiktok.com", "tiktokcdn.com", "vm.tiktok.com", "vt.tiktok.com")
            ),
            Platform(
                id = "reddit",
                name = "Reddit",
                url = "https://www.reddit.com",
                iconType = "reddit",
                accentColor = "#FF4500",
                allowedDomains = listOf("reddit.com", "redd.it", "redditmedia.com")
            ),
            Platform(
                id = "x",
                name = "X",
                url = "https://x.com",
                iconType = "x",
                accentColor = "#F8FAFC",
                allowedDomains = listOf("x.com", "twitter.com", "twimg.com", "t.co")
            ),
            Platform(
                id = "pinterest",
                name = "Pinterest",
                url = "https://www.pinterest.com",
                iconType = "pinterest",
                accentColor = "#E60023",
                allowedDomains = listOf("pinterest.com", "pinimg.com", "pin.it")
            ),
            Platform(
                id = "linkedin",
                name = "LinkedIn",
                url = "https://www.linkedin.com",
                iconType = "linkedin",
                accentColor = "#0A66C2",
                allowedDomains = listOf("linkedin.com", "licdn.com", "lnkd.in")
            ),
            Platform(
                id = "threads",
                name = "Threads",
                url = "https://www.threads.net",
                iconType = "threads",
                accentColor = "#FFFFFF",
                allowedDomains = listOf("threads.net")
            ),
            Platform(
                id = "twitch",
                name = "Twitch",
                url = "https://m.twitch.tv",
                iconType = "twitch",
                accentColor = "#9146FF",
                allowedDomains = listOf("twitch.tv", "ttvnw.net", "jtvnw.net")
            ),
            Platform(
                id = "bluesky",
                name = "Bluesky",
                url = "https://bsky.app",
                iconType = "bluesky",
                accentColor = "#0085FF",
                allowedDomains = listOf("bsky.app", "bsky.social")
            ),
            Platform(
                id = "mastodon",
                name = "Mastodon",
                url = "https://mastodon.social",
                iconType = "mastodon",
                accentColor = "#6364FF",
                allowedDomains = listOf("mastodon.social")
            )
        )

        fun normalizeUrl(urlString: String): String {
            val trimmed = urlString.trim()
            return if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                "https://$trimmed"
            } else {
                trimmed
            }
        }

        fun findMatchingPlatform(platforms: List<Platform>, targetUrl: String): Platform? {
            val cleanUrl = normalizeUrl(targetUrl)
            return platforms.find { platform -> platform.isDomainAllowed(cleanUrl) }
        }
    }

    fun getAllPlatforms(): List<Platform> = currentPlatforms.toList()

    fun getPlatformById(id: String): Platform? = currentPlatforms.find { it.id == id }

    fun addCustomPlatform(name: String, url: String): Platform? {
        val trimmedUrl = url.trim()
        val host = try {
            URI(trimmedUrl).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return null

        val platform = Platform(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            name = name.trim(),
            url = trimmedUrl,
            iconType = "globe",
            accentColor = "#38BDF8",
            allowedDomains = listOf(host),
            isCustom = true
        )

        currentPlatforms.add(platform)
        savePlatforms()
        return platform
    }

    fun movePlatform(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || fromPosition >= currentPlatforms.size ||
            toPosition < 0 || toPosition >= currentPlatforms.size
        ) {
            return
        }
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(currentPlatforms, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(currentPlatforms, i, i - 1)
            }
        }
        savePlatforms()
    }

    fun deletePlatform(id: String): Boolean {
        val removed = currentPlatforms.removeAll { it.id == id }
        if (removed) {
            savePlatforms()
        }
        return removed
    }

    fun resetToDefaults(): List<Platform> {
        currentPlatforms.clear()
        currentPlatforms.addAll(DEFAULT_PLATFORMS)
        savePlatforms()
        return getAllPlatforms()
    }

    fun isStripMetadataEnabled(): Boolean {
        return prefs.getBoolean(KEY_STRIP_METADATA, true)
    }

    fun setStripMetadataEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_STRIP_METADATA, enabled) }
    }

    fun isPolishUrlsEnabled(): Boolean {
        return prefs.getBoolean(KEY_POLISH_URLS, true)
    }

    fun setPolishUrlsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_POLISH_URLS, enabled) }
    }

    fun isFullScreenEnabled(): Boolean {
        return prefs.getBoolean(KEY_FULL_SCREEN, false)
    }

    fun setFullScreenEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FULL_SCREEN, enabled) }
    }

    fun findMatchingPlatform(targetUrl: String): Platform? {
        return findMatchingPlatform(getAllPlatforms(), targetUrl)
    }

    fun normalizeUrl(urlString: String): String = Companion.normalizeUrl(urlString)

    private fun loadPlatforms() {
        currentPlatforms.clear()
        val jsonString = prefs.getString(KEY_PLATFORMS, null)
        if (jsonString != null) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val url = obj.getString("url")
                    val iconType = obj.optString("iconType", "globe")
                    val accentColor = obj.optString("accentColor", "#0284C7")
                    val isCustom = obj.optBoolean("isCustom", false)

                    val domainsArray = obj.optJSONArray("allowedDomains")
                    val domains = mutableListOf<String>()
                    if (domainsArray != null) {
                        for (d in 0 until domainsArray.length()) {
                            domains.add(domainsArray.getString(d))
                        }
                    } else {
                        val host = try {
                            URI(url).host?.lowercase().orEmpty()
                        } catch (_: Exception) {
                            ""
                        }
                        if (host.isNotEmpty()) domains.add(host)
                    }

                    currentPlatforms.add(
                        Platform(
                            id = id,
                            name = name,
                            url = url,
                            iconType = iconType,
                            accentColor = accentColor,
                            allowedDomains = domains,
                            isCustom = isCustom
                        )
                    )
                }
            } catch (_: Exception) {
                currentPlatforms.clear()
                currentPlatforms.addAll(DEFAULT_PLATFORMS)
            }
        }

        if (currentPlatforms.isEmpty()) {
            currentPlatforms.addAll(DEFAULT_PLATFORMS)
            savePlatforms()
        }
    }

    private fun savePlatforms() {
        val array = JSONArray()
        for (p in currentPlatforms) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("url", p.url)
                put("iconType", p.iconType)
                put("accentColor", p.accentColor)
                put("isCustom", p.isCustom)

                val domainsArray = JSONArray()
                for (d in p.allowedDomains) {
                    domainsArray.put(d)
                }
                put("allowedDomains", domainsArray)
            }
            array.put(obj)
        }
        prefs.edit { putString(KEY_PLATFORMS, array.toString()) }
    }
}
