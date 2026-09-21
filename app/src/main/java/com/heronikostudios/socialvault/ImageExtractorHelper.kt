package com.heronikostudios.socialvault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ExtractedImage(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val alt: String = "",
    var isSelected: Boolean = true
) {
    val displayResolution: String
        get() = if (width > 0 && height > 0) {
            "${width} × ${height}"
        } else {
            "HD Image"
        }
}

object ImageExtractorHelper {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * JavaScript executed in the WebView to extract high-resolution image candidates,
     * handling carousels, srcset parsing, background images, and overlay divs (e.g. Instagram).
     */
    val EXTRACTION_SCRIPT = """
        (function() {
            try {
                var results = [];
                var seenUrls = new Set();

                function addUrl(rawUrl, w, h, alt) {
                    if (!rawUrl) return;
                    var url = rawUrl.trim();
                    if (url.indexOf('http://') !== 0 && url.indexOf('https://') !== 0) return;
                    if (url.indexOf('data:') === 0 || url.indexOf('blob:') === 0) return;

                    var hashIdx = url.indexOf('#');
                    if (hashIdx !== -1) url = url.substring(0, hashIdx);

                    var lower = url.toLowerCase();
                    if (lower.indexOf('emoji') !== -1 ||
                        lower.indexOf('favicon') !== -1 ||
                        lower.indexOf('1x1') !== -1 ||
                        lower.indexOf('/icons/') !== -1) {
                        return;
                    }

                    if (seenUrls.has(url)) return;
                    seenUrls.add(url);

                    results.push({
                        url: url,
                        width: w || 0,
                        height: h || 0,
                        alt: (alt || '').substring(0, 100).trim()
                    });
                }

                function getBestFromSrcset(srcset) {
                    if (!srcset) return null;
                    var parts = srcset.split(',');
                    var bestUrl = null;
                    var maxDim = 0;
                    for (var i = 0; i < parts.length; i++) {
                        var p = parts[i].trim();
                        var tokens = p.split(/\s+/);
                        if (tokens.length >= 1) {
                            var u = tokens[0];
                            var dim = 0;
                            if (tokens.length >= 2) {
                                var spec = tokens[1];
                                if (spec.endsWith('w')) dim = parseInt(spec, 10) || 0;
                                else if (spec.endsWith('x')) dim = (parseFloat(spec) || 1) * 1000;
                            }
                            if (dim >= maxDim || !bestUrl) {
                                maxDim = dim;
                                bestUrl = u;
                            }
                        }
                    }
                    return bestUrl;
                }

                // 1. Scan all <img> elements
                var images = document.getElementsByTagName('img');
                for (var i = 0; i < images.length; i++) {
                    var img = images[i];
                    var natW = img.naturalWidth || img.width || 0;
                    var natH = img.naturalHeight || img.height || 0;

                    // Skip tiny UI icons and avatars if dimensions are known
                    if (natW > 0 && natW < 120 && natH > 0 && natH < 120) continue;

                    var bestUrl = getBestFromSrcset(img.srcset);
                    if (!bestUrl) bestUrl = img.currentSrc || img.src;

                    if (bestUrl) {
                        addUrl(bestUrl, natW, natH, img.alt);
                    }
                }

                // 2. Scan background images (for carousels or div-based image holders)
                var bgElements = document.querySelectorAll('[role="img"], div[style*="background-image"], li[style*="background-image"], span[style*="background-image"], article div[style*="background"]');
                for (var j = 0; j < bgElements.length; j++) {
                    var bgEl = bgElements[j];
                    var bg = window.getComputedStyle(bgEl).backgroundImage;
                    if (bg && bg.indexOf('url(') !== -1) {
                        var m = bg.match(/url\(['"]?(https?:\/\/[^'"]+)['"]?\)/);
                        if (m && m[1]) {
                            var w = bgEl.offsetWidth || 0;
                            var h = bgEl.offsetHeight || 0;
                            if ((w === 0 && h === 0) || (w >= 120 || h >= 120)) {
                                addUrl(m[1], w, h, bgEl.getAttribute('aria-label') || '');
                            }
                        }
                    }
                }

                return JSON.stringify(results);
            } catch(e) {
                return '[]';
            }
        })();
    """.trimIndent()

    /**
     * Parses the JSON array of extracted images and applies platform-specific quality enhancements.
     */
    fun parseExtractedImages(rawJson: String?, platformId: String? = null): List<ExtractedImage> {
        if (rawJson.isNullOrBlank() || rawJson == "null" || rawJson == "[]") return emptyList()

        var json = rawJson.trim()
        if (json.startsWith("\"") && json.endsWith("\"") && json.length >= 2) {
            try {
                json = org.json.JSONTokener(json).nextValue() as? String ?: json
            } catch (_: Exception) {
                json = json.substring(1, json.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\/", "/")
            }
        }

        val list = mutableListOf<ExtractedImage>()
        val seen = mutableSetOf<String>()

        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val rawUrl = obj.optString("url", "").trim()
                if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) continue

                // Optimize Twitter / X image URLs to request original high resolution
                val optimizedUrl = if (platformId == "x" || rawUrl.contains("pbs.twimg.com/media")) {
                    optimizeTwitterImageUrl(rawUrl)
                } else {
                    rawUrl
                }

                if (!seen.add(optimizedUrl)) continue

                val width = obj.optInt("width", 0)
                val height = obj.optInt("height", 0)
                val alt = obj.optString("alt", "").trim()

                list.add(
                    ExtractedImage(
                        url = optimizedUrl,
                        width = width,
                        height = height,
                        alt = alt,
                        isSelected = true
                    )
                )
            }
        } catch (_: Exception) {
            // Graceful fallback
        }

        return list
    }

    /**
     * Upgrades Twitter media URLs from small/medium preview to original full resolution.
     */
    fun optimizeTwitterImageUrl(url: String): String {
        return when {
            url.contains("&name=") -> {
                url.replace(Regex("&name=[a-zA-Z0-9_]+"), "&name=large")
            }
            url.contains("?name=") -> {
                url.replace(Regex("\\?name=[a-zA-Z0-9_]+"), "?name=large")
            }
            url.contains("pbs.twimg.com/media") && !url.contains("?") && !url.substringAfter("pbs.twimg.com/media").contains(":") -> {
                "$url?name=large"
            }
            else -> url
        }
    }

    /**
     * Generates a safe, user-friendly filename for an extracted image.
     */
    fun generateSafeFileName(
        item: ExtractedImage,
        platformId: String?,
        index: Int,
        total: Int
    ): String {
        val platformPrefix = when (platformId?.lowercase()) {
            "instagram" -> "Instagram"
            "tiktok" -> "TikTok"
            "facebook" -> "Facebook"
            "x", "twitter" -> "X"
            "reddit" -> "Reddit"
            "pinterest" -> "Pinterest"
            "threads" -> "Threads"
            else -> "Image"
        }

        val format = DownloadHelper.inferMediaFormat(item.url, null, true)
        val ext = format?.first ?: "jpg"

        val timestamp = System.currentTimeMillis()
        val indexSuffix = if (total > 1) "_${index + 1}_of_$total" else ""

        return "${platformPrefix}_photo${indexSuffix}_$timestamp.$ext"
    }

    /**
     * Asynchronously loads a thumbnail bitmap for display in the bulk download sheet.
     */
    fun loadThumbnail(
        url: String,
        cookieManager: CookieManager?,
        userAgent: String?,
        onLoaded: (Bitmap?) -> Unit
    ) {
        val requestBuilder = Request.Builder().url(url)
        val cookies = cookieManager?.getCookie(url)
        if (!cookies.isNullOrBlank()) {
            requestBuilder.addHeader("cookie", cookies)
        }
        if (!userAgent.isNullOrBlank()) {
            requestBuilder.addHeader("User-Agent", userAgent)
        }
        val request = requestBuilder.build()

        httpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onLoaded(null)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        onLoaded(null)
                        return
                    }
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        val sampleSize = calculateInSampleSize(options, 180, 180)
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                        onLoaded(bitmap)
                    } else {
                        onLoaded(null)
                    }
                }
            }
        })
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
