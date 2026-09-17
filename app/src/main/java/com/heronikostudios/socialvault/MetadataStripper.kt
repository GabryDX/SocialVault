package com.heronikostudios.socialvault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object MetadataStripper {

    private const val CACHE_SUBDIR = "sanitized_uploads"

    data class StripResult(
        val uri: Uri,
        val wasStripped: Boolean
    )

    fun stripImageMetadata(context: Context, inputUri: Uri): StripResult {
        val contentResolver = context.contentResolver
        val type = contentResolver.getType(inputUri)?.lowercase() ?: ""
        val fileName = inputUri.lastPathSegment?.lowercase() ?: ""

        val isImage = type.startsWith("image/") || isImageExtension(fileName)
        if (!isImage) {
            return StripResult(inputUri, false)
        }

        return try {
            // 1. Read EXIF orientation before stripping
            val orientation = contentResolver.openInputStream(inputUri)?.use { stream ->
                try {
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } catch (_: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
            } ?: ExifInterface.ORIENTATION_NORMAL

            // 2. Decode bitmap to raw pixels (strips all EXIF, XMP, IPTC, and maker notes)
            val bitmap = contentResolver.openInputStream(inputUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return StripResult(inputUri, false)

            // 3. Bake the original orientation into the pixel buffer so it remains upright
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
            }

            val orientedBitmap = if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }

            // 4. Determine format and destination file
            val sanitizedDir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            val isPng = type.contains("png") || fileName.endsWith(".png")
            val isWebp = type.contains("webp") || fileName.endsWith(".webp")

            val ext = when {
                isPng -> "png"
                isWebp -> "webp"
                else -> "jpg"
            }

            val tempFile = File(sanitizedDir, "sanitized_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext")

            val compressFormat = when {
                isPng -> Bitmap.CompressFormat.PNG
                isWebp -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
                else -> Bitmap.CompressFormat.JPEG
            }

            tempFile.outputStream().use { out ->
                orientedBitmap.compress(compressFormat, 95, out)
            }
            orientedBitmap.recycle()

            val cleanUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            StripResult(cleanUri, true)
        } catch (_: Exception) {
            StripResult(inputUri, false)
        }
    }

    fun isImageExtension(fileName: String?): Boolean {
        if (fileName == null) return false
        val lower = fileName.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".bmp") || lower.endsWith(".heic") ||
                lower.endsWith(".heif")
    }

    fun cleanOldCache(context: Context) {
        try {
            val dir = File(context.cacheDir, CACHE_SUBDIR)
            if (dir.exists() && dir.isDirectory) {
                val cutoff = System.currentTimeMillis() - (30 * 60 * 1000) // 30 minutes
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
