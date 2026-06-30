package com.firefly.befirefly.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import java.io.InputStream

object FileChunker {
    private const val TAG = "FileChunker"
    // Each chunk is base64-encoded here, then the whole chunk string is AES-GCM encrypted
    // and base64-encoded again before transport. Google Nearby caps a BYTES payload at 32 KB,
    // so the RAW chunk must stay small enough that after both base64 layers + the JSON packet
    // wrapper it still fits. 12 KB raw → ~22 KB on the wire, safely under the 32 KB limit.
    private const val CHUNK_SIZE = 12 * 1024

    data class FileMetadata(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
        val fileSize: Long
    )

    fun getFileMetadata(context: Context, uri: Uri): FileMetadata {
        // file:// URIs (compressed images, recorded audio) don't expose OpenableColumns reliably,
        // so read directly from the File.
        if (uri.scheme == "file") {
            val f = java.io.File(uri.path ?: "")
            val mime = context.contentResolver.getType(uri) ?: MediaUtils.getMimeTypeFromExtension(f.name)
            return FileMetadata(uri, f.name.ifBlank { "file" }, mime, f.length())
        }

        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        var name = "file"
        var size = 0L
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file metadata for $uri", e)
        }
        return FileMetadata(uri, name, mime, size)
    }

    /**
     * Streams a file chunk-by-chunk WITHOUT loading the whole thing into memory, emitting:
     * "chunkIndex|totalChunks|fileName|mimeType|fileSize|byteOffset:base64data"
     *
     * The byteOffset lets the receiver write each chunk straight to disk at the right position
     * (in any order), so arbitrarily large files transfer with bounded memory on both ends.
     * Uses | as the field separator (not /) because MIME types contain /.
     */
    fun streamFile(context: Context, uri: Uri): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val meta = getFileMetadata(context, uri)
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open $uri", e); null
        } ?: return@flow

        input.use { stream ->
            val size = meta.fileSize
            if (size <= 0L) {
                // Unknown size (rare — some content providers). Fall back to a full read; such
                // streams are typically small.
                val all = stream.readBytes()
                if (all.isEmpty()) return@use
                val total = (all.size + CHUNK_SIZE - 1) / CHUNK_SIZE
                var idx = 0
                var off = 0
                while (off < all.size) {
                    val end = minOf(off + CHUNK_SIZE, all.size)
                    val b64 = Base64.encodeToString(all.copyOfRange(off, end), Base64.NO_WRAP)
                    emit("$idx|$total|${meta.fileName}|${meta.mimeType}|${all.size}|$off:$b64")
                    off = end; idx++
                }
                return@use
            }

            val total = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
            Log.d(TAG, "📦 Streaming ${meta.fileName}: $size bytes in $total chunks")
            val buffer = ByteArray(CHUNK_SIZE)
            var index = 0
            var offset = 0L
            while (true) {
                val n = readFully(stream, buffer)
                if (n <= 0) break
                val bytes = if (n < CHUNK_SIZE) buffer.copyOf(n) else buffer
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                emit("$index|$total|${meta.fileName}|${meta.mimeType}|$size|$offset:$b64")
                offset += n
                index++
                if (n < CHUNK_SIZE) break // short read => EOF
            }
            Log.d(TAG, "✅ Emitted $index/$total chunks for ${meta.fileName}")
        }
    }

    /** Read until [buf] is full or the stream ends. Returns bytes read (0 at EOF). */
    private fun readFully(stream: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = stream.read(buf, total, buf.size - total)
            if (r == -1) break
            total += r
        }
        return total
    }
}