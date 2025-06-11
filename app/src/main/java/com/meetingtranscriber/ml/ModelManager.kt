package com.meetingtranscriber.ml

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ModelManager(private val context: Context) {
    private lateinit var ortEnvironment: OrtEnvironment
    private var whisperSession: OrtSession? = null
    private var nllbSession: OrtSession? = null
    private var qwenSession: OrtSession? = null
    private var wavlmSession: OrtSession? = null

    suspend fun initializeModels() = withContext(Dispatchers.IO) {
        ortEnvironment = OrtEnvironment.getEnvironment()
        val modelsDir = File(context.filesDir, "models")
        val whisperPath = File(modelsDir, "whisper_tiny_int8.onnx").absolutePath
        val nllbPath = File(modelsDir, "nllb_600m_int8.onnx").absolutePath
        val qwenPath = File(modelsDir, "qwen2.5_1.8b_int8.onnx").absolutePath
        val wavlmPath = File(modelsDir, "wavlm_base_plus.onnx").absolutePath
        
        whisperSession = ortEnvironment.createSession(whisperPath)
        nllbSession = ortEnvironment.createSession(nllbPath)
        qwenSession = ortEnvironment.createSession(qwenPath)
        wavlmSession = ortEnvironment.createSession(wavlmPath)
    }

    fun getWhisperSession(): OrtSession = whisperSession ?: throw IllegalStateException("Whisper model not loaded")
    fun getNLLBSession(): OrtSession = nllbSession ?: throw IllegalStateException("NLLB model not loaded")
    fun getQwenSession(): OrtSession = qwenSession ?: throw IllegalStateException("Qwen model not loaded")
    fun getWavLMSession(): OrtSession = wavlmSession ?: throw IllegalStateException("WavLM model not loaded")

    fun releaseAll() {
        whisperSession?.close()
        nllbSession?.close()
        qwenSession?.close()
        wavlmSession?.close()
        ortEnvironment.close()
    }
}