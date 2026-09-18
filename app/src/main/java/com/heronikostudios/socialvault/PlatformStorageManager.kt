package com.heronikostudios.socialvault

import android.webkit.CookieManager
import android.webkit.WebStorage
import java.net.URI

object PlatformStorageManager {

    /**
     * Resolves all possible domain variations (root, www, m, mobile, etc.) for a platform
     * based on its allowedDomains and primary entry URL.
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

        return domains
    }

    /**
     * Clears temporary disk and memory cache for the specified platform without
     * removing session cookies or authentication tokens.
     */
    fun clearCacheForPlatform(platform: Platform, openTabs: List<Tab>) {
        val platformTabs = openTabs.filter { it.platform.id == platform.id }
        for (tab in platformTabs) {
            tab.webView.clearCache(true)
            tab.webView.evaluateJavascript("""
                try {
                    if (window.caches && window.caches.keys) {
                        window.caches.keys().then(function(keys) {
                            keys.forEach(function(k) { window.caches.delete(k); });
                        });
                    }
                } catch(e) {}
            """.trimIndent(), null)
        }
    }

    /**
     * Completely wipes all data for a single social media platform:
     * - Clears cookies for all platform domain variations (expiring them immediately)
     * - Deletes WebStorage (LocalStorage, SessionStorage, WebSQL) origins matching the platform
     * - Deletes IndexedDB and CacheStorage databases via JavaScript
     * - Purges WebView memory/disk cache, form data, and history for open tabs
     * - Resets open tabs to the clean platform entry URL
     */
    fun wipeDataForPlatform(
        platform: Platform,
        openTabs: List<Tab>,
        onComplete: (() -> Unit)? = null
    ) {
        // 1. Expire cookies for all domain variants
        val cookieManager = CookieManager.getInstance()
        val domains = getCandidateDomains(platform)

        for (domain in domains) {
            val urls = listOf("https://$domain", "http://$domain")
            for (url in urls) {
                val cookieStr = try {
                    cookieManager.getCookie(url)
                } catch (_: Exception) {
                    null
                }
                if (!cookieStr.isNullOrBlank()) {
                    val pairs = cookieStr.split(';')
                    for (pair in pairs) {
                        val name = pair.substringBefore('=').trim()
                        if (name.isNotEmpty()) {
                            cookieManager.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                            cookieManager.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=$domain")
                            cookieManager.setCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=.$domain")
                        }
                    }
                }
            }
        }
        cookieManager.flush()

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

        // 3. Clear open WebViews (cache, form data, history, DOM storage) and reload to base URL
        val platformTabs = openTabs.filter { it.platform.id == platform.id }
        for (tab in platformTabs) {
            tab.webView.clearCache(true)
            tab.webView.clearFormData()
            tab.webView.clearHistory()
            tab.webView.evaluateJavascript("""
                (function() {
                    try { localStorage.clear(); } catch(e) {}
                    try { sessionStorage.clear(); } catch(e) {}
                    try {
                        if (window.indexedDB && window.indexedDB.databases) {
                            window.indexedDB.databases().then(function(dbs) {
                                dbs.forEach(function(db) {
                                    if (db.name) window.indexedDB.deleteDatabase(db.name);
                                });
                            });
                        }
                    } catch(e) {}
                    try {
                        if (window.caches && window.caches.keys) {
                            window.caches.keys().then(function(keys) {
                                keys.forEach(function(k) { window.caches.delete(k); });
                            });
                        }
                    } catch(e) {}
                })();
            """.trimIndent(), null)
            tab.webView.loadUrl(platform.url)
        }

        onComplete?.invoke()
    }
}
