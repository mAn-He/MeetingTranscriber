package com.meetingtranscriber.ml

import android.content.Context
import android.os.PowerManager
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 메모리 최적화된 모델 매니저 - 동적 로딩/해제
 */
class OptimizedModelManager(private val context: Context) {
    private lateinit var ortEnvironment: OrtEnvironment
    private var currentSession: OrtSession? = null
    private var currentModelType: ModelType? = null
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    enum class ModelType {
        WHISPER, NLLB, QWEN, WAVLM
    }

    suspend fun initializeEnvironment() = withContext(Dispatchers.IO) {
        ortEnvironment = OrtEnvironment.getEnvironment()
        
        // CPU 최적화 설정
        val sessionOptions = OrtSession.SessionOptions()
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        sessionOptions.setIntraOpNumThreads(2) // CPU 코어 제한
        sessionOptions.setInterOpNumThreads(1)
        
        // GPU/NNAPI 사용 가능 시 활성화
        try {
            sessionOptions.addNnapi() // Android Neural Networks API
        } catch (e: Exception) {
            // NNAPI 사용 불가 시 CPU 사용
        }
    }

    suspend fun loadModel(modelType: ModelType): OrtSession = withContext(Dispatchers.IO) {
        // 기존 모델 해제
        releaseCurrentModel()
        
        // WakeLock 획득 (모델 로딩 중 절전 방지)
        acquireWakeLock()
        
        val modelsDir = File(context.filesDir, "models")
        val modelPath = when (modelType) {
            ModelType.WHISPER -> File(modelsDir, "whisper_tiny_int8.onnx").absolutePath
            ModelType.NLLB -> File(modelsDir, "nllb_600m_int8.onnx").absolutePath
            ModelType.QWEN -> File(modelsDir, "qwen2.5_1.8b_int8.onnx").absolutePath
            ModelType.WAVLM -> File(modelsDir, "wavlm_base_plus.onnx").absolutePath
        }
        
        val sessionOptions = OrtSession.SessionOptions()
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        sessionOptions.setIntraOpNumThreads(2)
        
        currentSession = ortEnvironment.createSession(modelPath, sessionOptions)
        currentModelType = modelType
        
        // 강제 GC 실행 (메모리 정리)
        System.gc()
        
        currentSession!!
    }

    private fun releaseCurrentModel() {
        currentSession?.close()
        currentSession = null
        currentModelType = null
        System.gc() // 메모리 즉시 해제
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld != true) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MeetingTranscriber::ModelInference"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 최대 10분
        }
    }

    fun releaseWakeLock() {
        wakeLock?.release()
        wakeLock = null
    }

    fun getCurrentSession(): OrtSession = currentSession ?: throw IllegalStateException("No model loaded")

    fun releaseAll() {
        releaseCurrentModel()
        releaseWakeLock()
        ortEnvironment.close()
    }

    // 메모리 사용량 모니터링
    fun getMemoryUsage(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()
        
        return MemoryInfo(
            usedMemoryMB = usedMemory / (1024 * 1024),
            totalMemoryMB = totalMemory / (1024 * 1024),
            maxMemoryMB = maxMemory / (1024 * 1024),
            freeMemoryMB = freeMemory / (1024 * 1024)
        )
    }

    data class MemoryInfo(
        val usedMemoryMB: Long,
        val totalMemoryMB: Long,
        val maxMemoryMB: Long,
        val freeMemoryMB: Long
    )
}