package com.heronikostudios.socialvault

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray

class FavouritesManager(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private val favourites = mutableListOf<Favourite>()
    private val listeners = mutableListOf<() -> Unit>()

    init {
        loadFavourites()
    }

    companion object {
        private const val PREFS_NAME = "social_vault_favourites"
        private const val KEY_FAVOURITES = "saved_favourites_v1"

        fun normalizeUrl(url: String): String {
            val polished = UrlPolisher.polishUrl(url).polishedUrl.trim()
            return polished.trimEnd('/')
        }
    }

    private fun loadFavourites() {
        favourites.clear()
        val jsonStr = prefs.getString(KEY_FAVOURITES, null) ?: return
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                favourites.add(Favourite.fromJson(obj))
            }
        } catch (_: Exception) {}
    }

    private fun saveFavourites() {
        val array = JSONArray()
        for (f in favourites) {
            array.put(f.toJson())
        }
        prefs.edit { putString(KEY_FAVOURITES, array.toString()) }
        notifyChanged()
    }

    fun getAllFavourites(): List<Favourite> {
        return favourites.toList()
    }

    fun getFavouritesCount(): Int = favourites.size

    fun isFavourite(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val normalized = normalizeUrl(url)
        return favourites.any { normalizeUrl(it.url) == normalized }
    }

    fun getFavouriteForUrl(url: String?): Favourite? {
        if (url.isNullOrBlank()) return null
        val normalized = normalizeUrl(url)
        return favourites.firstOrNull { normalizeUrl(it.url) == normalized }
    }

    fun addFavourite(title: String, url: String, platform: Platform): Favourite {
        val cleanUrl = UrlPolisher.polishUrl(url).polishedUrl.trim()
        val cleanTitle = if (title.isNotBlank()) title.trim() else platform.name

        val existingIndex = favourites.indexOfFirst { normalizeUrl(it.url) == normalizeUrl(cleanUrl) }
        val item = if (existingIndex >= 0) {
            val updated = favourites[existingIndex].copy(
                title = cleanTitle,
                url = cleanUrl,
                platformId = platform.id,
                platformName = platform.name
            )
            favourites[existingIndex] = updated
            updated
        } else {
            val fav = Favourite(
                title = cleanTitle,
                url = cleanUrl,
                platformId = platform.id,
                platformName = platform.name
            )
            favourites.add(0, fav)
            fav
        }
        saveFavourites()
        return item
    }

    fun removeFavourite(id: String): Boolean {
        val removed = favourites.removeAll { it.id == id }
        if (removed) {
            saveFavourites()
        }
        return removed
    }

    fun removeFavouriteByUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val normalized = normalizeUrl(url)
        val removed = favourites.removeAll { normalizeUrl(it.url) == normalized }
        if (removed) {
            saveFavourites()
        }
        return removed
    }

    fun updateTitle(id: String, newTitle: String): Boolean {
        val index = favourites.indexOfFirst { it.id == id }
        if (index >= 0) {
            favourites[index] = favourites[index].copy(title = newTitle.trim())
            saveFavourites()
            return true
        }
        return false
    }

    fun clearAll() {
        favourites.clear()
        saveFavourites()
    }

    fun addChangeListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        for (l in listeners) {
            l.invoke()
        }
    }
}
