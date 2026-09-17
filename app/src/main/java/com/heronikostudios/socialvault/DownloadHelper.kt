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
        mimeType: String? = null
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

        val fileName = try {
            URLUtil.guessFileName(trimmedUrl, contentDisposition, mimeType)
        } catch (_: Exception) {
            "download_${System.currentTimeMillis()}"
        }

        try {
            val request = DownloadManager.Request(uri).apply {
                if (!mimeType.isNullOrBlank()) {
                    setMimeType(mimeType)
                }

                // Preserve session cookies for authenticated social media media requests
                val cookies = CookieManager.getInstance().getCookie(trimmedUrl)
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
                else -> "jpg"
            }
            val fileName = "download_${System.currentTimeMillis()}.$ext"
            val resolvedMime = when (ext) {
                "png" -> "image/png"
                "webp" -> "image/webp"
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
                    0 -> downloadFile(context, mediaUrl, userAgent = userAgent)
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

    fun isMediaUrl(url: String?): Boolean {
        if (url == null) return false
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
                clean.endsWith(".png") || clean.endsWith(".webp") ||
                clean.endsWith(".gif") || clean.endsWith(".mp4") ||
                clean.endsWith(".webm") || clean.endsWith(".mov") ||
                clean.endsWith(".mkv")
    }
}
