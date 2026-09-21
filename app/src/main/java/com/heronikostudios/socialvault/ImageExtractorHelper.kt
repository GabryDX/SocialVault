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

data class ActiveMedia(
    val type: String, // "video" or "image"
    val url: String
)

object ImageExtractorHelper {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * JavaScript executed in WebView to extract all high-resolution images,
     * traversing Instagram React Fiber state, embedded script GraphQL/Relay payloads,
     * DOM <img> elements with srcset, and background images.
     */
    val EXTRACTION_SCRIPT = """
        (function() {
            try {
                var results = [];
                var seenKeys = new Set();

                function cleanUrl(u) {
                    if (!u) return null;
                    var s = u.trim().replace(/\\\//g, '/').replace(/\\u0026/g, '&');
                    if (s.indexOf('http://') !== 0 && s.indexOf('https://') !== 0) return null;
                    if (s.indexOf('data:') === 0 || s.indexOf('blob:') === 0) return null;
                    var hashIdx = s.indexOf('#');
                    if (hashIdx !== -1) s = s.substring(0, hashIdx);
                    return s;
                }

                function getDedupKey(u) {
                    if (!u) return '';
                    var clean = u.split('?')[0].split('#')[0];
                    var parts = clean.split('/');
                    var last = parts[parts.length - 1];
                    if (last && last.indexOf('.') !== -1 && last.length > 5) {
                        return last.toLowerCase();
                    }
                    return clean.toLowerCase();
                }

                function addUrl(rawUrl, w, h, alt) {
                    var url = cleanUrl(rawUrl);
                    if (!url) return;

                    var lower = url.toLowerCase();
                    if (lower.indexOf('emoji') !== -1 ||
                        lower.indexOf('favicon') !== -1 ||
                        lower.indexOf('1x1') !== -1 ||
                        lower.indexOf('/icons/') !== -1 ||
                        lower.indexOf('/rsrc.php/') !== -1) {
                        return;
                    }

                    var key = getDedupKey(url);
                    if (seenKeys.has(key)) {
                        if (w > 0 || h > 0) {
                            for (var e = 0; e < results.length; e++) {
                                if (getDedupKey(results[e].url) === key) {
                                    if ((w * h) > (results[e].width * results[e].height)) {
                                        results[e].url = url;
                                        results[e].width = w;
                                        results[e].height = h;
                                        if (alt) results[e].alt = alt;
                                    }
                                    break;
                                }
                            }
                        }
                        return;
                    }
                    seenKeys.add(key);

                    results.push({
                        url: url,
                        width: w || 0,
                        height: h || 0,
                        alt: (alt || '').substring(0, 100).trim()
                    });
                }

                function getBestFromResources(resources) {
                    if (!resources || !resources.length) return null;
                    var best = resources[0];
                    for (var r = 1; r < resources.length; r++) {
                        var w = resources[r].config_width || resources[r].width || 0;
                        var bw = best.config_width || best.width || 0;
                        if (w >= bw) best = resources[r];
                    }
                    return {
                        url: best.src || best.url,
                        width: best.config_width || best.width || 0,
                        height: best.config_height || best.height || 0
                    };
                }

                function processInstagramMediaItem(item) {
                    if (!item) return;
                    var node = item.node || item;
                    var alt = node.accessibility_caption || '';

                    if (node.display_resources && node.display_resources.length > 0) {
                        var best = getBestFromResources(node.display_resources);
                        if (best && best.url) {
                            addUrl(best.url, best.width, best.height, alt);
                            return;
                        }
                    }

                    if (node.image_versions2 && node.image_versions2.candidates && node.image_versions2.candidates.length > 0) {
                        var bestCand = getBestFromResources(node.image_versions2.candidates);
                        if (bestCand && bestCand.url) {
                            addUrl(bestCand.url, bestCand.width, bestCand.height, alt);
                            return;
                        }
                    }

                    if (node.display_url) {
                        var dim = node.dimensions || {};
                        addUrl(node.display_url, dim.width || 1080, dim.height || 1080, alt);
                    }
                }

                // Phase 1: Instagram React Fiber Discovery (Captures all carousel slides in sequence)
                var containers = document.querySelectorAll('article, section, main, [role="main"], [role="presentation"]');
                for (var c = 0; c < containers.length; c++) {
                    var el = containers[c];
                    var fKey = Object.keys(el).find(function(k) {
                        return k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance');
                    });
                    if (!fKey) continue;
                    var curr = el[fKey];
                    var depth = 0;
                    while (curr && depth < 25) {
                        var props = curr.memoizedProps;
                        if (props) {
                            var post = props.post || props.media || props.item || props.story;
                            if (post) {
                                var carouselItems = post.carousel_media || 
                                                   (post.edge_sidecar_to_children && post.edge_sidecar_to_children.edges) || 
                                                   (props.items && Array.isArray(props.items) && props.items);
                                if (carouselItems && carouselItems.length > 0) {
                                    for (var k = 0; k < carouselItems.length; k++) {
                                        processInstagramMediaItem(carouselItems[k]);
                                    }
                                } else {
                                    processInstagramMediaItem(post);
                                }
                            }
                        }
                        curr = curr.return;
                        depth++;
                    }
                }

                // Phase 2: Instagram Scripts Parsing (GraphQL sidecar & Relay store JSON)
                var scripts = document.getElementsByTagName('script');
                for (var s = 0; s < scripts.length; s++) {
                    var sc = scripts[s];
                    var content = sc.textContent || '';
                    if (!content) continue;

                    var hasSidecar = content.indexOf('edge_sidecar_to_children') !== -1;
                    var hasCarousel = content.indexOf('carousel_media') !== -1;
                    var hasDisplayResources = content.indexOf('display_resources') !== -1;

                    if (sc.type === 'application/json' && (hasSidecar || hasCarousel || hasDisplayResources)) {
                        try {
                            var parsed = JSON.parse(content);
                            var stack = [parsed];
                            while (stack.length > 0) {
                                var obj = stack.pop();
                                if (!obj || typeof obj !== 'object') continue;

                                if (obj.edge_sidecar_to_children && obj.edge_sidecar_to_children.edges) {
                                    var edges = obj.edge_sidecar_to_children.edges;
                                    for (var e = 0; e < edges.length; e++) {
                                        processInstagramMediaItem(edges[e]);
                                    }
                                }
                                if (obj.carousel_media && Array.isArray(obj.carousel_media)) {
                                    for (var cm = 0; cm < obj.carousel_media.length; cm++) {
                                        processInstagramMediaItem(obj.carousel_media[cm]);
                                    }
                                }
                                for (var prop in obj) {
                                    if (obj.hasOwnProperty(prop) && typeof obj[prop] === 'object' && obj[prop] !== null) {
                                        stack.push(obj[prop]);
                                    }
                                }
                            }
                        } catch(e) {}
                    } else if (hasSidecar || hasCarousel || hasDisplayResources) {
                        var resMatches = content.match(/\"display_resources\"\s*:\s*(\[[^\]]+\])/g);
                        if (resMatches) {
                            for (var rm = 0; rm < resMatches.length; rm++) {
                                try {
                                    var jsonPart = resMatches[rm].replace(/^\"display_resources\"\s*:\s*/, '')
                                                                 .replace(/\\\//g, '/')
                                                                 .replace(/\\u0026/g, '&');
                                    var resList = JSON.parse(jsonPart);
                                    var bestR = getBestFromResources(resList);
                                    if (bestR && bestR.url) {
                                        addUrl(bestR.url, bestR.width, bestR.height, '');
                                    }
                                } catch(e) {}
                            }
                        }

                        var candMatches = content.match(/\"image_versions2\"\s*:\s*\{\s*\"candidates\"\s*:\s*(\[[^\]]+\])/g);
                        if (candMatches) {
                            for (var ci = 0; ci < candMatches.length; ci++) {
                                try {
                                    var candJson = candMatches[ci].replace(/^\"image_versions2\"\s*:\s*\{\s*\"candidates\"\s*:\s*/, '')
                                                                  .replace(/\\\//g, '/')
                                                                  .replace(/\\u0026/g, '&');
                                    var candList = JSON.parse(candJson);
                                    var bestC = getBestFromResources(candList);
                                    if (bestC && bestC.url) {
                                        addUrl(bestC.url, bestC.width, bestC.height, '');
                                    }
                                } catch(e) {}
                            }
                        }

                        var urlMatches = content.match(/\"display_url\"\s*:\s*\"([^\"]+)\"/g);
                        if (urlMatches) {
                            for (var um = 0; um < urlMatches.length; um++) {
                                var rawU = urlMatches[um].replace(/^\"display_url\"\s*:\s*\"/, '').replace(/\"$/, '');
                                addUrl(rawU, 1080, 1080, '');
                            }
                        }
                    }
                }

                // Phase 3: DOM Scanning (Existing <img> elements with srcset parsing)
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

                var images = document.getElementsByTagName('img');
                for (var i = 0; i < images.length; i++) {
                    var img = images[i];
                    var natW = img.naturalWidth || img.width || 0;
                    var natH = img.naturalHeight || img.height || 0;

                    if (natW > 0 && natW < 120 && natH > 0 && natH < 120) continue;

                    var bestUrl = getBestFromSrcset(img.srcset);
                    if (!bestUrl) bestUrl = img.currentSrc || img.src;

                    if (bestUrl) {
                        addUrl(bestUrl, natW, natH, img.alt);
                    }
                }

                // Phase 4: CSS Background Images
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
     * JavaScript to detect the active video or story media (image or video) currently displayed.
     * Checks HTML5 videos, React Fiber on video/story elements, story viewer containers, and scripts.
     */
    val DETECT_ACTIVE_MEDIA_SCRIPT = """
        (function() {
            try {
                function clean(u) {
                    if (!u) return null;
                    var s = u.trim().replace(/\\\//g, '/').replace(/\\u0026/g, '&');
                    if (s.indexOf('http://') === 0 || s.indexOf('https://') === 0) return s;
                    return null;
                }

                function getBest(arr) {
                    if (!arr || !arr.length) return null;
                    var best = arr[0];
                    for (var r = 1; r < arr.length; r++) {
                        var w = arr[r].config_width || arr[r].width || 0;
                        var bw = best.config_width || best.width || 0;
                        if (w >= bw) best = arr[r];
                    }
                    return best.src || best.url;
                }

                // 1. Inspect HTML5 <video> elements
                var videos = Array.from(document.querySelectorAll('video'));
                for (var i = 0; i < videos.length; i++) {
                    var v = videos[i];
                    var isPlaying = !v.paused || v.currentTime > 0;
                    var isFs = document.fullscreenElement === v || document.webkitFullscreenElement === v;
                    var isVisible = (v.offsetWidth > 150 && v.offsetHeight > 150) || (v.videoWidth > 0);

                    if (isPlaying || isFs || isVisible) {
                        var src = clean(v.currentSrc || v.src);
                        if (src) return JSON.stringify({ type: 'video', url: src });

                        var source = v.querySelector('source[src]');
                        if (source) {
                            var sSrc = clean(source.src);
                            if (sSrc) return JSON.stringify({ type: 'video', url: sSrc });
                        }

                        // Check React Fiber on video element
                        var fKey = Object.keys(v).find(function(k) {
                            return k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance');
                        });
                        if (fKey) {
                            var curr = v[fKey];
                            var depth = 0;
                            while (curr && depth < 25) {
                                var p = curr.memoizedProps;
                                if (p) {
                                    var item = p.item || p.post || p.media || p.story;
                                    if (item) {
                                        if (item.video_versions && item.video_versions.length > 0) {
                                            var vUrl = clean(item.video_versions[0].url);
                                            if (vUrl) return JSON.stringify({ type: 'video', url: vUrl });
                                        }
                                        if (item.image_versions2 && item.image_versions2.candidates) {
                                            var iUrl = clean(getBest(item.image_versions2.candidates));
                                            if (iUrl) return JSON.stringify({ type: 'image', url: iUrl });
                                        }
                                    }
                                    if (p.videoUrl) {
                                        var vu = clean(p.videoUrl);
                                        if (vu) return JSON.stringify({ type: 'video', url: vu });
                                    }
                                }
                                curr = curr.return;
                                depth++;
                            }
                        }
                    }
                }

                // 2. Inspect active Story / Dialog containers
                var containers = document.querySelectorAll('section, [role="dialog"], [role="presentation"], article, div[style*="z-index"]');
                for (var c = 0; c < containers.length; c++) {
                    var el = containers[c];
                    var cfKey = Object.keys(el).find(function(k) {
                        return k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance');
                    });
                    if (!cfKey) continue;
                    var cCurr = el[cfKey];
                    var cDepth = 0;
                    while (cCurr && cDepth < 25) {
                        var cProps = cCurr.memoizedProps;
                        if (cProps) {
                            var mItem = cProps.item || cProps.post || cProps.media || cProps.story;
                            if (mItem) {
                                if (mItem.video_versions && mItem.video_versions.length > 0) {
                                    var vidUrl = clean(mItem.video_versions[0].url);
                                    if (vidUrl) return JSON.stringify({ type: 'video', url: vidUrl });
                                }
                                if (mItem.image_versions2 && mItem.image_versions2.candidates) {
                                    var imgUrl = clean(getBest(mItem.image_versions2.candidates));
                                    if (imgUrl) return JSON.stringify({ type: 'image', url: imgUrl });
                                }
                                if (mItem.display_url) {
                                    var dUrl = clean(mItem.display_url);
                                    if (dUrl) return JSON.stringify({ type: 'image', url: dUrl });
                                }
                            }
                        }
                        cCurr = cCurr.return;
                        cDepth++;
                    }
                }

                // 3. Check for active story image in the DOM
                var storyImgs = document.querySelectorAll('section img, [role="dialog"] img');
                for (var s = 0; s < storyImgs.length; s++) {
                    var simg = storyImgs[s];
                    if (simg.naturalWidth > 200 || simg.offsetWidth > 200) {
                        var isrc = clean(simg.currentSrc || simg.src);
                        if (isrc) return JSON.stringify({ type: 'image', url: isrc });
                    }
                }

                // 4. Check for any video URL in page scripts if on a story or reel permalink
                if (window.location.href.indexOf('/stories/') !== -1 || window.location.href.indexOf('/reel/') !== -1) {
                    var scripts = document.getElementsByTagName('script');
                    for (var sc = 0; sc < scripts.length; sc++) {
                        var content = scripts[sc].textContent || '';
                        var m = content.match(/\"video_versions\"\s*:\s*\[\s*\{\s*[^}]*\"url\"\s*:\s*\"([^\"]+)\"/);
                        if (m && m[1]) {
                            var scriptVid = clean(m[1]);
                            if (scriptVid) return JSON.stringify({ type: 'video', url: scriptVid });
                        }
                        var mPhoto = content.match(/\"display_url\"\s*:\s*\"([^\"]+)\"/);
                        if (mPhoto && mPhoto[1]) {
                            var scriptPhoto = clean(mPhoto[1]);
                            if (scriptPhoto) return JSON.stringify({ type: 'image', url: scriptPhoto });
                        }
                    }
                }

                return null;
            } catch(e) {
                return null;
            }
        })();
    """.trimIndent()

    /**
     * Extracts a deduplication key based on media filename or base path.
     */
    fun getDedupKey(url: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val lastSegment = clean.substringAfterLast('/')
        return if (lastSegment.contains('.') && lastSegment.length > 5) {
            lastSegment.lowercase()
        } else {
            clean.lowercase()
        }
    }

    /**
     * Cleans and sanitizes a media URL, stripping byte range parameters (e.g. MSE chunking).
     */
    fun cleanMediaUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        var u = rawUrl.trim()
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
        if (!u.startsWith("http://") && !u.startsWith("https://")) return null

        u = u.replace(Regex("[?&]bytestart=\\d+"), "")
        u = u.replace(Regex("[?&]byteend=\\d+"), "")
        if (!u.contains("?") && u.contains("&")) {
            u = u.replaceFirst("&", "?")
        }
        if (u.endsWith("?")) {
            u = u.dropLast(1)
        }
        return u
    }

    /**
     * Parses the result of DETECT_ACTIVE_MEDIA_SCRIPT.
     */
    fun parseActiveMedia(rawJson: String?): ActiveMedia? {
        if (rawJson.isNullOrBlank() || rawJson == "null" || rawJson == "{}") return null
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
        return try {
            val obj = org.json.JSONObject(json)
            val type = obj.optString("type", "").trim().lowercase()
            val rawUrl = obj.optString("url", "").trim()
            val cleanUrl = cleanMediaUrl(rawUrl)
            if (cleanUrl != null && (type == "video" || type == "image")) {
                ActiveMedia(type = type, url = cleanUrl)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a safe filename for a story media download.
     */
    fun generateStoryFileName(
        platformId: String?,
        isVideo: Boolean,
        ext: String = if (isVideo) "mp4" else "jpg"
    ): String {
        val platformPrefix = when (platformId?.lowercase()) {
            "instagram" -> "Instagram"
            "tiktok" -> "TikTok"
            "facebook" -> "Facebook"
            "x", "twitter" -> "X"
            "threads" -> "Threads"
            else -> "Story"
        }
        val mediaType = if (isVideo) "video" else "photo"
        val timestamp = System.currentTimeMillis()
        return "${platformPrefix}_story_${mediaType}_$timestamp.$ext"
    }

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

        val map = mutableMapOf<String, ExtractedImage>()
        val order = mutableListOf<String>()

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

                val width = obj.optInt("width", 0)
                val height = obj.optInt("height", 0)
                val alt = obj.optString("alt", "").trim()

                val item = ExtractedImage(
                    url = optimizedUrl,
                    width = width,
                    height = height,
                    alt = alt,
                    isSelected = true
                )

                val key = getDedupKey(optimizedUrl)
                val existing = map[key]
                if (existing == null) {
                    map[key] = item
                    order.add(key)
                } else {
                    if ((width * height) > (existing.width * existing.height)) {
                        map[key] = item
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback
        }

        return order.mapNotNull { map[it] }
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
