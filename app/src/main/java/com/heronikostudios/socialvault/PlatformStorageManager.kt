package com.heronikostudios.socialvault

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI

object PlatformStorageManager {

    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9_]")

    /**
     * Derives a valid Chromium profile name uniquely identifying the platform container.
     */
    fun getProfileName(platform: Platform): String {
        val cleanId = platform.id.lowercase().replace(NON_ALPHANUMERIC_REGEX, "_")
        return "sv_profile_$cleanId"
    }

    /**
     * Returns the isolated CookieManager for this platform if Multi-Profile is supported,
     * or the default system CookieManager as fallback.
     */
    fun getCookieManagerForPlatform(platform: Platform): CookieManager {
        return if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profileName = getProfileName(platform)
            ProfileStore.getInstance().getOrCreateProfile(profileName).cookieManager
        } else {
            CookieManager.getInstance()
        }
    }

    /**
     * Resolves all possible domain variations (root, www, m, mobile, etc.) for a platform
     * based on its allowedDomains and primary entry URL, including authentication origins.
     */
    fun getCandidateDomains(platform: Platform): Set<String> {
        val domains = mutableSetOf<String>()

        for (d in platform.allowedDomains) {
            val clean = d.trim().lowercase()
                .removePrefix("https://").removePrefix("http://")
                .removePrefix("www.").removePrefix("m.").removePrefix(".")
                .substringBefore('/')
            if (clean.isNotEmpty()) {
                domains.add(clean)
                domains.add("www.$clean")
                domains.add("m.$clean")
            }
        }

        val mainHost = try {
            val uri = URI(platform.url)
            uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.removePrefix(".")
        } catch (_: Exception) {
            null
        }

        if (!mainHost.isNullOrBlank()) {
            domains.add(mainHost)
            domains.add("www.$mainHost")
            domains.add("m.$mainHost")
        }

        if (platform.id == "youtube") {
            domains.add("google.com")
            domains.add("www.google.com")
            domains.add("accounts.google.com")
            domains.add("myaccount.google.com")
            domains.add("consent.google.com")
            domains.add("consent.youtube.com")
        }

        return domains
    }

    /**
     * Clears temporary disk and memory cache for the specified platform without
     * removing session cookies or authentication tokens.
     */
    fun clearCacheForPlatform(context: Context, platform: Platform, openTabs: List<Tab>) {
        val platformTabs = openTabs.filter { it.platform.id == platform.id }
        if (platformTabs.isNotEmpty()) {
            for (tab in platformTabs) {
                tab.webView.clearCache(true)
                tab.webView.evaluateJavascript("""
                    (function() {
                        try {
                            if (window.caches && window.caches.keys) {
                                window.caches.keys().then(function(keys) {
                                    keys.forEach(function(k) { window.caches.delete(k); });
                                });
                            }
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
            }
        } else {
            try {
                val tempWebView = WebView(context)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    val profileName = getProfileName(platform)
                    val profile = ProfileStore.getInstance().getOrCreateProfile(profileName)
                    WebViewCompat.setProfile(tempWebView, profile.name)
                }
                tempWebView.clearCache(true)
                tempWebView.destroy()
            } catch (_: Exception) {}
        }
    }

    /**
     * Completely wipes all data for a single social media platform:
     * - Deletes the entire isolated Multi-Profile container (cookies, LocalStorage LevelDB,
     *   IndexedDB, Service Workers, CacheStorage, HTTP cache).
     * - Also scrubs legacy/default CookieManager and WebStorage origins to clear any
     *   pre-existing sessions or fallback data.
     */
    fun wipeDataForPlatform(
        context: Context,
        platform: Platform,
        onComplete: (() -> Unit)? = null
    ) {
        // 1. Multi-Profile: physically purge profile container from disk
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            val profileStore = ProfileStore.getInstance()
            val profileName = getProfileName(platform)
            try {
                if (profileStore.allProfileNames.contains(profileName)) {
                    profileStore.deleteProfile(profileName)
                }
            } catch (_: Exception) {
                try {
                    val profile = profileStore.getOrCreateProfile(profileName)
                    profile.cookieManager.removeAllCookies(null)
                    profile.cookieManager.flush()
                    profile.webStorage.deleteAllData()
                } catch (_: Exception) {}
            }
        }

        // 2. Legacy / Default Profile: expire domain cookies across all protocol and flag variations
        val defaultCookieManager = CookieManager.getInstance()
        val domains = getCandidateDomains(platform)

        for (domain in domains) {
            val urls = listOf("https://$domain", "http://$domain")
            for (url in urls) {
                val cookieStr = try {
                    defaultCookieManager.getCookie(url)
                } catch (_: Exception) {
                    null
                }
                if (!cookieStr.isNullOrBlank()) {
                    val pairs = cookieStr.split(';')
                    for (pair in pairs) {
                        val name = pair.substringBefore('=').trim()
                        if (name.isNotEmpty()) {
                            defaultCookieManager.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                            defaultCookieManager.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=$domain")
                            defaultCookieManager.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.$domain")
                            defaultCookieManager.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure; SameSite=None")
                            defaultCookieManager.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure; SameSite=Lax")
                        }
                    }
                }
            }
        }
        defaultCookieManager.flush()

        // 3. Clear WebStorage origins matching the platform in default storage
        val webStorage = WebStorage.getInstance()
        webStorage.getOrigins { origins ->
            origins?.keys?.forEach { originKey ->
                val originStr = originKey as? String
                if (originStr != null) {
                    if (platform.isDomainAllowed(originStr) || platform.isDomainAllowed("https://$originStr")) {
                        webStorage.deleteOrigin(originStr)
                    }
                }
            }
        }

        onComplete?.invoke()
    }
}
