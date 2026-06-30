package com.firefly.befirefly.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-hosted, store-free update flow:
 *  1. Fetch a tiny version manifest (JSON) from a URL you control (e.g. GitHub Releases raw).
 *  2. If its versionCode is higher than the installed one, offer the update.
 *  3. Download the APK and hand it to the system installer (user confirms with one tap).
 *
 * Manifest format:
 *  { "versionCode": 5, "versionName": "2.1", "apkUrl": "https://.../befirefly.apk", "changelog": "..." }
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"

    // 👉 Replace with your real raw manifest URL (e.g. GitHub Releases raw link). Must be HTTPS.
    const val MANIFEST_URL = "https://raw.githubusercontent.com/your-username/befirefly-releases/main/manifest.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val changelog: String
    )

    fun currentVersionCode(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(info).toInt()
        } catch (e: Exception) {
            0
        }
    }

    /** Returns update info if a newer version is available, else null. */
    suspend fun check(context: Context, manifestUrl: String = MANIFEST_URL): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(text)
            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName", ""),
                apkUrl = json.getString("apkUrl"),
                changelog = json.optString("changelog", "")
            )
            if (info.versionCode > currentVersionCode(context)) info else null
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    /** Downloads the APK to cache. Returns the file, or null on failure. */
    suspend fun downloadApk(context: Context, apkUrl: String): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Clear old downloads so we don't pile up APKs.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "befirefly-update.apk")
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                requestMethod = "GET"
            }
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            out
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            null
        }
    }

    /** Launches the system installer for the downloaded APK (user taps "Update"). */
    fun installApk(context: Context, apk: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Install launch failed", e)
        }
    }
}
