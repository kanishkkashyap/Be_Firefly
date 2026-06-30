package com.firefly.befirefly.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.IOException

class AudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun playFile(file: File, onCompletion: () -> Unit) {
        stop()
        
        MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { 
                    onCompletion()
                    stop()
                }
                player = this
            } catch (e: IOException) {
                Log.e("AudioPlayer", "prepare() failed")
            }
        }
    }
    
    fun playUri(uri: Uri, onCompletion: () -> Unit) {
        stop()
        
        MediaPlayer().apply {
            try {
                setDataSource(context, uri)
                prepare()
                start()
                setOnCompletionListener { 
                    onCompletion()
                    stop()
                }
                player = this
            } catch (e: IOException) {
                Log.e("AudioPlayer", "prepare() failed")
            }
        }
    }

    fun stop() {
        player?.release()
        player = null
    }

    /** Current playback progress 0f..1f, or 0f if not playing. */
    fun progress(): Float {
        val p = player ?: return 0f
        return try {
            val d = p.duration
            if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
        } catch (e: Exception) {
            0f
        }
    }
}
