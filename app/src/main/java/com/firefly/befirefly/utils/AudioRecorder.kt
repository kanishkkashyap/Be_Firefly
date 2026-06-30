package com.firefly.befirefly.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(outputFile: File) {
        this.outputFile = outputFile

        // IMPORTANT: assign to the field so stopRecording() can actually stop/finalize it.
        recorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile.absolutePath)

            try {
                prepare()
                start()
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to start recording", e)
                try { release() } catch (_: Exception) {}
                this@AudioRecorder.recorder = null
                outputFile.delete()
            }
        }
    }

    fun stopRecording(): File? {
        val rec = recorder
        recorder = null
        if (rec == null) return null
        return try {
            rec.stop()
            rec.release()
            outputFile
        } catch (e: RuntimeException) {
            // stop() throws if called too soon after start() (recording < ~1s) — file is invalid.
            Log.e("AudioRecorder", "stop() failed (recording too short?)", e)
            try { rec.release() } catch (_: Exception) {}
            outputFile?.delete()
            outputFile = null
            null
        }
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }
}
