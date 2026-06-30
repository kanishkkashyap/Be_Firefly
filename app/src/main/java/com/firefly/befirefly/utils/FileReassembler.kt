package com.firefly.befirefly.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Reassembles streamed file chunks directly to disk (via RandomAccessFile), so files of any size
 * transfer with bounded memory. Chunks may arrive in any order — each is written at its byte offset.
 */
object FileReassembler {
    private const val TAG = "FileReassembler"

    data class ReassembledFile(
        val fileName: String,
        val mimeType: String,
        val fileSize: Long,
        val path: String
    )

    private class Assembly(
        val raf: RandomAccessFile,
        val tempFile: File,
        val total: Int,
        val received: MutableSet<Int>,
        val fileName: String,
        val mimeType: String,
        val fileSize: Long
    )

    private val assemblies = ConcurrentHashMap<String, Assembly>()

    /** Delete stale partial-transfer files left by abandoned/interrupted transfers. */
    fun cleanupStale(context: Context, maxAgeMs: Long = 60 * 60 * 1000L) {
        try {
            val dir = File(context.filesDir, "partial")
            if (!dir.exists()) return
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { f ->
                if (now - f.lastModified() > maxAgeMs) {
                    f.delete()
                    Log.d(TAG, "Cleaned stale partial: ${f.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stale cleanup failed", e)
        }
    }

    /**
     * Add one decrypted chunk. Returns the finished file once the final chunk arrives, else null.
     * Chunk format: "index|total|fileName|mimeType|fileSize|offset:base64data"
     */
    fun addChunk(context: Context, packetBaseId: String, chunkPayload: String): ReassembledFile? {
        try {
            val colonIdx = chunkPayload.indexOf(':')
            if (colonIdx == -1) return null
            val header = chunkPayload.substring(0, colonIdx)
            val data = chunkPayload.substring(colonIdx + 1)

            val parts = header.split("|")
            if (parts.size < 6) return null
            val index = parts[0].toIntOrNull() ?: return null
            val total = parts[1].toIntOrNull() ?: return null
            val fileName = parts[2]
            val mimeType = parts[3]
            val fileSize = parts[4].toLongOrNull() ?: return null
            val offset = parts[5].toLongOrNull() ?: return null

            val bytes = Base64.decode(data, Base64.NO_WRAP)

            val asm = synchronized(assemblies) {
                assemblies.getOrPut(packetBaseId) {
                    val dir = File(context.filesDir, "partial").apply { mkdirs() }
                    val tmp = File(dir, "$packetBaseId.part")
                    if (tmp.exists()) tmp.delete()
                    Assembly(RandomAccessFile(tmp, "rw"), tmp, total, mutableSetOf(), fileName, mimeType, fileSize)
                }
            }

            synchronized(asm) {
                if (asm.received.contains(index)) return null // duplicate chunk
                asm.raf.seek(offset)
                asm.raf.write(bytes)
                asm.received.add(index)

                if (asm.received.size >= asm.total) {
                    asm.raf.close()
                    val outDir = File(context.filesDir, "received_files").apply { mkdirs() }
                    val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file_${System.currentTimeMillis()}" }
                    val outFile = File(outDir, "${System.currentTimeMillis()}_$safeName")
                    if (!asm.tempFile.renameTo(outFile)) {
                        asm.tempFile.copyTo(outFile, overwrite = true)
                        asm.tempFile.delete()
                    }
                    assemblies.remove(packetBaseId)
                    Log.d(TAG, "✅ Reassembled $fileName (${asm.fileSize} bytes) → ${outFile.absolutePath}")
                    return ReassembledFile(fileName, mimeType, fileSize, outFile.absolutePath)
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add/assemble chunk for $packetBaseId", e)
            synchronized(assemblies) {
                assemblies.remove(packetBaseId)?.let {
                    try { it.raf.close() } catch (_: Exception) {}
                    try { it.tempFile.delete() } catch (_: Exception) {}
                }
            }
            return null
        }
    }
}
