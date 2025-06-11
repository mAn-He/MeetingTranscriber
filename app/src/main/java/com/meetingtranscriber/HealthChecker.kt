package com.meetingtranscriber

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 앱 실행 환경 및 모델 상태 실시간 검증
 */
object HealthChecker {
    
    data class HealthStatus(
        val isHealthy: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val deviceInfo: DeviceInfo
    )
    
    data class DeviceInfo(
        val model: String,
        val androidVersion: Int,
        val availableRAM: Long,
        val supportedABIs: List<String>
    )
    
    suspend fun checkSystemHealth(context: Context): HealthStatus = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // 1. Android 버전 확인
        if (Build.VERSION.SDK_INT < 26) {
            errors.add("Android API level too low (minimum: 26, current: ${Build.VERSION.SDK_INT})")
        }
        
        // 2. RAM 확인 (4GB 권장)
        val availableRAM = getAvailableRAM()
        if (availableRAM < 4 * 1024 * 1024 * 1024L) { // 4GB
            warnings.add("Low RAM detected: ${availableRAM / (1024 * 1024 * 1024)}GB (recommended: 4GB+)")
        }
        
        // 3. 지원되는 ABI 확인
        val supportedABIs = Build.SUPPORTED_ABIS.toList()
        if (!supportedABIs.contains("arm64-v8a") && !supportedABIs.contains("armeabi-v7a")) {
            errors.add("No supported ABI found. Supported: $supportedABIs")
        }
        
        // 4. 필요한 모델 파일 확인
        val modelsDir = File(context.filesDir, "models")
        val requiredModels = listOf(
            "whisper_tiny_int8.onnx",
            "nllb_600m_int8.onnx", 
            "qwen2.5_1.8b_int8.onnx",
            "wavlm_base_plus.onnx"
        )
        
        for (modelFile in requiredModels) {
            val file = File(modelsDir, modelFile)
            if (!file.exists()) {
                warnings.add("Model file missing: $modelFile")
            } else if (file.length() < 1024) { // 파일이 너무 작음
                errors.add("Model file corrupted: $modelFile (size: ${file.length()} bytes)")
            }
        }
        
        // 5. JNI 라이브러리 확인
        val jniLibraries = listOf(
            "sentencepiece_jni",
            "whisper_tokenizer_jni", 
            "qwen_tokenizer_jni"
        )
        
        for (lib in jniLibraries) {
            try {
                System.loadLibrary(lib)
            } catch (e: UnsatisfiedLinkError) {
                warnings.add("JNI library not found: $lib")
            }
        }
        
        // 6. 저장 공간 확인
        val freeSpace = context.filesDir.freeSpace
        if (freeSpace < 10 * 1024 * 1024 * 1024L) { // 10GB
            warnings.add("Low storage space: ${freeSpace / (1024 * 1024 * 1024)}GB free")
        }
        
        val deviceInfo = DeviceInfo(
            model = Build.MODEL,
            androidVersion = Build.VERSION.SDK_INT,
            availableRAM = availableRAM,
            supportedABIs = supportedABIs
        )
        
        HealthStatus(
            isHealthy = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            deviceInfo = deviceInfo
        )
    }
    
    private fun getAvailableRAM(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.maxMemory()
    }
    
    fun generateHealthReport(healthStatus: HealthStatus): String {
        val sb = StringBuilder()
        sb.appendLine("=== MeetingTranscriber Health Check ===")
        sb.appendLine("Status: ${if (healthStatus.isHealthy) "✅ HEALTHY" else "❌ UNHEALTHY"}")
        sb.appendLine()
        
        sb.appendLine("Device Info:")
        sb.appendLine("  Model: ${healthStatus.deviceInfo.model}")
        sb.appendLine("  Android: API ${healthStatus.deviceInfo.androidVersion}")
        sb.appendLine("  RAM: ${healthStatus.deviceInfo.availableRAM / (1024 * 1024 * 1024)}GB")
        sb.appendLine("  ABIs: ${healthStatus.deviceInfo.supportedABIs.joinToString(", ")}")
        sb.appendLine()
        
        if (healthStatus.errors.isNotEmpty()) {
            sb.appendLine("❌ Errors:")
            healthStatus.errors.forEach { sb.appendLine("  - $it") }
            sb.appendLine()
        }
        
        if (healthStatus.warnings.isNotEmpty()) {
            sb.appendLine("⚠️ Warnings:")
            healthStatus.warnings.forEach { sb.appendLine("  - $it") }
            sb.appendLine()
        }
        
        if (healthStatus.isHealthy) {
            sb.appendLine("✅ All systems ready!")
        } else {
            sb.appendLine("❌ Please resolve errors before using the app.")
        }
        
        return sb.toString()
    }
}