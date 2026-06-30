package com.firefly.befirefly.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Utility class for media operations:
 * - Image compression
 * - Base64 encoding/decoding
 * - File read/write for received media
 */
object MediaUtils {

    private const val TAG = "MediaUtils"
    private const val MAX_PAYLOAD_BYTES = Int.MAX_VALUE // No practical limit – chunking handles MQTT caps
    private const val MAX_IMAGE_DIMENSION = 800 // Max width/height for compressed images
    private const val JPEG_QUALITY = 70

    /**
     * Read a file from a URI and return compressed bytes (for images) or raw bytes (for other files).
     * Returns null if file is too large or unreadable.
     */
    fun readAndPrepareMedia(context: Context, uri: Uri, mimeType: String?): ByteArray? {
        return try {
            if (mimeType?.startsWith("image/") == true) {
                compressImage(context, uri)
            } else {
                readFileBytes(context, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read media: ${e.message}", e)
            null
        }
    }

    /**
     * Compress an image URI to JPEG with max dimension constraint.
     */
    fun compressImage(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Decode bounds first (without loading full bitmap)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size for downscaling
            val width = options.outWidth
            val height = options.outHeight
            var sampleSize = 1
            while (width / sampleSize > MAX_IMAGE_DIMENSION * 2 || height / sampleSize > MAX_IMAGE_DIMENSION * 2) {
                sampleSize *= 2
            }

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val stream2 = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(stream2, null, decodeOptions)
            stream2.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap")
                return null
            }

            // Scale down if still too large
            val scaledBitmap = if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
                val ratio = minOf(
                    MAX_IMAGE_DIMENSION.toFloat() / bitmap.width,
                    MAX_IMAGE_DIMENSION.toFloat() / bitmap.height
                )
                val newWidth = (bitmap.width * ratio).toInt()
                val newHeight = (bitmap.height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            // Compress to JPEG
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            scaledBitmap.recycle()

            val bytes = baos.toByteArray()
            Log.d(TAG, "Image compressed: ${bytes.size} bytes (${bytes.size / 1024}KB)")

            if (bytes.size > MAX_PAYLOAD_BYTES) {
                Log.w(TAG, "Compressed image still too large: ${bytes.size} bytes")
                // Try lower quality
                val baos2 = ByteArrayOutputStream()
                val bitmap2 = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bitmap2?.compress(Bitmap.CompressFormat.JPEG, 40, baos2)
                bitmap2?.recycle()
                val bytes2 = baos2.toByteArray()
                if (bytes2.size > MAX_PAYLOAD_BYTES) {
                    Log.e(TAG, "Image too large even at quality=40: ${bytes2.size} bytes")
                    return null
                }
                return bytes2
            }

            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Image compression failed", e)
            null
        }
    }

    /**
     * Read raw bytes from a URI.
     */
    fun readFileBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()

            if (bytes.size > MAX_PAYLOAD_BYTES) {
                Log.e(TAG, "File too large: ${bytes.size} bytes (limit: $MAX_PAYLOAD_BYTES)")
                return null
            }

            Log.d(TAG, "File read: ${bytes.size} bytes")
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file", e)
            null
        }
    }

    /**
     * Read bytes from a local file path.
     */
    fun readLocalFile(path: String): ByteArray? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            if (file.length() > MAX_PAYLOAD_BYTES) {
                Log.e(TAG, "Local file too large: ${file.length()} bytes")
                return null
            }
            file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read local file", e)
            null
        }
    }

    /**
     * Encode bytes to Base64 string.
     */
    fun encodeToBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Decode Base64 string to bytes.
     */
    fun decodeFromBase64(base64: String): ByteArray {
        return Base64.decode(base64, Base64.NO_WRAP)
    }

    /**
     * Save received media bytes to app-private storage.
     * Returns the absolute file path.
     */
    fun saveMediaToStorage(context: Context, bytes: ByteArray, fileName: String, mimeType: String?): String? {
        return try {
            // Determine subdirectory based on type
            val subDir = when {
                mimeType?.startsWith("image/") == true -> "images"
                mimeType?.startsWith("audio/") == true -> "audio"
                mimeType?.startsWith("video/") == true -> "video"
                else -> "files"
            }

            val dir = File(context.filesDir, "media/$subDir")
            dir.mkdirs()

            // Ensure unique filename
            val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val timestamp = System.currentTimeMillis()
            val outputFile = File(dir, "${timestamp}_$safeFileName")

            FileOutputStream(outputFile).use { fos ->
                fos.write(bytes)
            }

            Log.d(TAG, "Media saved: ${outputFile.absolutePath} (${bytes.size} bytes)")
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media", e)
            null
        }
    }

    /**
     * Get the display filename from a URI.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "file"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: "file"
                }
            }
        } catch (e: Exception) {
            // Fallback: try to get from path
            name = uri.lastPathSegment ?: "file"
        }
        return name
    }

    /**
     * Get the MIME type from a URI.
     */
    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    /**
     * Get MIME type from a file extension.
     */
    fun getMimeTypeFromExtension(fileName: String): String {
        return when {
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".gif", true) -> "image/gif"
            fileName.endsWith(".webp", true) -> "image/webp"
            fileName.endsWith(".mp4", true) -> "video/mp4"
            fileName.endsWith(".m4a", true) -> "audio/m4a"
            fileName.endsWith(".mp3", true) -> "audio/mpeg"
            fileName.endsWith(".ogg", true) -> "audio/ogg"
            fileName.endsWith(".pdf", true) -> "application/pdf"
            fileName.endsWith(".doc", true) || fileName.endsWith(".docx", true) -> "application/msword"
            fileName.endsWith(".txt", true) -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /**
     * Format file size for display.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
        }
    }

    /**
     * Check if a file is within the transfer size limit.
     */
    fun isWithinSizeLimit(bytes: ByteArray): Boolean {
        return bytes.size <= MAX_PAYLOAD_BYTES
    }

    /**
     * Get the max payload size for display.
     */
    fun getMaxSizeDisplay(): String = formatFileSize(MAX_PAYLOAD_BYTES.toLong())
}
