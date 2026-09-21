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
    val videoUrl: String? = null,
    val imageUrl: String? = null,
    val hasVideo: Boolean = !videoUrl.isNullOrBlank(),
    val hasImage: Boolean = !imageUrl.isNullOrBlank()
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
                        lower.indexOf('/rsrc.php/') !== -1 ||
                        lower.indexOf('t51.2885-19') !== -1 ||
                        lower.indexOf('profile_pic') !== -1 ||
                        lower.indexOf('avatar') !== -1 ||
                        /(s|p)\d{2,3}x\d{2,3}/.test(lower) ||
                        lower.indexOf('s150x150') !== -1 ||
                        lower.indexOf('s320x320') !== -1) {
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
     * When viewing Stories or Highlights, strictly confines all DOM and React Fiber searches to
     * the active story viewer overlay to prevent intercepting the page below.
     * Accurately extracts videos, high-res photos, and multi-slide highlight reels.
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

                function isAvatar(url, el) {
                    var u = (url || '').toLowerCase();
                    if (u.indexOf('t51.2885-19') !== -1 ||
                        u.indexOf('profile_pic') !== -1 ||
                        u.indexOf('avatar') !== -1 ||
                        u.indexOf('emoji') !== -1 ||
                        u.indexOf('favicon') !== -1 ||
                        u.indexOf('s150x150') !== -1 ||
                        u.indexOf('s320x320') !== -1 ||
                        /(s|p)(150|100|80|75|64|50)x\d{2,3}/.test(u)) {
                        return true;
                    }
                    if (el) {
                        var alt = (el.getAttribute('alt') || '').toLowerCase();
                        var aria = (el.getAttribute('aria-label') || '').toLowerCase();
                        if (alt.indexOf('profil') !== -1 || alt.indexOf('perfil') !== -1 || alt.indexOf('avatar') !== -1 ||
                            aria.indexOf('profil') !== -1 || aria.indexOf('perfil') !== -1 || aria.indexOf('avatar') !== -1) {
                            return true;
                        }
                        var r = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
                        var w = (r && r.width) || el.offsetWidth || 0;
                        var h = (r && r.height) || el.offsetHeight || 0;
                        if (w > 0 && h > 0 && w < 100 && h < 100) {
                            return true;
                        }
                    }
                    return false;
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

                function extractMediaFromItem(item) {
                    if (!item) return null;
                    var vid = null;
                    var img = null;
                    if (item.video_versions && item.video_versions.length > 0) {
                        vid = clean(item.video_versions[0].url);
                    }
                    if (item.image_versions2 && item.image_versions2.candidates && item.image_versions2.candidates.length > 0) {
                        var cand = clean(getBest(item.image_versions2.candidates));
                        if (cand && !isAvatar(cand, null)) img = cand;
                    }
                    if (!img && item.display_url) {
                        var d = clean(item.display_url);
                        if (d && !isAvatar(d, null)) img = d;
                    }
                    if (vid || img) {
                        return { videoUrl: vid, imageUrl: img };
                    }
                    return null;
                }

                var videoUrl = null;
                var imageUrl = null;

                // Detect if viewing Instagram Stories or Highlights
                var isStoryPage = window.location.pathname.indexOf('/stories/') !== -1;
                var storyViewer = null;

                // Locate the active Story/Highlight Modal or Section
                var closeBtn = document.querySelector('[aria-label="Close"], [aria-label="Chiudi"], [aria-label="Cerrar"], [aria-label="Fermer"], [aria-label*="lose"]');
                if (closeBtn) {
                    storyViewer = closeBtn.closest('section, [role="dialog"], [role="presentation"]');
                    if (!storyViewer && closeBtn.parentElement) {
                        var p = closeBtn.parentElement;
                        while (p && p !== document.body && p !== document.documentElement) {
                            var pst = window.getComputedStyle(p);
                            if (pst.position === 'fixed' || pst.position === 'absolute' || parseInt(pst.zIndex, 10) > 0) {
                                storyViewer = p;
                                break;
                            }
                            p = p.parentElement;
                        }
                    }
                }

                if (!storyViewer) {
                    var pBar = document.querySelector('[role="progressbar"], div[style*="scaleX"]');
                    if (pBar) {
                        storyViewer = pBar.closest('section, [role="dialog"], [role="presentation"]');
                    }
                }

                if (!storyViewer && isStoryPage) {
                    var overlays = document.querySelectorAll('section, [role="dialog"]');
                    for (var oi = 0; oi < overlays.length; oi++) {
                        var cand = overlays[oi];
                        var cst = window.getComputedStyle(cand);
                        if (cst.position === 'fixed' || cst.position === 'absolute' || parseInt(cst.zIndex, 10) > 0) {
                            storyViewer = cand;
                            break;
                        }
                    }
                }

                // ==============================================================
                // BRANCH A: WE ARE VIEWING STORIES / HIGHLIGHTS
                // STRICTLY SCOPE SEARCH TO THE STORY VIEWER SO IT NEVER INTERCEPTS
                // THE PAGE BELOW!
                // ==============================================================
                if (isStoryPage || storyViewer) {
                    // 1. Try React Fiber on the storyViewer or its elements
                    var fiberNodes = [storyViewer];
                    if (storyViewer && storyViewer.querySelectorAll) {
                        var subNodes = storyViewer.querySelectorAll('section, div, video, img');
                        for (var sn = 0; sn < Math.min(subNodes.length, 30); sn++) {
                            fiberNodes.push(subNodes[sn]);
                        }
                    }

                    for (var fn = 0; fn < fiberNodes.length; fn++) {
                        var node = fiberNodes[fn];
                        if (!node) continue;
                        var fKey = Object.keys(node).find(function(k) {
                            return k.startsWith('__reactFiber') || k.startsWith('__reactInternalInstance');
                        });
                        if (!fKey) continue;
                        var curr = node[fKey];
                        var depth = 0;
                        while (curr && depth < 25) {
                            var props = curr.memoizedProps;
                            if (props) {
                                var itm = props.item || props.story || props.media;
                                if (itm) {
                                    var ext = extractMediaFromItem(itm);
                                    if (ext) {
                                        if (ext.videoUrl && !videoUrl) videoUrl = ext.videoUrl;
                                        if (ext.imageUrl && !imageUrl) imageUrl = ext.imageUrl;
                                    }
                                }
                                var reelItems = (props.reel && props.reel.items) || (props.highlight && props.highlight.items) || (props.items && Array.isArray(props.items) && props.items);
                                if (reelItems && Array.isArray(reelItems) && reelItems.length > 0) {
                                    var activeIdx = props.currentIndex || props.activeItemIndex || props.selectedItemIndex || 0;
                                    var rItem = reelItems[activeIdx] || reelItems[0];
                                    if (rItem) {
                                        var rExt = extractMediaFromItem(rItem);
                                        if (rExt) {
                                            if (rExt.videoUrl && !videoUrl) videoUrl = rExt.videoUrl;
                                            if (rExt.imageUrl && !imageUrl) imageUrl = rExt.imageUrl;
                                        }
                                    }
                                }
                            }
                            curr = curr.return;
                            depth++;
                        }
                        if (videoUrl && imageUrl) break;
                    }

                    // 2. Search DOM inside storyViewer ONLY
                    if (storyViewer) {
                        // Check for video inside storyViewer
                        if (!videoUrl) {
                            var storyVideos = storyViewer.querySelectorAll('video');
                            for (var sv = 0; sv < storyVideos.length; sv++) {
                                var sVid = storyVideos[sv];
                                var vUrl = clean(sVid.currentSrc || sVid.src);
                                if (vUrl && vUrl.indexOf('blob:') === -1) videoUrl = vUrl;
                                var sSource = sVid.querySelector('source[src]');
                                if (sSource && !videoUrl) {
                                    var sn = clean(sSource.src);
                                    if (sn && sn.indexOf('blob:') === -1) videoUrl = sn;
                                }
                                if (sVid.poster && !imageUrl) {
                                    var pUrl = clean(sVid.poster);
                                    if (pUrl && !isAvatar(pUrl, null)) imageUrl = pUrl;
                                }
                                if (videoUrl) break;
                            }
                        }

                        // Check for image inside storyViewer
                        if (!imageUrl) {
                            var storyImgs = storyViewer.querySelectorAll('img');
                            var bestStoryArea = 0;
                            for (var si = 0; si < storyImgs.length; si++) {
                                var sImg = storyImgs[si];
                                var sW = sImg.offsetWidth || sImg.naturalWidth || 0;
                                var sH = sImg.offsetHeight || sImg.naturalHeight || 0;
                                var candSrc = getBestFromSrcset(sImg.srcset) || sImg.currentSrc || sImg.src;
                                var cleaned = clean(candSrc);
                                if (!cleaned || isAvatar(cleaned, sImg)) continue;
                                var sArea = sW * sH;
                                if (sArea >= bestStoryArea) {
                                    bestStoryArea = sArea;
                                    imageUrl = cleaned;
                                }
                            }
                        }
                    }

                    // 3. Performance Resource Timing for active story media loaded recently
                    if ((!imageUrl || !videoUrl) && window.performance && performance.getEntriesByType) {
                        var entries = performance.getEntriesByType('resource');
                        var now = performance.now();
                        for (var re = entries.length - 1; re >= 0; re--) {
                            var res = entries[re];
                            if ((now - res.responseEnd) > 30000) continue;
                            var resName = res.name || '';
                            if (!imageUrl && resName.indexOf('.fbcdn.net') !== -1 && resName.indexOf('t51.2885-15') !== -1 && !isAvatar(resName, null)) {
                                var cRes = clean(resName);
                                if (cRes && !isAvatar(cRes, null)) imageUrl = cRes;
                            }
                            if (!videoUrl && resName.indexOf('.fbcdn.net') !== -1 && (resName.indexOf('.mp4') !== -1 || resName.indexOf('t50.2886-16') !== -1)) {
                                var cVid = clean(resName);
                                if (cVid) videoUrl = cVid;
                            }
                            if (videoUrl && imageUrl) break;
                        }
                    }

                    // For stories/highlights: NEVER fall back to searching the page below!
                    if (videoUrl || imageUrl) {
                        return JSON.stringify({
                            videoUrl: videoUrl,
                            imageUrl: imageUrl,
                            hasVideo: !!videoUrl,
                            hasImage: !!imageUrl
                        });
                    }
                    return null;
                }

                // ==============================================================
                // BRANCH B: REGULAR WEBPAGE (Not Stories / Highlights)
                // ==============================================================
                var cx = window.innerWidth / 2;
                var cy = window.innerHeight / 2;

                var videos = Array.from(document.querySelectorAll('video'));
                for (var vIdx = 0; vIdx < videos.length; vIdx++) {
                    var vid = videos[vIdx];
                    var isFs = document.fullscreenElement === vid || document.webkitFullscreenElement === vid;
                    var isPlaying = !vid.paused || vid.currentTime > 0;
                    var vr = vid.getBoundingClientRect();
                    var isCentered = (vr.left <= cx && vr.right >= cx && vr.top <= cy && vr.bottom >= cy);
                    var isVisible = (vr.width > 150 && vr.height > 150) || vid.videoWidth > 0;

                    if (isFs || isPlaying || (isCentered && isVisible)) {
                        var vUrl = clean(vid.currentSrc || vid.src);
                        if (vUrl && vUrl.indexOf('blob:') === -1) videoUrl = vUrl;
                        var srcNode = vid.querySelector('source[src]');
                        if (srcNode && !videoUrl) {
                            var sn = clean(srcNode.src);
                            if (sn && sn.indexOf('blob:') === -1) videoUrl = sn;
                        }
                        if (vid.poster && !imageUrl) {
                            var pUrl = clean(vid.poster);
                            if (pUrl && !isAvatar(pUrl, null)) imageUrl = pUrl;
                        }
                        if (videoUrl) break;
                    }
                }

                if (!imageUrl) {
                    var allImgs = Array.from(document.querySelectorAll('img'));
                    var bestScore = -1;
                    var bestUrlCandidate = null;

                    for (var si = 0; si < allImgs.length; si++) {
                        var sImg = allImgs[si];
                        var sr = sImg.getBoundingClientRect();
                        var sWidth = sr.width || sImg.offsetWidth || sImg.naturalWidth || 0;
                        var sHeight = sr.height || sImg.offsetHeight || sImg.naturalHeight || 0;

                        if (sWidth < 140 || sHeight < 140) continue;

                        var candidateUrl = getBestFromSrcset(sImg.srcset) || sImg.currentSrc || sImg.src;
                        var cleanedCandidate = clean(candidateUrl);
                        if (!cleanedCandidate || isAvatar(cleanedCandidate, sImg)) continue;

                        var isOverCenter = (sr.left <= cx && sr.right >= cx && sr.top <= cy && sr.bottom >= cy);
                        var distFromCenter = Math.hypot((sr.left + sr.right)/2 - cx, (sr.top + sr.bottom)/2 - cy);
                        var score = (isOverCenter ? 10000 : 0) + (sWidth * sHeight) - distFromCenter;

                        if (score > bestScore) {
                            bestScore = score;
                            bestUrlCandidate = cleanedCandidate;
                        }
                    }

                    if (bestUrlCandidate) {
                        imageUrl = bestUrlCandidate;
                    }
                }

                if (!videoUrl && !imageUrl) return null;

                return JSON.stringify({
                    videoUrl: videoUrl,
                    imageUrl: imageUrl,
                    hasVideo: !!videoUrl,
                    hasImage: !!imageUrl
                });
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
     * Parses the result of DETECT_ACTIVE_MEDIA_SCRIPT into ActiveMedia.
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
            val rawVideo = obj.optString("videoUrl", "").takeIf { it.isNotBlank() }
            val rawImage = obj.optString("imageUrl", "").takeIf { it.isNotBlank() }

            val cleanVideo = cleanMediaUrl(rawVideo)
            val cleanImage = cleanMediaUrl(rawImage)

            if (cleanVideo != null || cleanImage != null) {
                ActiveMedia(
                    videoUrl = cleanVideo,
                    imageUrl = cleanImage,
                    hasVideo = cleanVideo != null,
                    hasImage = cleanImage != null
                )
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
