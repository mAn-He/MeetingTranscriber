package com.meetingtranscriber.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Environment
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: String? = null

    fun startRecording(): String {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings")
        if (!dir.exists()) dir.mkdirs()
        outputFile = File(dir, "recording_${System.currentTimeMillis()}.wav").absolutePath

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setAudioEncodingBitRate(96000)
            setOutputFile(outputFile)
            prepare()
            start()
        }
        return outputFile!!
    }

    fun stopRecording(): String? {
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        return outputFile
    }
}
