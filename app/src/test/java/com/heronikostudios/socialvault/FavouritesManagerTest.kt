package com.heronikostudios.socialvault

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FavouritesManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var manager: FavouritesManager

    private val platformX = Platform(
        id = "x",
        name = "X",
        url = "https://x.com",
        iconType = "x",
        allowedDomains = listOf("x.com", "twitter.com")
    )

    private val platformInsta = Platform(
        id = "instagram",
        name = "Instagram",
        url = "https://www.instagram.com",
        iconType = "instagram",
        allowedDomains = listOf("instagram.com")
    )

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        manager = FavouritesManager(fakePrefs)
    }

    @Test
    fun testFavouriteSerializationAndDeserialization() {
        val original = Favourite(
            id = "fav-123",
            title = "Test Post",
            url = "https://x.com/user/status/123456",
            platformId = "x",
            platformName = "X",
            createdAt = 1700000000000L
        )

        val json = original.toJson()
        val restored = Favourite.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.title, restored.title)
        assertEquals(original.url, restored.url)
        assertEquals(original.platformId, restored.platformId)
        assertEquals(original.platformName, restored.platformName)
        assertEquals(original.createdAt, restored.createdAt)
    }

    @Test
    fun testNormalizeUrl_stripsTrackingAndTrailingSlash() {
        val urlWithTracking = "https://x.com/user/status/123456?s=20&t=abcxyz/"
        val normalized = FavouritesManager.normalizeUrl(urlWithTracking)
        assertEquals("https://x.com/user/status/123456", normalized)

        val igUrl = "https://www.instagram.com/reel/C7abc123/?igsh=MWZ4d3J2bnEx"
        val normalizedIg = FavouritesManager.normalizeUrl(igUrl)
        assertEquals("https://www.instagram.com/reel/C7abc123", normalizedIg)
    }

    @Test
    fun testAddFavourite_cleansTrackingAndSaves() {
        val dirtyUrl = "https://x.com/tech_insider/status/987654?s=46&t=Tracker123"
        val fav = manager.addFavourite(
            title = "Interesting Tweet",
            url = dirtyUrl,
            platform = platformX
        )

        // URL must be stripped of tracking parameters
        assertEquals("https://x.com/tech_insider/status/987654", fav.url)
        assertEquals("Interesting Tweet", fav.title)
        assertEquals(1, manager.getFavouritesCount())

        // isFavourite must match both dirty and clean URL variants
        assertTrue(manager.isFavourite(dirtyUrl))
        assertTrue(manager.isFavourite("https://x.com/tech_insider/status/987654"))
        assertTrue(manager.isFavourite("https://x.com/tech_insider/status/987654/"))

        val found = manager.getFavouriteForUrl(dirtyUrl)
        assertNotNull(found)
        assertEquals(fav.id, found?.id)
    }

    @Test
    fun testAddFavourite_updatesExistingInsteadOfDuplicating() {
        val url = "https://www.instagram.com/p/C-photo123"
        manager.addFavourite("Old Title", url, platformInsta)
        assertEquals(1, manager.getFavouritesCount())

        // Add again with tracking param and updated title
        manager.addFavourite("New Title", "$url?igsh=xyz987", platformInsta)
        assertEquals(1, manager.getFavouritesCount())

        val list = manager.getAllFavourites()
        assertEquals(1, list.size)
        assertEquals("New Title", list[0].title)
    }

    @Test
    fun testRemoveFavourite_byIdAndByUrl() {
        val fav1 = manager.addFavourite("Post 1", "https://x.com/p/1", platformX)
        val fav2 = manager.addFavourite("Post 2", "https://x.com/p/2", platformX)
        assertEquals(2, manager.getFavouritesCount())

        // Remove by ID
        val removed1 = manager.removeFavourite(fav1.id)
        assertTrue(removed1)
        assertEquals(1, manager.getFavouritesCount())
        assertFalse(manager.isFavourite(fav1.url))

        // Remove by URL (even with tracking query parameter)
        val removed2 = manager.removeFavouriteByUrl("${fav2.url}?s=20")
        assertTrue(removed2)
        assertEquals(0, manager.getFavouritesCount())
        assertFalse(manager.isFavourite(fav2.url))
    }

    @Test
    fun testUpdateTitle_changesStoredTitle() {
        val fav = manager.addFavourite("Original Title", "https://x.com/post/99", platformX)
        val success = manager.updateTitle(fav.id, "Renamed Title")
        assertTrue(success)

        val updated = manager.getFavouriteForUrl(fav.url)
        assertEquals("Renamed Title", updated?.title)

        val failed = manager.updateTitle("non_existent_id", "New Title")
        assertFalse(failed)
    }

    @Test
    fun testClearAll_removesEverything() {
        manager.addFavourite("1", "https://x.com/1", platformX)
        manager.addFavourite("2", "https://x.com/2", platformX)
        manager.addFavourite("3", "https://x.com/3", platformX)
        assertEquals(3, manager.getFavouritesCount())

        manager.clearAll()
        assertEquals(0, manager.getFavouritesCount())
        assertTrue(manager.getAllFavourites().isEmpty())
    }

    @Test
    fun testPersistence_survivesReloadFromPreferences() {
        manager.addFavourite("Persisted Post", "https://x.com/persisted/1?s=20", platformX)
        assertEquals(1, manager.getFavouritesCount())

        // Simulate app relaunch by creating a new manager with the same preferences
        val newManager = FavouritesManager(fakePrefs)
        assertEquals(1, newManager.getFavouritesCount())
        val item = newManager.getAllFavourites()[0]
        assertEquals("Persisted Post", item.title)
        assertEquals("https://x.com/persisted/1", item.url)
    }

    @Test
    fun testChangeListener_calledOnModifications() {
        var callCount = 0
        val listener: () -> Unit = { callCount++ }
        manager.addChangeListener(listener)

        val fav = manager.addFavourite("Title", "https://x.com/test", platformX)
        assertEquals(1, callCount)

        manager.updateTitle(fav.id, "Updated")
        assertEquals(2, callCount)

        manager.removeFavourite(fav.id)
        assertEquals(3, callCount)

        manager.removeChangeListener(listener)
        manager.addFavourite("Title 2", "https://x.com/test2", platformX)
        assertEquals(3, callCount)
    }

    private class FakeSharedPreferences : SharedPreferences, SharedPreferences.Editor {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = this
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun putString(key: String?, value: String?): SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { data[key ?: ""] = values; return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { data[key ?: ""] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { data.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { data.clear(); return this }
        override fun commit(): Boolean = true
        override fun apply() {}
    }
}
