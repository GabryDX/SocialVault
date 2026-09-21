package com.heronikostudios.socialvault

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

object DownloadHelper {

    fun downloadFile(
        context: Context,
        url: String,
        userAgent: String? = null,
        contentDisposition: String? = null,
        mimeType: String? = null,
        cookieManager: CookieManager? = null,
        customFileName: String? = null,
        isImage: Boolean = false
    ) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.startsWith("data:image/")) {
            saveBase64Image(context, trimmedUrl)
            return
        }

        if (trimmedUrl.startsWith("blob:")) {
            Toast.makeText(context, "Streamed video chunks cannot be downloaded directly", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = try {
            Uri.parse(trimmedUrl)
        } catch (_: Exception) {
            Toast.makeText(context, "Invalid download URL", Toast.LENGTH_SHORT).show()
            return
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(context, "Cannot download: unsupported protocol", Toast.LENGTH_SHORT).show()
            return
        }

        val (fileName, resolvedMime) = resolveFileName(
            url = trimmedUrl,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            customFileName = customFileName,
            isImage = isImage
        )

        try {
            val request = DownloadManager.Request(uri).apply {
                if (!resolvedMime.isNullOrBlank()) {
                    setMimeType(resolvedMime)
                }

                // Preserve session cookies for authenticated social media media requests
                val cm = cookieManager ?: CookieManager.getInstance()
                val cookies = cm.getCookie(trimmedUrl)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("cookie", cookies)
                }

                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }

                setTitle(fileName)
                setDescription("Downloading via SocialVault")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null) {
                downloadManager.enqueue(request)
                Toast.makeText(context, "Downloading $fileName...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "DownloadManager service unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBase64Image(context: Context, dataUrl: String) {
        try {
            val commaIndex = dataUrl.indexOf(',')
            if (commaIndex == -1) {
                Toast.makeText(context, "Invalid image data", Toast.LENGTH_SHORT).show()
                return
            }

            val header = dataUrl.substring(0, commaIndex)
            val base64Data = dataUrl.substring(commaIndex + 1)
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)

            val ext = when {
                header.contains("png", ignoreCase = true) -> "png"
                header.contains("webp", ignoreCase = true) -> "webp"
                header.contains("gif", ignoreCase = true) -> "gif"
                header.contains("svg", ignoreCase = true) -> "svg"
                else -> "jpg"
            }
            val fileName = "download_${System.currentTimeMillis()}.$ext"
            val resolvedMime = when (ext) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "svg" -> "image/svg+xml"
                else -> "image/jpeg"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                    Toast.makeText(context, "Saved $fileName to Downloads", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to create download file", Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                file.outputStream().use { out ->
                    out.write(bytes)
                }
                Toast.makeText(context, "Saved $fileName to Downloads", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save image: ${e.message ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showMediaContextMenu(
        context: Context,
        mediaUrl: String,
        isImage: Boolean = true,
        userAgent: String? = null,
        cookieManager: CookieManager? = null,
        onOpenInNewTab: (String) -> Unit
    ) {
        val title = if (isImage) "Image Options" else "Media Options"
        val items = arrayOf(
            if (isImage) "Save Image to Downloads" else "Save Video to Downloads",
            "Open Media in New Tab",
            "Copy Link",
            "Share Link"
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> downloadFile(
                        context = context,
                        url = mediaUrl,
                        userAgent = userAgent,
                        cookieManager = cookieManager,
                        isImage = isImage
                    )
                    1 -> onOpenInNewTab(mediaUrl)
                    2 -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("Media URL", mediaUrl)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, mediaUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Media Link"))
                    }
                }
            }
            .show()
    }

    fun sanitizeFileName(name: String): String {
        val sanitized = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .trimStart('.')
            .take(120)
            .trimEnd('.')
        return if (sanitized.isNotBlank()) sanitized else "download_${System.currentTimeMillis()}"
    }

    fun getQueryParameter(url: String, key: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        for (param in query.split('&')) {
            val parts = param.split('=', limit = 2)
            if (parts.isNotEmpty() && parts[0].equals(key, ignoreCase = true)) {
                return if (parts.size == 2) parts[1] else ""
            }
        }
        return null
    }

    fun inferMediaFormat(
        url: String?,
        declaredMime: String? = null,
        isImage: Boolean = false
    ): Pair<String, String>? {
        if (url.isNullOrBlank() && declaredMime.isNullOrBlank()) {
            return if (isImage) Pair("jpg", "image/jpeg") else null
        }

        // 1. Check declared MIME type if specific and not generic octet-stream
        if (!declaredMime.isNullOrBlank() &&
            !declaredMime.equals("application/octet-stream", ignoreCase = true) &&
            !declaredMime.equals("*/*", ignoreCase = true)
        ) {
            val mime = declaredMime.substringBefore(';').trim().lowercase()
            val ext = when (mime) {
                "image/jpeg", "image/jpg", "image/pjpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/svg+xml" -> "svg"
                "image/bmp", "image/x-ms-bmp" -> "bmp"
                "image/x-icon", "image/vnd.microsoft.icon" -> "ico"
                "video/mp4" -> "mp4"
                "video/webm" -> "webm"
                "video/quicktime" -> "mov"
                "video/x-matroska" -> "mkv"
                "video/3gpp" -> "3gp"
                "audio/mpeg", "audio/mp3" -> "mp3"
                "audio/mp4", "audio/m4a" -> "m4a"
                "audio/ogg" -> "ogg"
                "audio/wav" -> "wav"
                else -> null
            }
            if (ext != null) {
                return Pair(ext, mime)
            }
        }

        if (url.isNullOrBlank()) {
            return if (isImage) Pair("jpg", "image/jpeg") else null
        }

        // 2. Check query parameters (format, ext, fmt, auto)
        val formatParam = getQueryParameter(url, "format")
            ?: getQueryParameter(url, "ext")
            ?: getQueryParameter(url, "fmt")
        if (!formatParam.isNullOrBlank()) {
            val cleanParam = formatParam.lowercase()
            when (cleanParam) {
                "jpg", "jpeg", "pjpg" -> return Pair("jpg", "image/jpeg")
                "png" -> return Pair("png", "image/png")
                "webp" -> return Pair("webp", "image/webp")
                "gif" -> return Pair("gif", "image/gif")
                "svg" -> return Pair("svg", "image/svg+xml")
                "mp4" -> return Pair("mp4", "video/mp4")
                "webm" -> return Pair("webm", "video/webm")
            }
        }

        val autoParam = getQueryParameter(url, "auto")?.lowercase()
        if (autoParam == "webp") {
            return Pair("webp", "image/webp")
        }

        // 3. Check path extension (handling Twitter suffixes like :large, :orig)
        val cleanPath = url.substringBefore('?').substringBefore('#')
            .replace(Regex(":(large|orig|small|thumb|medium)$", RegexOption.IGNORE_CASE), "")
        val extFromPath = cleanPath.substringAfterLast('.', "").lowercase()
        if (extFromPath.isNotBlank() && extFromPath.length in 2..5 && !extFromPath.contains('/')) {
            when (extFromPath) {
                "jpg", "jpeg" -> return Pair("jpg", "image/jpeg")
                "png" -> return Pair("png", "image/png")
                "webp" -> return Pair("webp", "image/webp")
                "gif" -> return Pair("gif", "image/gif")
                "svg" -> return Pair("svg", "image/svg+xml")
                "bmp" -> return Pair("bmp", "image/bmp")
                "ico" -> return Pair("ico", "image/x-icon")
                "mp4" -> return Pair("mp4", "video/mp4")
                "webm" -> return Pair("webm", "video/webm")
                "mov" -> return Pair("mov", "video/quicktime")
                "mkv" -> return Pair("mkv", "video/x-matroska")
                "3gp" -> return Pair("3gp", "video/3gpp")
                "mp3" -> return Pair("mp3", "audio/mpeg")
                "m4a" -> return Pair("m4a", "audio/mp4")
            }
        }

        // 4. Domain specific patterns (Twitter / Reddit media URLs)
        val lowerUrl = url.lowercase()
        if (lowerUrl.contains("pbs.twimg.com/media/") || lowerUrl.contains("ton.twitter.com/")) {
            return Pair("jpg", "image/jpeg")
        }
        if (lowerUrl.contains("preview.redd.it/") || lowerUrl.contains("i.redd.it/")) {
            return Pair("jpg", "image/jpeg")
        }

        // 5. Caller hint
        if (isImage) {
            return Pair("jpg", "image/jpeg")
        }

        return null
    }

    fun resolveFileName(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null,
        customFileName: String? = null,
        isImage: Boolean = false
    ): Pair<String, String?> {
        val formatInfo = inferMediaFormat(url, mimeType, isImage)
        val inferredExt = formatInfo?.first
        val effectiveMime = mimeType ?: formatInfo?.second

        if (!customFileName.isNullOrBlank()) {
            var sanitized = sanitizeFileName(customFileName)
            if (!sanitized.contains('.') && !inferredExt.isNullOrBlank()) {
                sanitized = "$sanitized.$inferredExt"
            }
            return Pair(sanitized, effectiveMime)
        }

        var candidate = try {
            URLUtil.guessFileName(url, contentDisposition, effectiveMime)
        } catch (_: Exception) {
            null
        }

        // URLUtil defaults to "downloadfile.bin" when it can't determine a name.
        // Fall back to the last URL path segment if available.
        if (candidate.isNullOrBlank() || candidate == "downloadfile.bin" || candidate == ".bin") {
            val pathSegment = url.substringBefore('?').substringBefore('#')
                .trimEnd('/')
                .substringAfterLast('/')
                .replace(Regex(":(large|orig|small|thumb|medium)$", RegexOption.IGNORE_CASE), "")
                .substringBeforeLast('.')
            if (pathSegment.isNotBlank()) {
                candidate = pathSegment
            }
        }

        var name = sanitizeFileName(candidate ?: "download_${System.currentTimeMillis()}")

        // Strip colon suffixes like :large, :orig from Twitter
        name = name.replace(Regex(":(large|orig|small|thumb|medium)$", RegexOption.IGNORE_CASE), "")

        // Fix .bin extension or missing extension
        if (name.endsWith(".bin", ignoreCase = true)) {
            val base = name.substringBeforeLast(".bin")
            val ext = inferredExt ?: if (isImage) "jpg" else null
            name = if (ext != null) "$base.$ext" else name
        } else if (!name.contains('.')) {
            val ext = inferredExt ?: if (isImage) "jpg" else null
            name = if (ext != null) "$name.$ext" else name
        } else if (name.endsWith('.')) {
            val ext = inferredExt ?: if (isImage) "jpg" else "bin"
            name = "$name$ext"
        }

        val finalMime = effectiveMime ?: when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            else -> null
        }

        return Pair(name, finalMime)
    }

    fun isMediaUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return inferMediaFormat(url, null, false) != null
    }

    fun isImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val format = inferMediaFormat(url, null, false) ?: return false
        return format.second.startsWith("image/")
    }
}
