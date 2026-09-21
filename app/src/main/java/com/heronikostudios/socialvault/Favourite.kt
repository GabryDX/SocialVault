package com.heronikostudios.socialvault

import org.json.JSONObject
import java.util.UUID

/**
 * Represents a privately saved favorite link / bookmark.
 * Stored entirely within SocialVault local storage, keeping bookmarks
 * invisible to tracking algorithms on social media platforms.
 */
data class Favourite(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val platformId: String,
    val platformName: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("url", url)
        put("platformId", platformId)
        put("platformName", platformName)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Favourite {
            return Favourite(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", ""),
                url = json.optString("url", ""),
                platformId = json.optString("platformId", ""),
                platformName = json.optString("platformName", ""),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
