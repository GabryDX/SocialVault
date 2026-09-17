package com.heronikostudios.socialvault

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

class PlatformManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "social_vault_platforms"
        private const val KEY_CUSTOM_PLATFORMS = "custom_platforms"

        val DEFAULT_PLATFORMS = listOf(
            Platform(
                id = "tiktok",
                name = "TikTok",
                url = "https://www.tiktok.com",
                iconResId = R.drawable.ic_platform_tiktok,
                accentColor = "#00F2FE",
                allowedDomains = listOf("tiktok.com", "tiktokcdn.com")
            ),
            Platform(
                id = "instagram",
                name = "Instagram",
                url = "https://www.instagram.com",
                iconResId = R.drawable.ic_platform_instagram,
                accentColor = "#E1306C",
                allowedDomains = listOf("instagram.com", "cdninstagram.com")
            ),
            Platform(
                id = "x",
                name = "X",
                url = "https://x.com",
                iconResId = R.drawable.ic_platform_x,
                accentColor = "#F8FAFC",
                allowedDomains = listOf("x.com", "twitter.com", "twimg.com")
            ),
            Platform(
                id = "threads",
                name = "Threads",
                url = "https://www.threads.net",
                iconResId = R.drawable.ic_platform_threads,
                accentColor = "#FFFFFF",
                allowedDomains = listOf("threads.net")
            ),
            Platform(
                id = "facebook",
                name = "Facebook",
                url = "https://m.facebook.com",
                iconResId = R.drawable.ic_platform_facebook,
                accentColor = "#1877F2",
                allowedDomains = listOf("facebook.com", "fbcdn.net")
            ),
            Platform(
                id = "reddit",
                name = "Reddit",
                url = "https://www.reddit.com",
                iconResId = R.drawable.ic_platform_reddit,
                accentColor = "#FF4500",
                allowedDomains = listOf("reddit.com", "redd.it", "redditmedia.com")
            )
        )
    }

    fun getAllPlatforms(): List<Platform> {
        val list = mutableListOf<Platform>()
        list.addAll(DEFAULT_PLATFORMS)
        list.addAll(loadCustomPlatforms())
        return list
    }

    fun getPlatformById(id: String): Platform? {
        return getAllPlatforms().find { it.id == id }
    }

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
            iconResId = R.drawable.ic_globe,
            accentColor = "#38BDF8",
            allowedDomains = listOf(host),
            isCustom = true
        )

        val currentCustom = loadCustomPlatforms().toMutableList()
        currentCustom.add(platform)
        saveCustomPlatforms(currentCustom)
        return platform
    }

    fun removeCustomPlatform(id: String): Boolean {
        val currentCustom = loadCustomPlatforms().toMutableList()
        val removed = currentCustom.removeAll { it.id == id }
        if (removed) {
            saveCustomPlatforms(currentCustom)
        }
        return removed
    }

    private fun loadCustomPlatforms(): List<Platform> {
        val jsonString = prefs.getString(KEY_CUSTOM_PLATFORMS, null) ?: return emptyList()
        val platforms = mutableListOf<Platform>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val url = obj.getString("url")
                val host = try {
                    URI(url).host?.lowercase().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                platforms.add(
                    Platform(
                        id = id,
                        name = name,
                        url = url,
                        iconResId = R.drawable.ic_globe,
                        accentColor = "#38BDF8",
                        allowedDomains = if (host.isNotEmpty()) listOf(host) else emptyList(),
                        isCustom = true
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }
        return platforms
    }

    private fun saveCustomPlatforms(customPlatforms: List<Platform>) {
        val array = JSONArray()
        for (p in customPlatforms) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("url", p.url)
            }
            array.put(obj)
        }
        prefs.edit { putString(KEY_CUSTOM_PLATFORMS, array.toString()) }
    }
}
